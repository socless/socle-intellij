package socle.intellij

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import socle.intellij.panel.SoclePanel

class StartupTest : BasePlatformTestCase() {
    // SoclePanel listens for socle/openPanel from its constructor: something must create it before
    // the first openPanel arrives, or the panel never opens.
    fun testStartupCreatesThePanelService() {
        runBlocking { SocleStartup().execute(project) }
        assertNotNull(project.serviceIfCreated<SoclePanel>())
    }
}
