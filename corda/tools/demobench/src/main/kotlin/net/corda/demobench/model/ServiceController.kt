package net.corda.demobench.model

import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.logging.Level
import tornadofx.Controller

class ServiceController(resourceName: String = "/services.conf") : Controller() {

    val services: List<String> = loadConf(resources.url(resourceName))

    val notaries: List<String> = services.filter { it.startsWith("corda.notary.") }.toList()

    /*
     * Load our list of known extra Corda services.
     */
    private fun loadConf(url: URL?): List<String> {
        if (url == null) {
            return emptyList()
        } else {
            try {
                val set = TreeSet<String>()
                InputStreamReader(url.openStream()).useLines { sq ->
                    sq.forEach { line ->
                        val service = line.trim()
                        set.add(service)

                        log.info("Supports: $service")
                    }
                }
                return set.toList()
            } catch (e: IOException) {
                log.log(Level.SEVERE, "Failed to load $url: ${e.message}", e)
                return emptyList()
            }
        }
    }

}
