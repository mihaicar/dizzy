package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
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
import net.corda.traderdemo.model.Share
import java.util.*


/**
     * This is the flow which handles transfers of existing IOUs on the ledger.
     * This flow doesn't come in an Initiator and Responder pair as messaging across the network is handled by a [subFlow]
     * call to [CollectSignatureFlow.Initiator].
     * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
     * The flow returns the [SignedTransaction] that was committed to the ledger.
     */
    class SellerTransferFlow(val otherParty: Party, val qty: Long, val ticker: String, val value: Amount<Currency>): FlowLogic<String>() {

        @Suspendable
        override fun call(): String {
            try {
                val vault = serviceHub.vaultService
                val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
                println("Notary node is: ${notary.notaryIdentity}")
                val currentOwner = serviceHub.myInfo.legalIdentity

                // Stage 1. Retrieve needed shares and add them as input/output to the share tx
                val txb = TransactionType.General.Builder(notary.notaryIdentity)
                val (tx, keys) = vault.generateShareSpend(txb, qty, ticker, otherParty.owningKey, value = value)


                //Stage 2. Send the buyer info for the cash tx
                val items = SellerTransferInfo(qty, ticker, value, currentOwner.owningKey)
                send(otherParty, items)

                // Stage 3. Retrieve the tx for cash movement
                val cashSTX = receive<SignedTransaction>(otherParty).unwrap { it }

                // Stage 4. Verify validity of cash transaction?
                //TODO: check the nullity in the verifyContracts() function.

                // Stage 5. Sign the share transaction now
                tx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
                val ptx = tx.signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)

                // Stage 6. Collect signature from buyer.
                // This also verifies the transaction and checks the signatures.
                val shareSTX = subFlow(SignTransferFlow.Initiator(ptx))

                // Stage 7. Notarise and record, the transactions in our vaults.
                val newCash = subFlow(FinalityFlow(cashSTX, setOf(currentOwner, otherParty))).single()
                val newShare = subFlow(FinalityFlow(shareSTX, setOf(currentOwner, otherParty))).single()
                return  "Transaction IDs: \n \n Cash: ${newCash.tx.id} \n Shares: ${newShare.tx.id} \n \n \n" +
                        "${value * qty} were received from ${otherParty.name}. \n \n" +
                        "${otherParty.name} received $qty shares in $ticker (priced at $value per share)"
            } catch(ex: Exception) {
                return "Failure: ${ex.message}"
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

sealed class FlowResult {
    class Success(val message: String?): FlowResult() {
        override fun toString(): String = "Success($message)"
    }

    class Failure(val message: String?): FlowResult() {
        override fun toString(): String = "Failure($message)"
    }
}
