package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.ShareContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionGraphSearch
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.flows.TwoPartyTradeFlow
import java.nio.file.Path
import java.util.*

class BuyerFlow(val otherParty: Party,
                private val attachmentsPath: Path,
                override val progressTracker: ProgressTracker = ProgressTracker(STARTING_BUY)) : FlowLogic<Unit>() {

    object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing shares")

    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            // Buyer will fetch the attachment from the seller automatically when it resolves the transaction.
            // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
            val attachmentsPath = (services.storageService.attachments).let {
                it.automaticallyExtractAttachments = true
                it.storePath
            }
            services.registerFlowInitiator(SellerFlow::class.java) { BuyerFlow(it, attachmentsPath) }
            //services.registerFlowInitiator(SellerTransferFlow::class.java) { BuyerFlow(it, attachmentsPath)}
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = STARTING_BUY
        logBalance()
        // Receive the offered amount and automatically agree to it (in reality this would be a longer negotiation)
        // MC: we're also automatically agreeing to the qty/ticker - why?
        val items = receive<List<Any>>(otherParty).unwrap { it }

        @Suppress("UNCHECKED_CAST")
        val amount : Amount<Currency> = items[0] as Amount<Currency>
        val qty : Long = items[1] as Long
        val ticker : String = items[2] as String
        // MC: the agreement is already done - left to check amount and value are similar
        val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
        val buyer = TwoPartyTradeFlow.Buyer(
                otherParty,
                notary.notaryIdentity,
                amount,
                qty,
                ticker,
                ShareContract.State::class.java)

        // This invokes the trading flow and out pops our finished transaction.
        val tradeTX: SignedTransaction = subFlow(buyer, shareParentSessions = true)
        // TODO: This should be moved into the flow itself.
        serviceHub.recordTransactions(listOf(tradeTX))

        println("Purchase complete - we are a happy customer! Final transaction is: " +
                "\n\n${Emoji.renderIfSupported(tradeTX.tx)}")

        logIssuanceAttachment(tradeTX)
        logBalance()
        //logShareContracts()

    }

    private fun logBalance() {
        val balances = serviceHub.vaultService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Balance of Buyer is: ${balances.joinToString()}")
    }

    private fun logIssuanceAttachment(tradeTX: SignedTransaction) {
        // Find the original share issuance - TGS
        // MC: Explore the complexity of TGS
        val search = TransactionGraphSearch(serviceHub.storageService.validatedTransactions, listOf(tradeTX.tx))
        search.query = TransactionGraphSearch.Query(withCommandOfType = ShareContract.Commands.Issue::class.java,
                followInputsOfType = ShareContract.State::class.java)
        val cpIssuance = search.call().single()

        cpIssuance.attachments.first().let {
            val p = attachmentsPath.toAbsolutePath().resolve("$it.jar")
            println("""
Attachment for the Share => any legal physical contract. Follows the initial issuance of the shares.

${Emoji.renderIfSupported(cpIssuance)}""")
        }
    }

    private fun logShareContracts() {
        val lockID: UUID = UUID(1234, 1234)
        val list = serviceHub.vaultService.unconsumedStatesForShareSpending<ShareContract.State>(qty = 4, lockId = lockID, ticker = "AAPL");
        var noShares = 0L
        list
                .filter { it.state.data.ticker == "AAPL" }
                .forEach { noShares += it.state.data.qty }

        println("Buyer has a total of $noShares in AAPL.")
    }
}
