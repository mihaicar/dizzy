package net.corda.traderdemo

import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType.Companion.regulator
import net.corda.core.utilities.Emoji
import net.corda.flows.IssuerFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.node.NodeBasedTest
import net.corda.traderdemo.client.TraderDemoClientApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Ensure the quasar.jar is added to the configuration of the test when ran in IntelliJ
 * -javaagent:path-to-quasar-jar.jar
 */
class TraderDemoTest : NodeBasedTest() {

    val Epermissions = setOf(
            startFlowPermission<IssuerFlow.IssuanceRequester>(),
            startFlowPermission<net.corda.traderdemo.flow.SellerFlow>())

    val Bpermissions = setOf(
            startFlowPermission<IssuerFlow.IssuanceRequester>(),
            startFlowPermission<net.corda.traderdemo.flow.SellerTransferFlow>(),
            startFlowPermission<net.corda.traderdemo.flow.ShowHistory>())


    val bankUser = listOf(User("bank", "b4nk", Bpermissions))
    val exchangeUser = listOf(User("exch", "3xch", Epermissions))
    val badUser = listOf(User("hax0r", "hax0r", Epermissions))

    val user = User("BoC", "b0c", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))

    @Test
    fun `runs trader demo`() {

        val (nodeE, nodeA, nodeB, nodeReg) = Futures.allAsList(
                startNode("Exchange", rpcUsers = exchangeUser),
                startNode("Bank A", rpcUsers = bankUser),
                startNode("Bank B", rpcUsers = bankUser),
                startNode("Auditor", setOf(ServiceInfo(regulator)), rpcUsers = bankUser),
                startNode("BankOfCorda", rpcUsers = listOf(user)),
                startNode("Notary", setOf(ServiceInfo(SimpleNotaryService.type)))
        ).getOrThrow()

        // Bad agent tries to log into the exchange to gain access to Share Issuing
        try {
            val badNodeERpc = CordaRPCClient(nodeE.configuration.rpcAddress!!).
                    start(badUser[0].username, badUser[0].password).proxy()
        } catch (ex: Exception) {
            println("${Emoji.CODE_GREEN_TICK} Correctly invalidated user.")
        }

        val (nodeARpc, nodeBRpc, nodeRegRpc) = listOf(nodeA, nodeB, nodeReg).map {
            val client = CordaRPCClient(it.configuration.rpcAddress!!)
            client.start(bankUser[0].username, bankUser[0].password).proxy()
        }
        val nodeERpc = CordaRPCClient(nodeE.configuration.rpcAddress!!).
                start(exchangeUser[0].username, exchangeUser[0].password).proxy()

        // We give Bank A 30.000 USD to be able to buy shares in AAPL
        TraderDemoClientApi(nodeARpc).runBuyer()

        TraderDemoClientApi(nodeERpc).runSeller(counterparty = nodeA.info.legalIdentity.name, qty = 2,
                ticker = "AAPL", amount = 160.DOLLARS)

        // We assert that the money is where it's supposed to be; same for shares
        val cashA = TraderDemoClientApi(nodeARpc).retrieveCash()
        val cashB = TraderDemoClientApi(nodeBRpc).retrieveCash()
        assertEquals(29680.0, cashA, "${Emoji.CODE_RED_CROSS} Wrong cash deduction logic")
        assertEquals(0.0, cashB, "${Emoji.CODE_RED_CROSS} Wrong cash deduction logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly paid cash")

        val sharesA = TraderDemoClientApi(nodeARpc).retrieveSharesIn("AAPL")
        assertEquals(2, sharesA, "${Emoji.CODE_RED_CROSS} Wrong share issuing logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly issued shares")

        // We're selling them to node B now.

        // Node B has no money - expected exception

        val resultNoCash = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        if (resultNoCash.contains("Not enough cash")) {
            println("${Emoji.CODE_GREEN_TICK} Correctly identified not enough cash available")
        } else if (resultNoCash.contains("internal error")) {
            println("${Emoji.CODE_RED_CROSS} internal error")
            TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        } else {
            throw Exception(Emoji.CODE_RED_CROSS)
        }

        // Issue some cash for node B
        TraderDemoClientApi(nodeBRpc).runBuyer()
        val cashB2 = TraderDemoClientApi(nodeBRpc).retrieveCash()
        assertNotEquals(0.0, cashB2, "${Emoji.CODE_RED_CROSS} Wrong cash deduction logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly issued cash in Bank B")

        // Try to sell more than we have? Exception √
        val resultMoreShares = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 3, counterparty = nodeB.info.legalIdentity.name)
        if (resultMoreShares.contains("Not enough shares") || resultMoreShares.contains("Insufficient shares")){
            println("${Emoji.CODE_GREEN_TICK} Correctly identified not enough shares available.")
        } else {
            throw Exception(Emoji.CODE_RED_CROSS)
        }

        val resultLessMoney = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 130.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        if (resultLessMoney.contains("Unacceptable price")) {
            println("${Emoji.CODE_GREEN_TICK} Correctly identified unacceptable price for shares")
        } else {
            throw Exception(Emoji.CODE_RED_CROSS)
        }
        val msg = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        Thread.sleep(30000)
        val newCashA = TraderDemoClientApi(nodeARpc).retrieveCash()
        val newCashB = TraderDemoClientApi(nodeBRpc).retrieveCash()
        assertEquals(30040.0, newCashA, "${Emoji.CODE_RED_CROSS} Wrong cash deduction logic")
        assertEquals(29640.0, newCashB, "${Emoji.CODE_RED_CROSS} Wrong cash deduction logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly moved cash")

        val newSharesA = TraderDemoClientApi(nodeARpc).retrieveSharesIn("AAPL")
        val newSharesB = TraderDemoClientApi(nodeBRpc).retrieveSharesIn("AAPL")
        assertEquals(2, newSharesB, "${Emoji.CODE_RED_CROSS} Wrong share issuing logic")
        assertEquals(0, newSharesA, "${Emoji.CODE_RED_CROSS} Wrong share issuing logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly moved shares")

        // Regulator requirements - get info for trade above.
        // Extract the tx id of the shares tx
        val txID = msg.substringAfter("Shares: ").substringBefore(" ")
        // Find the recent transaction's details:
        TraderDemoClientApi(nodeRegRpc).showTxHistory(TraderDemoClientApi(nodeBRpc).runAuditor(txID))
        // Now, the auditor can do any regulatory checks on this as he may please. More info is available
        // than just the fields shown
    }
}
