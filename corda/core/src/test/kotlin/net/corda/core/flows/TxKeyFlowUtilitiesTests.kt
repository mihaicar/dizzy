package net.corda.core.flows

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.MOCK_IDENTITY_SERVICE
import net.corda.testing.ledger
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

class TxKeyFlowUtilitiesTests {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
        net.identities += MOCK_IDENTITY_SERVICE.identities
    }

    @Test
    fun `issue key`() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        net = MockNetwork(false, true)

        // Set up values we'll need
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = net.createPartyNode(notaryNode.info.address, ALICE.name)
        val bobNode = net.createPartyNode(notaryNode.info.address, BOB.name)
        val aliceKey: Party = aliceNode.services.myInfo.legalIdentity
        val bobKey: Party = bobNode.services.myInfo.legalIdentity

        // Run the flows
        TxKeyFlow.registerFlowInitiator(bobNode.services)
        val requesterFlow = aliceNode.services.startFlow(TxKeyFlow.Requester(bobKey))

        // Get the results
        val actual: CompositeKey = requesterFlow.resultFuture.get().first
        assertNotNull(actual)
    }
}