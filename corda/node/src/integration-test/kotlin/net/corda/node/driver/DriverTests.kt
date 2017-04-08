package net.corda.node.driver

import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.list
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.readLines
import net.corda.node.LOGS_DIRECTORY_NAME
import net.corda.node.services.api.RegulatorService
import net.corda.node.services.transactions.SimpleNotaryService
import org.assertj.core.api.Assertions.assertThat
import net.corda.nodeapi.ArtemisMessagingComponent
import org.junit.Test
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class DriverTests {
    companion object {
        val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun nodeMustBeUp(nodeInfo: NodeInfo) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort)
        }

        fun nodeMustBeDown(nodeInfo: NodeInfo) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }
    }

    @Test
    fun `simple node startup and shutdown`() {
        val (notary, regulator) = driver {
            val notary = startNode("TestNotary", setOf(ServiceInfo(SimpleNotaryService.type)))
            val regulator = startNode("Regulator", setOf(ServiceInfo(RegulatorService.type)))

            nodeMustBeUp(notary.getOrThrow().nodeInfo)
            nodeMustBeUp(regulator.getOrThrow().nodeInfo)
            Pair(notary.getOrThrow(), regulator.getOrThrow())
        }
        nodeMustBeDown(notary.nodeInfo)
        nodeMustBeDown(regulator.nodeInfo)
    }

    @Test
    fun `starting node with no services`() {
        val noService = driver {
            val noService = startNode("NoService")
            nodeMustBeUp(noService.getOrThrow().nodeInfo)
            noService.getOrThrow()
        }
        nodeMustBeDown(noService.nodeInfo)
    }

    @Test
    fun `random free port allocation`() {
        val nodeHandle = driver(portAllocation = PortAllocation.RandomFree) {
            val nodeInfo = startNode("NoService")
            nodeMustBeUp(nodeInfo.getOrThrow().nodeInfo)
            nodeInfo.getOrThrow()
        }
        nodeMustBeDown(nodeHandle.nodeInfo)
    }

    @Test
    fun `debug mode enables debug logging level`() {
        // Make sure we're using the log4j2 config which writes to the log file
        val logConfigFile = Paths.get("..", "config", "dev", "log4j2.xml").toAbsolutePath()
        assertThat(logConfigFile).isRegularFile()
        driver(isDebug = true, systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString())) {
            val baseDirectory = startNode("Alice").getOrThrow().configuration.baseDirectory
            val logFile = (baseDirectory / LOGS_DIRECTORY_NAME).list { it.sorted().findFirst().get() }
            val debugLinesPresent = logFile.readLines { lines -> lines.anyMatch { line -> line.startsWith("[DEBUG]") } }
            assertThat(debugLinesPresent).isTrue()
        }
    }
}
