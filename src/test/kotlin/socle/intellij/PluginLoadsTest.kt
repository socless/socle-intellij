package socle.intellij

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginLoadsTest : BasePlatformTestCase() {
    fun testPluginIsLoaded() {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("socless.socle"))
        assertNotNull("socless.socle is loaded", plugin)
        assertTrue(plugin!!.isEnabled)
    }
}
