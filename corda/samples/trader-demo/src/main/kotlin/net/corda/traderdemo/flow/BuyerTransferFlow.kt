package net.corda.traderdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow
import net.corda.stockinfo.StockFetch
import java.util.*

class UnacceptablePriceException(givenPrice: Amount<Currency>, accPrice: Amount<Currency> = 0.DOLLARS) : FlowException("Unacceptable price: $givenPrice != $accPrice")
class UnacceptableNegativeException(price: Long, qty : Long) : FlowException("Unacceptable negative value: $price or $qty")
class UnacceptableSellerException(sellerOwnerKey: CompositeKey, owningKey: CompositeKey) : FlowException("Different sellers: $sellerOwnerKey and $owningKey")

class BuyerTransferFlow(val otherParty: Party,
                override val progressTracker: ProgressTracker = ProgressTracker(STARTING_BUY)) : FlowLogic<Unit>() {

    object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing shares")
    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            services.registerFlowInitiator(SellerTransferFlow::class.java) { BuyerTransferFlow(it)}
        }
    }

    @Suspendable
    override fun call() {
        val vault = serviceHub.vaultService
        val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
        val currentOwner = serviceHub.myInfo.legalIdentity

        // Stage 1. Receive the tx from the seller.
        val untrustedItems = receive<SellerTransferInfo>(otherParty)

        // Stage 2. verify the amount is correctly determined
        val items = verify(untrustedItems)

        // Stage 3. Generate the cash spend
        val tx = TransactionType.General.Builder(notary.notaryIdentity)
        val (ctx, sgn) = vault.generateSpend(tx, items.price * items.qty, items.sellerOwnerKey)

        // Stage 4. Sign transaction
        ctx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
        val ptx = ctx.signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)

        // Stage 5. Collect signature from seller. This also verifies the transaction and checks the signatures.
        val stx = subFlow(SignTransferFlow.Initiator(ptx))

        // Stage 6. Notarise and record, the transaction in our vaults.
        val newCash = subFlow(FinalityFlow(stx, setOf(currentOwner, otherParty))).single()

        // Stage 7. Send the signed transaction over to the seller for finalisation
        send(otherParty, newCash)
    }

    private fun verify(data: UntrustworthyData<SellerTransferInfo>): SellerTransferInfo {
        return data.unwrap{
            // is price accurately describing the asset?
            val sharePrice = StockFetch.getPrice(it.ticker)
            if (sharePrice.toDouble() * 100.0 > it.price.quantity) {
                throw UnacceptablePriceException(it.price, DOLLARS(sharePrice.toDouble()))
            }

            // do we have acceptable prices/quantities (!= 0)
            if (it.price.quantity <= 0 || it.qty <= 0) {
                throw UnacceptableNegativeException(it.price.quantity, it.qty)
            }

            // check whether the seller is indeed the other party
            if (it.sellerOwnerKey != otherParty.owningKey) {
                throw UnacceptableSellerException(it.sellerOwnerKey, otherParty.owningKey)
            }

            it
        }
    }

    private fun logBalance() {
        println("Buyer's balance: ${serviceHub.vaultService.cashBalances}")
    }

}
