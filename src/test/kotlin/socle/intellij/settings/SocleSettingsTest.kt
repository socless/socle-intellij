package socle.intellij.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SocleSettingsTest {
    @Test
    fun defaultsMatchVscode() {
        val s = SocleSettings.State()
        assertEquals("", s.javaPath)
        assertEquals("", s.serverJar)
        assertEquals("emitVcd", s.waveFlag)
        assertEquals("surfer", s.surferCommand)
        assertEquals("surfer", s.waveViewer)
        assertEquals("", s.waveViewerCommand)
        assertEquals(true, s.wavesFollowSelection)
        assertEquals(false, s.wavesShowClockReset)
        assertEquals(false, s.wavesFoldBundles)
        assertEquals(true, s.wavesShowWires)
        assertEquals(true, s.wavesColorByDirection)
        assertEquals(150, s.wavesFollowDelayMs)
        assertEquals(true, s.followCursor)
        assertEquals(false, s.schematicColorizeComponents)
        assertEquals(false, s.schematicColorizeWires)
        assertEquals("comfortable", s.schematicDensity)
        assertEquals("normal", s.schematicLabelSize)
        assertEquals(true, s.schematicAutoOptimize)
        assertEquals(true, s.schematicHintBar)
        assertEquals(false, s.schematicShowLayers)
        assertEquals(emptyList<String>(), s.schematicHide)
    }

    @Test
    fun serverSettingsOmitClientKeys() {
        val s = SocleSettings.State().apply { schematicHide = mutableListOf("op", "reg") }
        val m = s.toServerSettings()
        assertFalse(m.containsKey("javaPath"))
        assertFalse(m.containsKey("serverJar"))
        assertEquals(listOf("op", "reg"), m["schematicHide"])
        assertEquals(150, m["wavesFollowDelayMs"])
        assertEquals("emitVcd", m["waveFlag"])
        assertEquals(19, m.size)
    }
}
