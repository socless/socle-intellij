package socle.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SocleConfigurableTest : BasePlatformTestCase() {
    fun testApplyStoresAndNotifies() {
        val c = SocleConfigurable()
        c.createComponent()
        var notified: SocleSettings.State? = null
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(SocleSettingsListener.TOPIC, SocleSettingsListener { notified = it })
        assertFalse(c.isModified)
        c.apply()
        assertNotNull(notified)
        assertEquals("emitVcd", SocleSettings.getInstance().state.waveFlag)
        c.disposeUIResources()
    }
}
