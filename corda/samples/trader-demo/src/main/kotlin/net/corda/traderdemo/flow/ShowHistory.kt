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
class ShowHistory(val otherParty: Party, val txID: String): FlowLogic<String>() {

    @Suspendable
    override fun call(): String {
        try {
            val vault = serviceHub.vaultService
            val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
            val currentOwner = serviceHub.myInfo.legalIdentity

            // Stage 1. Retrieve needed shares and add them as input/output to the share tx
            //val txInfo = vault.txHistory(txID)

            return vault.txHistory(txID)
        } catch(ex: Exception) {
            return "Failure: ${ex.message} $ex "
        }
    }
}
