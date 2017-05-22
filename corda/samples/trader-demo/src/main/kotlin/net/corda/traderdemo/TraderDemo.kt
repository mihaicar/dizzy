package net.corda.traderdemo

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.utilities.loggerFor
import net.corda.traderdemo.client.TraderDemoClientApi
import org.slf4j.Logger
import java.util.*
import kotlin.system.exitProcess

/**
 * This entry point allows for command line running of the trader demo functions on nodes started by Main.kt.
 */
fun main(args: Array<String>) {
    TraderDemo().main(args)
}

private class TraderDemo {
    enum class Role {
        BUYER,
        SELLER,
        SELLER_TRANSFER,
        DISPLAY,
        RANDOM,
        AUDIT,
        RANDOM_ISSUE,
        RANDOM_TRANSACT
    }

    companion object {
        val logger: Logger = loggerFor<TraderDemo>()
    }

    @Suspendable
    fun main(args: Array<String>) {
        val parser = OptionParser()

        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
        val amountArg = parser.accepts("amount").withOptionalArg().ofType(String::class.java)
        val qtyArg = parser.accepts("quantity").withOptionalArg().ofType(Long::class.java)
        val tickerArg = parser.accepts("ticker").withOptionalArg().ofType(String::class.java)
        val portArg = parser.accepts("port").withOptionalArg().ofType(Integer::class.java)
        val cpartyArg = parser.accepts("cparty").withOptionalArg().ofType(String::class.java)
        val txArg = parser.accepts("tx").withOptionalArg().ofType(String::class.java)
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            logger.error(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        // What happens next depends on the role. The buyer sits around waiting for a trade to start. The seller role
        // will contact the buyer and actually make something happen.

        // MC: There are several roles that can be called as follows:
        /** MC
         * Buyer: makes a request for cash to be able to pay for future trades
         *        BankOfCorda issues the cash without any pre-requisites (mocked up version of reality)
         *
         * Seller: contacts the Exchange which generates shares (issues them) and moves
         *        them to the counterparty's ledger in return for cash.
         *        * can happen between banks, but this behaviour is prevented in the front end currently
         *        * to say that only Exchanges are allowed to issue shares
         *
         * Seller Transfer: emulates trades between 2 banks (nodes). It follows a 2 party trade
         *        agreement - buyer pays cash for the shares offered by the seller
         *
         * Display: displays cash and shares available for the given node
         *
         * Auditor: ideally, it would offer information on a requested trade ID. however, auditor trail
         *         has not been implemented as of this version of corda, but it is planned for future release
         *
         * Random: generates some cash in the nodes' vaults, issues some shares then starts a trading cycle
         *         between the participants; runs to infinity!
         */

        val role = options.valueOf(roleArg)!!
        val port = options.valueOf(portArg)
        val ip = "localhost"
        if (role == Role.BUYER) {
            val host = HostAndPort.fromString("$ip:$port")
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)

            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runBuyer(amount)
            }
        } else if (role == Role.SELLER) {
            val host = HostAndPort.fromString("$ip:$port")
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            val cparty = options.valueOf(cpartyArg)
            val qty = options.valueOf(qtyArg)!!
            val ticker = options.valueOf(tickerArg)

            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runSeller(amount, cparty, qty, ticker)
            }
        } else if (role == Role.SELLER_TRANSFER) {
            val host = HostAndPort.fromString("$ip:$port")
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            val cparty = options.valueOf(cpartyArg)
            val qty = options.valueOf(qtyArg)!!
            val ticker = options.valueOf(tickerArg)

            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runSellerTransfer(amount, cparty, qty, ticker)
            }
        } else if (role == Role.DISPLAY) {
            val host = HostAndPort.fromString("$ip:$port")

            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runDisplay()
            }
        } else if (role == Role.AUDIT) {
            val host = HostAndPort.fromString("$ip:10002")
            val cps = listOf("Bank A", "Bank B")
            val tx = options.valueOf(txArg)

            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runAuditor(cps, tx)
            }
        } else if (role == Role.RANDOM) {
            val portA = 10006
            val portB = 10009
            val portE = 10041
            val portHL = 10048

            val ports = listOf(Pair(portA, "Bank A"),
                    Pair(portB, "Bank B"),
                    Pair(portHL, "Hargreaves"),
                    Pair(portE, "Exchange"))

            val stocks = listOf(Pair("AAPL", 160.DOLLARS),
                    Pair("MSFT", 75.DOLLARS))

            // Money issuing
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portA")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
                println("Money issued for Bank A")
            } catch (ex: Exception) {
                println("NOT ISSUED A $ex")
            }
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portB")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
                println("Money issued for Bank B")
            } catch (ex: Exception) {
                println("NOT ISSUED B $ex")
            }
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portHL")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
                println("Money issued for HL")
            } catch (ex: Exception) {
                println("NOT ISSUED HL $ex")
            }

            // We issue some shares (no transfers)
            val rand = Random()
            var tries = 10
            while (tries > 0) {
                val stock = rand.nextInt(2)
                val p = rand.nextInt(3)
                try {
                    CordaRPCClient(HostAndPort.fromString("$ip:$portE")).use("demo", "demo") {
                        TraderDemoClientApi(this).runSellerR(stocks[stock].second, ports[p].second, 1, stocks[stock].first)
                    }
                } catch (ex: Exception) {
                    println("ERROR SHARES 1 $ex")
                }
                tries--
            }



            println("Performing transactions now! \n\n\n\n\n\n\n")
            while (true) {
                val stock = rand.nextInt(2)
                val p = rand.nextInt(3)
                val p2 = rand.nextInt(3)
                var msg = ""
                CordaRPCClient(HostAndPort.fromString("$ip:${ports[p2].first}")).use("demo", "demo") {
                    msg = TraderDemoClientApi(this).runSellerTransferR(stocks[stock].second, ports[p].second, 1, stocks[stock].first)
                }
                println(msg)
                if (msg.contains("Insufficient balance, missing")) {
                    try {
                        CordaRPCClient(HostAndPort.fromString("$ip:${ports[p].first}")).use("demo", "demo") {
                            TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                        }
                        println("Money issued because of insufficient funds")
                    } catch (ex: Exception) {
                        println("ERROR MONEY $ex")
                    }
                } else if (msg.contains("IndexOutOfBounds")) {
                    try {
                        CordaRPCClient(HostAndPort.fromString("$ip:$portE")).use("demo", "demo") {
                            msg = TraderDemoClientApi(this).runSellerR(stocks[stock].second, ports[p2].second, 1, stocks[stock].first)
                        }
                        println(msg)
                    } catch (ex: Exception) {
                        println("ERROR SHARES $ex")
                    }
                }
                tries++
                if (tries % 5 == 0) {
                    CordaRPCClient(HostAndPort.fromString("$ip:$portA")).use("demo", "demo") {
                        TraderDemoClientApi(this).runDisplay()
                    }
                    CordaRPCClient(HostAndPort.fromString("$ip:$portB")).use("demo", "demo") {
                        TraderDemoClientApi(this).runDisplay()
                    }
                    CordaRPCClient(HostAndPort.fromString("$ip:$portHL")).use("demo", "demo") {
                        TraderDemoClientApi(this).runDisplay()
                    }
                }
            }
        }
        /** These are used for the shared marketplace (on 2 machines)
            We will have as follows:
            M1: Bank A, Bank B, BankOfCorda, Beaufort - ip1
            M2: Notary, Hargreaves, Exchange, Bank C - ip2
        */
        else if (role == Role.RANDOM_ISSUE) {
            val vals = varDeclaration()
            @Suppress("UNCHECKED_CAST")
            val pi = vals[0] as Map<String, Pair<Int, String>>
            @Suppress("UNCHECKED_CAST")
            val stocks = vals[1] as Map<String, Amount<Currency>>
            val rand = vals[2] as Random
            val stocksAsArray = ArrayList<String>(stocks.keys)
            val banksAsArray = ArrayList<String>(pi.keys.minus("Exchange"))

            // Issue money for Banks
            for (entity in banksAsArray) {
                tryIssueCashTo(entity, pi)
            }

            // Issue some shares for the banks - we start with 20
            var tries = 20
            while (tries > 0) {
                val stock = rand.nextInt(stocks.size)
                val p = rand.nextInt(pi.size - 1)
                tryIssueSharesTo(banksAsArray[p], "Exchange", pi, stocks[stocksAsArray[stock]] as Amount<Currency>, stocksAsArray[stock])
                tries--
            }

        } else if (role == Role.RANDOM_TRANSACT) {
            val vals = varDeclaration()
            @Suppress("UNCHECKED_CAST")
            val pi = vals[0] as Map<String, Pair<Int, String>>
            @Suppress("UNCHECKED_CAST")
            val stocks = vals[1] as Map<String, Amount<Currency>>
            val rand = vals[2] as Random
            val stocksAsArray = ArrayList<String>(stocks.keys)
            val banksAsArray = ArrayList<String>(pi.keys.minus("Exchange"))
            var tries = 0
            while (true) {
                val stock = rand.nextInt(2)
                val buyer = banksAsArray[rand.nextInt(pi.size - 1)]
                val seller = banksAsArray[rand.nextInt(pi.size - 1)]
                transferShares(buyer, seller, pi, stocks[stocksAsArray[stock]] as Amount<Currency>, stocksAsArray[stock])
                tries++
                if (tries % 10 == 0) {
                    for (entity in banksAsArray) {
                        displayBalances(entity, pi)
                    }
                }
            }
        }
    }

    private fun displayBalances(entity: String, pi: Map<String, Pair<Int, String>>) {
        CordaRPCClient(HostAndPort.fromString("${pi[entity]?.second}:${pi[entity]?.first}")).use("demo", "demo") {
            TraderDemoClientApi(this).runDisplay()
        }
    }

    private fun transferShares(buyer: String, seller: String, pi: Map<String, Pair<Int, String>>,
                               amount: Amount<Currency>, ticker: String) {
        var msg = ""
        try {
            CordaRPCClient(HostAndPort.fromString("${pi[seller]?.second}:${pi[seller]?.first}")).use("demo", "demo") {
                msg = TraderDemoClientApi(this).runSellerTransferR(amount, buyer, 1, ticker)
            }
        } catch (ex: Exception) {
            println("Transfer not performed => $ex")
        }

        if (msg.contains("cash")) {
            println("Not enough cash to buy shares!")
            tryIssueCashTo(buyer, pi)
        } else if (msg.contains("shares")) {
            println("Not enough shares to sell!")
            tryIssueSharesTo(buyer, "Exchange", pi, amount, ticker)
        }
    }

    private fun tryIssueSharesTo(entity: String, seller: String, pi: Map<String, Pair<Int, String>>,
                                 amount: Amount<Currency>, ticker: String) {
        try {
            CordaRPCClient(HostAndPort.fromString("${pi[seller]?.second}:${pi[seller]?.first}")).use("demo", "demo") {
                TraderDemoClientApi(this).runSellerR(amount, entity, 1, ticker)
            }
        } catch (ex: Exception) {
            println("No shares issued ($ex)")
        }
    }

    private fun tryIssueCashTo(entity: String, pi: Map<String, Pair<Int, String>>) {
        try {
            CordaRPCClient(HostAndPort.fromString("${pi[entity]?.second}:${pi[entity]?.first}")).use("demo", "demo") {
                TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
            }
            println("Money issued for $entity")
        } catch (ex: Exception) {
            println("Not fully issued. ($ex)")
        }
    }

    private fun varDeclaration(): List<Any> {
        val ipM1 = "146.169.47.223"
        val ipM2 = "146.169.47.221"
        val ports = mapOf(
                "Bank A" to Pair(10006, ipM1),
                "Bank B" to Pair (10009, ipM1),
                "Bank C" to Pair (10015, ipM2),
                "Beaufort" to Pair(10021, ipM1),
                "Hargreaves" to Pair(10048, ipM2),
                "Exchange" to Pair(10041, ipM2))
        val rand = Random()
        val stocks = mapOf(
                "AAPL" to 160.DOLLARS,
                "MSFT" to 75.DOLLARS,
                "GS" to 230.DOLLARS)
        return listOf(ports, stocks, rand)
    }

    fun printHelp(parser: OptionParser) {
        println("""
        Usage: trader-demo --role [BUYER|SELLER]
        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }
}
