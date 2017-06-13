package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow
import java.util.*


/**
     * This is the flow which handles transfers of existing shares on the ledger.
     * This flow doesn't come in an Initiator and Responder pair as messaging across the network is handled by a [subFlow]
     * call to [CollectSignatureFlow.Initiator].
     * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
     * The flow returns some information from the 2 [SignedTransactions] that were committed to the ledgers.
     */
    class SellerTransferFlow(val otherParty: Party, val qty: Long, val ticker: String, val value: Amount<Currency>): FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            try {
                val vault = serviceHub.vaultService
                val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
                val currentOwner = serviceHub.myInfo.legalIdentity
                //val auditor = serviceHub.networkMapCache.regulatorNodes[0].legalIdentity

                // Stage 1. Retrieve needed shares and add them as input/output to the share tx builder
                val txb = TransactionType.General.Builder(notary.notaryIdentity)
                val (tx, keys) = vault.generateShareSpend(txb, qty, ticker, otherParty.owningKey, value = value)

                //Stage 2. Send the buyer info for the cash tx
                val items = SellerTransferInfo(qty, ticker, value, currentOwner.owningKey)
                send(otherParty, items)

                // Stage 3. Sign the share transaction now
                tx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
                val ptx = tx.signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)

                // Stage 4. Collect signature from buyer. This also verifies the transaction and checks the signatures.
                val shareSTX = subFlow(SignTransferFlow.Initiator(ptx))

                // Stage 5. Retrieve the tx for cash movement
                val newCash = receive<SignedTransaction>(otherParty).unwrap { it }

                // Stage 6. Notarise and record, the share transaction in our vaults.
                //val newShare = subFlow(FinalityFlow(shareSTX, setOf(currentOwner, otherParty, auditor))).single()
                val newShare = subFlow(FinalityFlow(shareSTX, setOf(currentOwner, otherParty))).single()

                // Stage 7. Print confirmation and return the results to be displayed in the front end.
                println("Sale transfer! Final transaction is: " +
                        "\n\n${Emoji.renderIfSupported(newCash.tx)} \n\n" + Emoji.renderIfSupported(newShare.tx))

                return  "Transaction IDs: \n \n Cash: ${newCash.tx.id} \n Shares: ${newShare.tx.id} \n \n \n" +
                        "${value * qty} were received from ${otherParty.name}. \n \n" +
                        "${otherParty.name} received $qty shares in $ticker (priced at $value per share)"
            } catch (ex: UnacceptablePriceException) {
                return "Not enough shares. ${ex.message}"
            } catch (ex: InsufficientBalanceException) {
                return "Not enough cash. ${ex.message}"
            } catch (ex: Exception) {
                return "Failure: ${ex.message} $ex "
            }
        }

        private fun logBalance() {
            println("Seller's balance: ${serviceHub.vaultService.cashBalances}")
        }
    }

@CordaSerializable
data class SellerTransferInfo(
        val qty: Long,
        val ticker: String,
        val price: Amount<Currency>,
        val sellerOwnerKey: CompositeKey
)