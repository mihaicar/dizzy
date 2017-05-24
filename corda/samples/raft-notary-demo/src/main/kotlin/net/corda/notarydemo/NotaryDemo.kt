package net.corda.notarydemo

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.toStringShort
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.flows.NotaryFlow
import net.corda.notarydemo.flows.DummyIssueAndMove

fun main(args: Array<String>) {
    val host = HostAndPort.fromString("localhost:10003")
    println("Connecting to the recipient node ($host)")
    CordaRPCClient(host).use("demo", "demo") {
        val api = NotaryDemoClientApi(this)
        api.startNotarisation()
    }
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val rpc: CordaRPCOps) {
    private val notary by lazy {
        rpc.networkMapUpdates().first.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    }

    private val counterpartyNode by lazy {
        rpc.networkMapUpdates().first.first { it.legalIdentity.name == "Counterparty" }
    }

    private companion object {
        private val TRANSACTION_COUNT = 10
    }

    /** Makes calls to the node rpc to start transaction notarisation. */
    fun startNotarisation() {
        println("Getting an avg for 10 transactions...")
        var avg10 = 0.0
        for (i in 1..10) {
            println("Batch $i")
            avg10 += notarise(TRANSACTION_COUNT)
        }
        println("${Emoji.CODE_GREEN_TICK} AVERAGE 10 = ${avg10/10}")
        println("Getting an avg for 50 transactions...")
        var avg50 = 0.0
        for (i in 1..10) {
            avg50 += notarise(TRANSACTION_COUNT)
        }
        println("${Emoji.CODE_GREEN_TICK} AVERAGE 50 = ${avg50/10}")
    }

    fun notarise(count: Int): Double {
        val transactions = buildTransactions(count)
        val before = System.currentTimeMillis().toDouble()/1000
        notariseTransactions(transactions)
        val after = System.currentTimeMillis().toDouble()/1000
        return after - before
    }

    /**
     * Builds a number of dummy transactions (as specified by [count]). The party first self-issues a state (asset),
     * and builds a transaction to transfer the asset to the counterparty. The *move* transaction requires notarisation,
     * as it consumes the original asset and creates a copy with the new owner as its output.
     */
    private fun buildTransactions(count: Int): List<SignedTransaction> {
        val moveTransactions = (1..count).map {
            rpc.startFlow(::DummyIssueAndMove, notary, counterpartyNode.legalIdentity).returnValue
        }
        return Futures.allAsList(moveTransactions).getOrThrow()
    }

    /**
     * For every transaction invoke the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys - one for every transaction
     */
    private fun notariseTransactions(transactions: List<SignedTransaction>): List<String> {
        // TODO: Remove this suppress when we upgrade to kotlin 1.1 or when JetBrain fixes the bug.
        @Suppress("UNSUPPORTED_FEATURE")
        val signatureFutures = transactions.map { rpc.startFlow(NotaryFlow::Client, it).returnValue }
        return Futures.allAsList(signatureFutures).getOrThrow().map { it.map { it.by.toStringShort() }.joinToString() }
    }
}
