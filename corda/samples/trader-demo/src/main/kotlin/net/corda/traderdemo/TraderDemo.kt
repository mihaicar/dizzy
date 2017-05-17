package net.corda.traderdemo

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
        AUDIT
    }

    companion object {
        val logger: Logger = loggerFor<TraderDemo>()
    }

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
                } else if (msg.contains("Insufficient shares in")) {
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
    }

    fun printHelp(parser: OptionParser) {
        println("""
        Usage: trader-demo --role [BUYER|SELLER]
        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }
}
