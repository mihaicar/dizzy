package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.IShareState
import net.corda.contracts.ShareContract
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.unconsumedStates
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.FinalityFlow
import net.corda.flows.NotaryFlow
import net.corda.flows.TwoPartyTradeFlow
import java.security.KeyPair
import java.time.Instant
import java.util.*


    /**
     * This is the flow which handles transfers of existing IOUs on the ledger.
     * This flow doesn't come in an Initiator and Responder pair as messaging across the network is handled by a [subFlow]
     * call to [CollectSignatureFlow.Initiator].
     * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
     * The flow returns the [SignedTransaction] that was committed to the ledger.
     */
    class SellerTransferFlow(val otherParty: Party, val qty: Long, val ticker: String): FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val vault = serviceHub.vaultService
            val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
            val currentOwner = serviceHub.myInfo.legalIdentity
            // Stage 1. Retrieve IOU specified by linearId from the vault.
            val lock: UUID = UUID(1234, 1234)
            val acceptableShares = vault.unconsumedStatesForShareSpending<ShareContract.State>(qty = qty, lockId = lock, ticker = ticker, notary = notary.notaryIdentity);
            println("As reference, we have the following shares: ")
            for (share in acceptableShares){
                println("${share.state.data.qty} in ${share.state.data.ticker}\n")
            }
            val txb = TransactionType.General.Builder(notary.notaryIdentity)
            val (tx, keys) = vault.generateShareSpend(txb, qty, ticker, otherParty.owningKey)
            println("Transaction: $tx")

            // Stage 2. This flow can only be initiated by the current recipient.
//            if (serviceHub.myInfo.legalIdentity != inputIou.lender) {
//                throw IllegalArgumentException("IOU transfer can only be initiated by the IOU lender.")
//            }

            // Stage 3. Create the new IOU state reflecting a new lender.
//done in the share spend            val outputIou = inputIou.withNewLender(newLender)

//            // Stage 4. Create the transfer command.
//            val signers = inputIou.participants + newLender.owningKey
//            val transferCommand = Command(IOUContract.Commands.Transfer(), signers)

            // Stage 6. Create the transaction which comprises: one input, one output and one command.
            //builder.withItems(iouStateAndRef, outputIou, transferCommand)

            // Stage 7. Verify and sign the transaction.
            //TODO: check the nullity in the verifyContracts() function.
            tx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            val ptx = tx.signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)
            println("We delegate signing now...")
            // Stage 8. Collect signature from borrower and the new lender and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            val stx = subFlow(SignTransferFlow.Initiator(ptx))
            println("Prepare for the signature transaction.")
            // Stage 9. Notarise and record, the transaction in our vaults.
            return subFlow(FinalityFlow(stx, setOf(currentOwner, otherParty))).single()
        }
    }

