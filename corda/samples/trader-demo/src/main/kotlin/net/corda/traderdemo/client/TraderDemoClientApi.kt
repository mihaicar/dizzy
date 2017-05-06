package net.corda.traderdemo.client

import com.google.common.util.concurrent.Futures
import net.corda.contracts.testing.calculateRandomlySizedAmounts
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.BOC
import net.corda.traderdemo.flow.SellerFlow
import net.corda.traderdemo.flow.SellerTransferFlow
import java.util.*
import kotlin.test.assertEquals

/**
 * Interface for communicating with nodes running the trader demo.
 */
class TraderDemoClientApi(val rpc: CordaRPCOps) {
    private companion object {
        val logger = loggerFor<TraderDemoClientApi>()
    }

    fun runBuyer(amount: Amount<Currency> = 30000.DOLLARS) {
        val bankOfCordaParty = rpc.partyFromName(BOC.name)
                ?: throw Exception("Unable to locate ${BOC.name} in Network Map Service")
        val me = rpc.nodeIdentity()
        val amounts = calculateRandomlySizedAmounts(amount, 3, 10, Random())
        // issuer random amounts of currency totaling 30000.DOLLARS in parallel
        println("About to request money issuance in the Buyer.")

        // The IssuanceRequester is just a testing feature to enable asset mvm - not to be used in production.
        val resultFutures = amounts.map { pennies ->
            rpc.startFlow(::IssuanceRequester, Amount(pennies, amount.token), me.legalIdentity, OpaqueBytes.of(1), bankOfCordaParty).returnValue
        }

        Futures.allAsList(resultFutures).getOrThrow()
        println("Requested and received.")
    }

    fun runSeller(amount: Amount<Currency> = 1000.0.DOLLARS, counterparty: String, qty: Long, ticker: String) {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
        //
        // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
        // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
        //
        // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                assertEquals(SellerFlow.Companion.PROSPECTUS_HASH, id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        println("About to start seller flow.")
        val stx = rpc.startFlow(::SellerFlow, otherParty, amount, qty, ticker).returnValue.getOrThrow()
        println("Sale completed in API - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(stx)}")
    }

    fun runSellerTransfer(amount: Amount<Currency>, counterparty: String, qty: Long, ticker: String) {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        // MC: This time, the seller will already have the shares in his vault - no issuing.
        println("About to start seller transfer flow.")
        val stxs = rpc.startFlow(::SellerTransferFlow, otherParty, qty, ticker, amount).returnValue.getOrThrow()
        println("Transfer completed in API - we have a happy customer!\n\nFinal transaction is:" +
                "$stxs")

    }

    fun runDisplay() {
        val cash = rpc.getCashBalances().entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Cash balance: ${cash.joinToString()}")
        val shares = rpc.getShareBalances().entries.map { "${it.key} ${it.value}" }
        println("Share balance: ${shares.joinToString()}")
    }
}
