package net.corda.traderdemo

import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.Emoji
import net.corda.flows.IssuerFlow
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.node.NodeBasedTest
import net.corda.traderdemo.client.TraderDemoClientApi
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Ensure the quasar.jar is added to the configuration of the test when ran in IntelliJ
 */
class TraderDemoTest : NodeBasedTest() {

    val Epermissions = setOf(
            startFlowPermission<IssuerFlow.IssuanceRequester>(),
            startFlowPermission<net.corda.traderdemo.flow.SellerFlow>())

    val Bpermissions = setOf(
            startFlowPermission<IssuerFlow.IssuanceRequester>(),
            startFlowPermission<net.corda.traderdemo.flow.SellerTransferFlow>())


    val bankUser = listOf(User("bank", "b4nk", Bpermissions))
    val exchangeUser = listOf(User("exch", "3xch", Epermissions))
    val badUser = listOf(User("hax0r", "hax0r", Epermissions))

    val user = User("BoC", "b0c", permissions = setOf(startFlowPermission<IssuerFlow.IssuanceRequester>()))

    @Test
    fun `runs trader demo`() {

        val (nodeE, nodeA, nodeB) = Futures.allAsList(
                startNode("Exchange", rpcUsers = exchangeUser),
                startNode("Bank A", rpcUsers = bankUser),
                startNode("Bank B", rpcUsers = bankUser),
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

        val (nodeARpc, nodeBRpc) = listOf(nodeA, nodeB).map {
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
        assertEquals(29680.0, cashA, "Wrong cash deduction logic")
        assertEquals(0.0, cashB, "Wrong cash deduction logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly paid cash")

        val sharesA = TraderDemoClientApi(nodeARpc).retrieveSharesIn("AAPL")
        assertEquals(2, sharesA, "Wrong share issuing logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly issued shares")

        // We're selling them to node B now.

        // Node B has no money - expected exception

        val resultNoCash = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        if (resultNoCash.contains("Not enough cash")) {
            println("${Emoji.CODE_GREEN_TICK} Correctly identified not enough cash available")
        }

        // Issue some cash for node B
        TraderDemoClientApi(nodeBRpc).runBuyer(500.DOLLARS)

        // Try to sell more than we have? Exception
        val resultMoreShares = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 3, counterparty = nodeB.info.legalIdentity.name)
        if (resultMoreShares.contains("Not enough shares")){
            println("${Emoji.CODE_GREEN_TICK} Correctly identified not enough shares available.")
        }

        val resultLessMoney = TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 130.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)
        if (resultLessMoney.contains("Unacceptable price")) {
            println("${Emoji.CODE_GREEN_TICK} Correctly identified unacceptable price for shares")
        }

        TraderDemoClientApi(nodeARpc).runSellerTransferR(amount = 180.DOLLARS, ticker = "AAPL", qty = 2, counterparty = nodeB.info.legalIdentity.name)

        val newCashA = TraderDemoClientApi(nodeARpc).retrieveCash()
        val newCashB = TraderDemoClientApi(nodeBRpc).retrieveCash()
        assertEquals(30040.0, newCashA, "Wrong cash deduction logic")
        assertEquals(140.0, newCashB, "Wrong cash deduction logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly moved cash")
        Thread.sleep(30000)
        val newSharesA = TraderDemoClientApi(nodeARpc).retrieveSharesIn("AAPL")
        val newSharesB = TraderDemoClientApi(nodeBRpc).retrieveSharesIn("AAPL")
        assertEquals(2, newSharesB, "Wrong share issuing logic")
        assertEquals(0, newSharesA, "Wrong share issuing logic")
        println("${Emoji.CODE_GREEN_TICK} Correctly moved shares")
    }
}
