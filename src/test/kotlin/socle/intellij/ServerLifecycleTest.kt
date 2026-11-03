package socle.intellij

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import socle.intellij.lsp.SocleClientFeatures
import socle.intellij.panel.SoclePanel
import socle.intellij.panel.SocleTabFile

class ServerLifecycleTest : BasePlatformTestCase() {
    private fun freshService() = SocleProjectService(project).also { Disposer.register(testRootDisposable, it) }

    /** LSP4IJ stops a server 5 s after its last document closes unless the client keeps it: the panel lives on it. */
    fun testServerOutlivesTheLastScalaEditor() {
        assertTrue(SocleClientFeatures().keepServerAlive())
    }

    fun testOnlyUnexpectedStopsTakeFromTheBudget() {
        val s = freshService()
        assertTrue(s.onUnexpectedStop(0)); assertTrue(s.onUnexpectedStop(1_000)); assertTrue(s.onUnexpectedStop(2_000))
        assertNull(s.problem)
        assertFalse(s.onUnexpectedStop(3_000))
        assertTrue(s.problem!!, s.problem!!.contains("stopped 3 times in 3 minutes"))
        assertTrue(s.onUnexpectedStop(200_000))
    }

    /** A tab IntelliJ reopens itself (Reopen Closed Tab) is the panel: closing it closes the panel. */
    fun testThePanelAdoptsTheTabItIsShownIn() {
        var closed = 0
        project.messageBus.connect(testRootDisposable).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun panelClosed() { closed++ }
        })
        val f = SocleTabFile(SocleTabFile.Kind.PANEL, "Socle design")
        SoclePanel.getInstance(project).adopt(f)
        project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
            .fileClosed(FileEditorManager.getInstance(project), f)
        assertEquals(1, closed)
    }
}
