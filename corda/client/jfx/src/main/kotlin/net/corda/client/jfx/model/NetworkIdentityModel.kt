package net.corda.client.jfx.model

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.firstOrDefault
import net.corda.client.jfx.utils.firstOrNullObservable
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.map
import net.corda.core.crypto.CompositeKey
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache.MapChange
import java.security.PublicKey

class NetworkIdentityModel {
    private val networkIdentityObservable by observable(NodeMonitorModel::networkMap)

    val networkIdentities: ObservableList<NodeInfo> =
            networkIdentityObservable.fold(FXCollections.observableArrayList()) { list, update ->
                list.removeIf {
                    when (update) {
                        is MapChange.Removed -> it == update.node
                        is MapChange.Modified -> it == update.previousNode
                        else -> false
                    }
                }
                list.addAll(update.node)
            }

    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)

    val parties: ObservableList<NodeInfo> = networkIdentities.filtered { !it.isCordaService() }
    val notaries: ObservableList<NodeInfo> = networkIdentities.filtered { it.advertisedServices.any { it.info.type.isNotary() } }
    val myIdentity = rpcProxy.map { it?.nodeIdentity() }

    private fun NodeInfo.isCordaService(): Boolean {
        // TODO: better way to identify Corda service?
        return advertisedServices.any { it.info.type.isNetworkMap() || it.info.type.isNotary() }
    }

    // TODO: Use Identity Service in service hub instead?
    fun lookup(compositeKey: CompositeKey): ObservableValue<NodeInfo?> = parties.firstOrDefault(notaries.firstOrNullObservable { it.notaryIdentity.owningKey == compositeKey }) {
        it.legalIdentity.owningKey == compositeKey
    }

    fun lookup(publicKey: PublicKey): ObservableValue<NodeInfo?> = parties.firstOrDefault(notaries.firstOrNullObservable { it.notaryIdentity.owningKey.keys.any { it == publicKey } }) {
        it.legalIdentity.owningKey.keys.any { it == publicKey }
    }
}
