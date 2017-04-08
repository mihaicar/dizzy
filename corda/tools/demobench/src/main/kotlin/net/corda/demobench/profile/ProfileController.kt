package net.corda.demobench.profile

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.*
import java.util.*
import java.util.function.BiPredicate
import java.util.logging.Level
import java.util.stream.StreamSupport
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter
import net.corda.demobench.model.*
import net.corda.demobench.plugin.PluginController
import net.corda.demobench.plugin.inPluginsDir
import net.corda.demobench.plugin.isPlugin
import tornadofx.Controller

class ProfileController : Controller() {

    private val jvm by inject<JVMConfig>()
    private val baseDir: Path = jvm.dataHome
    private val nodeController by inject<NodeController>()
    private val pluginController by inject<PluginController>()
    private val installFactory by inject<InstallFactory>()
    private val chooser = FileChooser()

    init {
        chooser.title = "DemoBench Profiles"
        chooser.initialDirectory = baseDir.toFile()
        chooser.extensionFilters.add(ExtensionFilter("DemoBench profiles (*.profile)", "*.profile", "*.PROFILE"))
    }

    /**
     * Saves the active node configurations into a ZIP file, along with their Cordapps.
     */
    @Throws(IOException::class)
    fun saveProfile(): Boolean {
        val target = forceExtension(chooser.showSaveDialog(null) ?: return false, ".profile")
        log.info("Saving profile as: $target")

        val configs = nodeController.activeNodes

        // Delete the profile, if it already exists. The save
        // dialogue has already confirmed that this is OK.
        target.delete()

        // Write the profile as a ZIP file.
        try {
            FileSystems.newFileSystem(URI.create("jar:" + target.toURI()), mapOf("create" to "true")).use { fs ->
                configs.forEach { config ->
                    // Write the configuration file.
                    val nodeDir = Files.createDirectories(fs.getPath(config.key))
                    val file = Files.write(nodeDir.resolve("node.conf"), config.toText().toByteArray(UTF_8))
                    log.info("Wrote: $file")

                    // Write all of the non-built-in plugins.
                    val pluginDir = Files.createDirectory(nodeDir.resolve("plugins"))
                    pluginController.userPluginsFor(config).forEach {
                        val plugin = Files.copy(it, pluginDir.resolve(it.fileName.toString()))
                        log.info("Wrote: $plugin")
                    }
                }
            }

            log.info("Profile saved.")
        } catch (e: IOException) {
            log.log(Level.SEVERE, "Failed to save profile '$target': '${e.message}'", e)
            target.delete()
            throw e
        }

        return true
    }

    private fun forceExtension(target: File, ext: String): File {
        return if (target.extension.isEmpty()) File(target.parent, target.name + ext) else target
    }

    /**
     * Parses a profile (ZIP) file.
     */
    @Throws(IOException::class)
    fun openProfile(): List<InstallConfig>? {
        val chosen = chooser.showOpenDialog(null) ?: return null
        log.info("Selected profile: $chosen")

        val configs = LinkedList<InstallConfig>()

        FileSystems.newFileSystem(chosen.toPath(), null).use { fs ->
            // Identify the nodes first...
            StreamSupport.stream(fs.rootDirectories.spliterator(), false)
                .flatMap { Files.find(it, 2, BiPredicate { p, attr -> "node.conf" == p?.fileName.toString() && attr.isRegularFile }) }
                .map { file ->
                    try {
                        val config = installFactory.toInstallConfig(parse(file), baseDir)
                        log.info("Loaded: $file")
                        config
                    } catch (e: Exception) {
                        log.log(Level.SEVERE, "Failed to parse '$file': ${e.message}", e)
                        throw e
                    }
                // Java seems to "walk" through the ZIP file backwards.
                // So add new config to the front of the list, so that
                // our final list is ordered to match the file.
                }.forEach { configs.addFirst(it) }

            val nodeIndex = configs.map { it.key to it }.toMap()

            // Now extract all of the plugins from the ZIP file,
            // and copy them to a temporary location.
            StreamSupport.stream(fs.rootDirectories.spliterator(), false)
                .flatMap { Files.find(it, 3, BiPredicate { p, attr -> p.inPluginsDir() && p.isPlugin() && attr.isRegularFile }) }
                .forEach { plugin ->
                    val config = nodeIndex[plugin.getName(0).toString()] ?: return@forEach

                    try {
                        val pluginDir = Files.createDirectories(config.pluginDir)
                        Files.copy(plugin, pluginDir.resolve(plugin.fileName.toString()))
                        log.info("Loaded: $plugin")
                    } catch (e: Exception) {
                        log.log(Level.SEVERE, "Failed to extract '$plugin': ${e.message}", e)
                        configs.forEach { c -> c.deleteBaseDir() }
                        throw e
                    }
                }
        }

        return configs
    }

    private fun parse(path: Path): Config = Files.newBufferedReader(path).use {
        return ConfigFactory.parseReader(it)
    }

}
