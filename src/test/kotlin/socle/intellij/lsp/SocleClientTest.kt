package socle.intellij.lsp

import com.google.gson.JsonParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService

class SocleClientTest : BasePlatformTestCase() {
    private fun obj(s: String) = JsonParser.parseString(s).asJsonObject

    override fun tearDown() {
        try {
            // openPanel opens the panel tab (SoclePanel listens): close it.
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
        } finally {
            super.tearDown()
        }
    }

    fun testOpenPanelPublishesAndRemembersTheUrl() {
        var got: Pair<String, String?>? = null
        project.messageBus.connect(testRootDisposable).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun openPanel(url: String, test: String?) { got = url to test }
        })
        SocleClient(project).openPanel(obj("""{"url":"http://127.0.0.1:4711/","test":"GCD computes gcd(48, 18)"}"""))
        assertEquals("http://127.0.0.1:4711/" to "GCD computes gcd(48, 18)", got)
        val service = SocleProjectService.getInstance(project)
        assertEquals("http://127.0.0.1:4711/", service.panelUrl)
        assertEquals("GCD computes gcd(48, 18)", service.lastTest)
    }

    fun testStatusUpdatesTheRunTracker() {
        SocleClient(project).status(obj("""{"status":"running","suite":"gcd.GcdSpec","test":"t","message":""}"""))
        val key = SidebarModel.TestKey("gcd.GcdSpec", "t")
        assertEquals(SidebarModel.RunState.Running, SocleProjectService.getInstance(project).tracker.state(key))
        SocleClient(project).status(obj("""{"status":"ok","suite":"gcd.GcdSpec","test":"t","message":""}"""))
        assertEquals(SidebarModel.RunState.Shown, SocleProjectService.getInstance(project).tracker.state(key))
    }

    fun testTestsChangedIsPublished() {
        var n = 0
        project.messageBus.connect(testRootDisposable).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun testsChanged() { n++ }
        })
        SocleClient(project).testsChanged()
        assertEquals(1, n)
    }

    fun testSettingsAreTheSocleSection() {
        val settings = SocleClient(project).settingsForServer()
        assertEquals("emitVcd", (settings["socle"] as Map<*, *>)["waveFlag"])
    }
}
