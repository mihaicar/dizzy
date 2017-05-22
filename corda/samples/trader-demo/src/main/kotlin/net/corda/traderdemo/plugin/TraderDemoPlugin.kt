package net.corda.traderdemo.plugin

import net.corda.core.contracts.Amount
import net.corda.core.crypto.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.traderdemo.flow.BuyerFlow
import net.corda.traderdemo.flow.BuyerTransferFlow
import net.corda.traderdemo.flow.SellerFlow
import net.corda.traderdemo.flow.SellerTransferFlow
import java.util.function.Function
import net.corda.traderdemo.api.TraderApi

class TraderDemoPlugin : CordaPluginRegistry() {
    // A list of Flows that are required for this cordapp
//    override val webApis = listOf(Function(::TraderApi))
//    override val staticServeDirs: Map<String, String> = mapOf(
//            "trade" to javaClass.classLoader.getResource("web").toExternalForm()
//    )
    override val requiredFlows: Map<String, Set<String>> = mapOf(
            SellerFlow::class.java.name to setOf(Party::class.java.name, Amount::class.java.name, Long::class.java.name, String::class.java.name),
            SellerTransferFlow::class.java.name to setOf(Party::class.java.name, Amount::class.java.name, Long::class.java.name, String::class.java.name)
    )
    override val servicePlugins = listOf(Function(BuyerFlow::Service), Function(BuyerTransferFlow::Service))
}
