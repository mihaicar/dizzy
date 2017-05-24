package net.corda.traderdemo.client

import com.google.common.util.concurrent.Futures
import net.corda.contracts.testing.calculateRandomlySizedAmounts
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.crypto.Party
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
import net.corda.traderdemo.flow.ShowHistory
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
        // MC: The IssuanceRequester is just a testing feature to enable asset mvm - not to be used in production.
        val resultFutures = amounts.map { pennies ->
            rpc.startFlow(::IssuanceRequester, Amount(pennies, amount.token), me.legalIdentity, OpaqueBytes.of(1), bankOfCordaParty).returnValue
        }
        Futures.allAsList(resultFutures).getOrThrow()
        println("Requested and received in ${me.legalIdentity.name}")
    }


    // The following 2 sellers are meant for the front-end development.
    fun runSeller(amount: Amount<Currency> = 1000.0.DOLLARS, counterparty: String, qty: Long, ticker: String) {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")

        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                assertEquals(SellerFlow.Companion.PROSPECTUS_HASH, id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        println("About to start seller flow.")
        val stx = rpc.startFlow(::SellerFlow, otherParty, amount, qty, ticker).returnValue.getOrThrow()
        println("Final result is: \n\n${Emoji.renderIfSupported(stx)}")
    }

    fun runSellerTransfer(amount: Amount<Currency>, counterparty: String, qty: Long, ticker: String) {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        // MC: This time, the seller will already have the shares in his vault - no issuing.
        println("About to start seller transfer flow.")
        val stxs = rpc.startFlow(::SellerTransferFlow, otherParty, qty, ticker, amount).returnValue.getOrThrow()
        println("Final result is: $stxs")
    }

    // The following 2 sellers are meant for the Random trade cycle
    fun runSellerR(amount: Amount<Currency> = 1000.0.DOLLARS, counterparty: String, qty: Long, ticker: String): String {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        if (!rpc.attachmentExists(SellerFlow.PROSPECTUS_HASH)) {
            javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                val id = rpc.uploadAttachment(it)
                assertEquals(SellerFlow.Companion.PROSPECTUS_HASH, id)
            }
        }

        // The line below blocks and waits for the future to resolve.
        println("About to start seller flow.")
        val stx = rpc.startFlow(::SellerFlow, otherParty, amount, qty, ticker).returnValue.getOrThrow()
        return stx
    }

    fun runSellerTransferR(amount: Amount<Currency>, counterparty: String, qty: Long, ticker: String): String {
        val otherParty = rpc.partyFromName(counterparty) ?: throw IllegalStateException("Don't know $counterparty")
        // MC: This time, the seller will already have the shares in his vault - no issuing.
        println("About to start seller transfer flow.")
        val stxs = rpc.startFlow(::SellerTransferFlow, otherParty, qty, ticker, amount).returnValue.getOrThrow()
        return stxs
    }

    fun runDisplay() {
        val cash = rpc.getCashBalances().entries.map { "${it.key.currencyCode} ${it.value}" }
        println("Cash balance: ${cash.joinToString()}")
        val shares = rpc.getShareBalances().entries.map { "${it.key} ${it.value}" }
        println("Share balance: ${shares.joinToString()}")
    }

    fun retrieveCash(): Double {
        try {
            return ((rpc.getCashBalances()[Currency.getInstance("USD")]!!.quantity)/100).toDouble()
        } catch (ex: Exception) {
            return 0.0
        }
    }

    fun retrieveSharesIn(ticker: String): Long {
        try {
            return rpc.getShareBalances()[ticker] as Long
        } catch (ex: Exception) {
            return 0
        }
    }

    fun  runAuditor(counterparties: List<String>, txID: String) {
        val otherParties : MutableList<Party> = mutableListOf()
        for (cp in counterparties) {
            val otherParty = rpc.partyFromName(cp) ?: throw IllegalStateException("Don't know $cp")
            otherParties.add(otherParty)
        }
        var txs: String = ""
        for (p in otherParties) {
            try {
                txs = rpc.startFlow(::ShowHistory, p, txID).returnValue.getOrThrow()
                break;
            } catch (ex: Exception) {}
        }
        println("Transaction details: $txs")
    }

}
