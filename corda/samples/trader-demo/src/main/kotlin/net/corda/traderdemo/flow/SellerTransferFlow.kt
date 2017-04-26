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
import net.corda.flows.NotaryFlow
import net.corda.flows.TwoPartyTradeFlow
import java.time.Instant
import java.util.*


class SellerTransferFlow(val otherParty: Party,
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
        val cpOwnerKey = serviceHub.legalIdentityKey

        // MC: change this!
        val shareContract = retrieveShareContract(cpOwnerKey.public.composite, notary, amount, ticker, qty)

        progressTracker.currentStep = TRADING

        val balances = serviceHub.vaultService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Balance of the Seller previous to the trade is: ${balances.joinToString()}")


        // Send the offered amount, quantity and ticker - these come from gradle and, higher, a JS page (//TODO)
        val items = listOf(amount, qty, ticker)
        // amount - what the buyer has to pay - could be a diff between exchange and gradle input!
        send(otherParty, items)
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
    fun retrieveShareContract(ownedBy: CompositeKey, notaryNode: NodeInfo, value: Amount<Currency>, ticker: String, qty: Long): StateAndRef<ShareContract.State> {
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


            // MC: what we can do is to 'retrieve the shares' by making a shareContract out of many pieces of SC
            // eg. i want 10 shares and i have in my vault 3 papers with 2, 5 and 3 shares each. just piece
            // together a SC that has 10 shares, update the price with the exchange given one at the time of
            // calling the function (and use that for the comparison with the buyer price).

            // If we have more shares than they want us to issue, we should break.
            // Otherwise the idea works as for the generateSpend() - go through the scs
            // and for the last trans that fulfills the total, we split into 2
            // Now, consider example above, but we want 4 shares. We get the 2, and we get the 5.
            // Then we piece together a generateIssue trans of 4 (that will change ownership), we generateIssue
            // a trans of the remainder 3 shares (that won't change ownership) and we delete the 2 trans we retrieved.

            // To remove a paper, feed it into a generateRedeem (for eg) as input and dont give an output.
            // We're meant to .addInput all state and refs (so the 2 and the 5) and we can actually call generateShareSpend
            // fromt here - no need to call the serviceHub here, first of all and then, we can actually have the logic
            // to verify the transactions in the contract, where it's meant to be.
//            val tx : TransactionBuilder
//            if (total >= qty) {
//                tx = ShareContract().generateIssue(party.ref(1, 2, 3), value `issued by` DUMMY_CASH_ISSUER,
//                        Instant.now() + 10.seconds, notaryNode.notaryIdentity, qty, ticker)
//            } else {
//                println("Something went wrong")
//                return scs[0]
//            }
            println("Generating contract of new size...")
            val tx = ShareContract().generateIssue(party.ref(1, 2, 3), value `issued by` DUMMY_CASH_ISSUER,
                    Instant.now() + 10.seconds, notaryNode.notaryIdentity, qty, ticker)
            //val tx = ShareContract().generateTransfer(serviceHub.vaultService, party.ref(1, 2, 3), value `issued by` DUMMY_CASH_ISSUER,
            //            Instant.now() + 10.seconds, notaryNode.notaryIdentity, qty, ticker)

            // MC: Need to make sure we are also deleting the transaction states from the issuing bank
            // This is important, because shares do not behave like commercial papers. We do not care
            // about their maturity date, as it is instant (what we wanted to achieve, after all).
            // The 'redemption' stage, which would have given back *cash* to the buyer in return for the dead CP
            // will be replaced with an instant settlement stage, right after the deal is done
            // That being said, yes, we are issuing the paper at this stage. However, we can only be certain the
            // trade is performed correctly at the end of the transaction signing by the notaries. At that stage,
            // we want to emulate the redeem process - basically destroy evidences of the paper existing in the
            // seller's node (//TODO: is this done by an actual DELETE FROM in sql?)
            // in the db - owner_key is the actual owner in the sense that they issued the CP and they will be the ones
            // paying for its destroying. maybe this should change for shares - change owner key into smth else (say, hash of ticker?)

            // Modify the GenerateIssue by self-issuing a contract with qty shares, but also remembering to DELETE the small ones
            // Basically the generateSpend
            // TODO: Consider moving these two steps below into generateIssue.

            // Attach the prospectus. MC: we don't actually need that

            tx.addAttachment(serviceHub.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

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

            stx
        }

        // Now make a dummy transaction that moves it to a new key, just to show that resolving dependencies works.
        val move: SignedTransaction = run {
            val builder = TransactionType.General.Builder(notaryNode.notaryIdentity)
            ShareContract().generateMove(builder, issuance.tx.outRef(0), ownedBy)

            // Sign it as ourselves
            builder.signWith(keyPair)
            // Get the notary to sign the timestamp
            val notarySignature = subFlow(NotaryFlow.Client(builder.toSignedTransaction(false)))
            notarySignature.forEach { builder.addSignatureUnchecked(it) }

            // Commit it to local storage
            val tx = builder.toSignedTransaction(true)
            serviceHub.recordTransactions(listOf(tx))
            tx
        }

        return move.tx.outRef(0)
    }

}
