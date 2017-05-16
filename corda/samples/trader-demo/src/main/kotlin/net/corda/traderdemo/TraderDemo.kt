package net.corda.traderdemo

import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.DOLLARS
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import kotlin.system.exitProcess
import net.corda.core.contracts.Amount
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.traderdemo.client.TraderDemoClientApi
import net.corda.traderdemo.flow.SellerFlow
import java.net.InetAddress
import java.util.*

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
        TRANSFER_BACK,
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

        // MC: Buyer should be getting the parameter of "amount" - how much money he has!
        // Currently, that defaults to 30.000 - arbitrary HARDCODED value.
        // Should be changed from gradle.

        // MC: Seller should be getting the amount and counterparty from gradle too!

        val role = options.valueOf(roleArg)!!
        val port = options.valueOf(portArg)
        //val ip = InetAddress.getLocalHost().hostAddress
        val ip = "localhost"
        if (role == Role.BUYER) {
            val host = HostAndPort.fromString("$ip:$port")
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runBuyer(amount)
            }
        } else if (role == Role.SELLER) { //change back to SELLER once you're done testing!
            // MC: Bank B (10009) sells shares to Bank A - by issuing them
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            val qty = options.valueOf(qtyArg)!!
            val ticker = options.valueOf(tickerArg)
            val cparty = options.valueOf(cpartyArg)
            val host = HostAndPort.fromString("$ip:$port")
            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runSeller(amount, cparty, qty, ticker)
            }
        } else if (role == Role.SELLER_TRANSFER) {
            // MC: Bank A (10006) sells shares back to bank B - by trading (moving)
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            val qty = options.valueOf(qtyArg)!!
            val ticker = options.valueOf(tickerArg)
            val host = HostAndPort.fromString("$ip:$port")
            val cparty = options.valueOf(cpartyArg)
            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runSellerTransfer(amount, cparty, qty, ticker)
            }
        } else if (role == Role.TRANSFER_BACK) {
            // MC: Bank B (10009) moves back the shares to Bank A
            val amount = Amount.parseCurrency(options.valueOf(amountArg)!!)
            val qty = options.valueOf(qtyArg)!!
            val ticker = options.valueOf(tickerArg)
            val host = HostAndPort.fromString("$ip:$port")
            val cparty = options.valueOf(cpartyArg)
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
        }
 else if (role == Role.RANDOM) {
            val portA = 10006
            val portB = 10009
            val portE = 10041
            val portHL = 10048
            val portBS = 10021

            val ports = listOf(Pair(portA, "Bank A"),
                    Pair(portB, "Bank B"),
                    Pair(portHL, "Hargreaves Lansdown"),
                    Pair(portBS, "Beaufort Securities"),
                    Pair(portE, "Exchange"))

            val stocks = listOf(Pair("AAPL", 160.DOLLARS),
                    Pair("GS", 230.DOLLARS),
                    Pair("MSFT", 75.DOLLARS))

            // give 100.000 dollars to banks
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portA")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
            } catch (ex: Exception) {}
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portB")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
            } catch (ex: Exception) {}
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portHL")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
            } catch (ex: Exception) {}
            try {
                CordaRPCClient(HostAndPort.fromString("$ip:$portBS")).use("demo", "demo") {
                    TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                }
            } catch (ex: Exception) {}


            // Then we have a few random variables and start the trades. Sure, this would work best with about 4-5
            // banks (Bank A, Bank B, HL and BS)
            val rand = Random()
            var tries = 10
            while (tries > 0) {
                val stock = rand.nextInt(3)
                val p = rand.nextInt(5)
                try {
                    CordaRPCClient(HostAndPort.fromString("$ip:$portE")).use("demo", "demo") {
                        TraderDemoClientApi(this).runSellerR(stocks[stock].second, ports[p].second, 1, stocks[stock].first)
                    }
                } catch (ex: Exception) {}
                tries--
            }
            println("Performing transactions now! \n\n\n\n\n\n\n")
            while (true) {
                val stock = rand.nextInt(3)
                val p = rand.nextInt(5)
                var msg = ""
                CordaRPCClient(HostAndPort.fromString("$ip:$portE")).use("demo", "demo") {
                    msg = TraderDemoClientApi(this).runSellerTransferR(stocks[stock].second, ports[p].second, 1, stocks[stock].first)
                }
                if (msg.contains("Insufficient balance, missing")) {
                    try {
                        CordaRPCClient(HostAndPort.fromString("$ip:${ports[p].second}")).use("demo", "demo") {
                            TraderDemoClientApi(this).runBuyer(30000.DOLLARS)
                        }
                    } catch (ex: Exception) {}
                } else if (msg.contains("Insufficient shares in")) {
                    try {
                        CordaRPCClient(HostAndPort.fromString("$ip:$portE")).use("demo", "demo") {
                            msg = TraderDemoClientApi(this).runSellerR(stocks[stock].second, ports[p].second, 1, stocks[stock].first)
                        }
                    } catch (ex: Exception) {}
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
                    CordaRPCClient(HostAndPort.fromString("$ip:$portBS")).use("demo", "demo") {
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
