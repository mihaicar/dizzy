package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.ShareContract
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.`issued by`
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.NotaryFlow
import net.corda.flows.TwoPartyTradeFlow
import java.time.Instant
import java.util.*


class SellerFlow(val otherParty: Party,
                 val amount: Amount<Currency>,
                 val qty: Long,
                 val ticker: String,
                 override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(otherParty: Party, amount: Amount<Currency>, qty: Long, ticker: String) : this(otherParty, amount, qty, ticker, tracker())

    companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")

        object SELF_ISSUING : ProgressTracker.Step("Got session ID back, issuing and timestamping some commercial paper")

        object TRADING : ProgressTracker.Step("Starting the trade flow") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyTradeFlow.Seller.tracker()
        }

        // We vend a progress tracker that already knows there's going to be a TwoPartyTradingFlow involved at some
        // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
        // surprised when it appears as a new set of tasks below the current one.
        fun tracker() = ProgressTracker(SELF_ISSUING, TRADING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = SELF_ISSUING

        val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]

        // cpOwner is the seller (because they're the ones 'issuing' it)
        val cpOwnerKey = serviceHub.legalIdentityKey
        val shareContract = selfIssueShareContract(cpOwnerKey.public.composite, notary, amount, ticker, qty)
        println("Share contract's owner: ${shareContract.state.data.owner} and ${cpOwnerKey.public.composite} -- should be the same now" )
        progressTracker.currentStep = TRADING

        val balances = serviceHub.vaultService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
        val lockID: UUID = UUID(1234, 1234)
        //println("List of unconsumed states: " + list)


        // Send the offered amount, quantity and ticker - these come from gradle and, higher, a JS page (//TODO)
        // MC: AMOUNT should be the price of ONE share, not all the shares sent in the contract.
        val items = listOf(amount, qty, ticker)
        // amount - what the buyer has to pay - could be a diff between exchange and gradle input!
        send(otherParty, items)
        //println("Otherparty: " + otherParty + " " + otherParty.owningKey)
        //println("Balance of the Seller previous to the trade is: ${balances.joinToString()}")
        val seller = TwoPartyTradeFlow.Seller(
                otherParty,
                notary,
                shareContract,
                amount,
                qty,
                ticker,
                cpOwnerKey,
                progressTracker.getChildProgressTracker(TRADING)!!)
        return subFlow(seller, shareParentSessions = true)
    }

    @Suspendable
    fun selfIssueShareContract(ownedBy: CompositeKey, notaryNode: NodeInfo, value: Amount<Currency>, ticker: String, qty: Long): StateAndRef<ShareContract.State> {
        // Make a fake company that's issued its own paper, given a ticker
        // This is for DEMO PURPOSES ONLY: in reality, the issuing would only be done by the company owning the ticker
        // However, we are not interested in the financial side of the trade, but the programming part.
        val keyPair = generateKeyPair()
        val party = Party(ticker, keyPair.public)
        val issuance: SignedTransaction = run {
            // MC: we get the realAmount from the exchange (ideally; here, x = Yahoo)
            // The value of the contract is the amount times the no of shares in it.
            // Need to check the offered price is good for the value of the contract, i.e.
            // assert paid > value

            // MC: timestamping - we can redeem 10 sec after
            val tx = ShareContract().generateIssue(party.ref(1, 2, 3), value `issued by` DUMMY_CASH_ISSUER,
                    Instant.now() + 10.seconds, notaryNode.notaryIdentity, qty, ticker)
            println("\nTransaction 1: " + tx.outputStates())
            println("We have generated a ShareContract worth ${value * qty}. :)")


            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus.
            tx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!.id)
            println("\nTransaction 2: " + tx.outputStates())
            // Requesting timestamping, all CP must be timestamped.
            tx.setTime(Instant.now(), 5.seconds)

            // Sign it as ourselves.
            tx.signWith(keyPair)

            // Get the notary to sign the timestamp
            val notarySigs = subFlow(NotaryFlow.Client(tx.toSignedTransaction(false)))
            notarySigs.forEach { tx.addSignatureUnchecked(it) }

            // Commit it to local storage.
            val stx = tx.toSignedTransaction(true)
            serviceHub.recordTransactions(listOf(stx))
            println("\nSIGNED Transaction 3: " + stx.tx)
            stx
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.
        val move: SignedTransaction = run {
            val builder = TransactionType.General.Builder(notaryNode.notaryIdentity)
            ShareContract().generateMove(builder, issuance.tx.outRef(0), ownedBy)
            builder.signWith(keyPair)
            println("Builder: " + builder.outputStates())
            val notarySignature = subFlow(NotaryFlow.Client(builder.toSignedTransaction(false)))
            notarySignature.forEach { builder.addSignatureUnchecked(it) }
            val tx = builder.toSignedTransaction(true)
            serviceHub.recordTransactions(listOf(tx))
            println("\n SIGNED Transaction in move: " + tx.tx)
            tx
        }
        val trans: StateAndRef<ShareContract.State> = move.tx.outRef(0)
        return trans
    }

}
