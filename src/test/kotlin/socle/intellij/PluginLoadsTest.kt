package socle.intellij

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginLoadsTest : BasePlatformTestCase() {
    private val plugin get() = PluginManagerCore.getPlugin(PluginId.getId("socless.socle"))

    fun testPluginIsLoaded() {
        assertNotNull("socless.socle is loaded", plugin)
        assertTrue(plugin!!.isEnabled)
    }

    // The Scala plugin bundles its own lsp4j-jsonrpc: a classloader dependency on it makes Socle see that copy,
    // and LSP4IJ then cannot build the server proxy (the server fails to start, again and again).
    fun testNoClassloaderDependencyOnTheScalaPlugin() {
        val ids = plugin!!.dependencies.map { it.pluginId.idString }
        assertFalse(ids.toString(), "org.intellij.scala" in ids)
        assertEquals("LSP4IJ comes first", "com.redhat.devtools.lsp4ij", ids.first())
    }
}
