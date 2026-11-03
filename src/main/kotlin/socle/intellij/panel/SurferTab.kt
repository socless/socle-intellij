package socle.intellij.panel

import com.google.gson.JsonObject
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import socle.intellij.Logic
import socle.intellij.SocleProjectService

/**
 * The embedded Surfer tab (the surfer-panel viewer): a second JCEF tab on the server's UI in Surfer-only
 * mode, driven by the design panel through a [SurferChannel].
 */
@Service(Service.Level.PROJECT)
class SurferTab(private val project: Project) : Disposable {
    private var file: SocleTabFile? = null
    private var browser: RelayBrowser? = null
    private var tabDisposable: Disposable? = null
    private var wantedUrl: String? = null
    private var lastTest: String? = null

    val channel = SurferChannel(
        toPanel = { m -> ApplicationManager.getApplication().invokeLater({ SoclePanel.getInstance(project).post(m) }, project.disposed) },
        toTab = { m -> browser?.router?.send(m) },
    )

    init {
        val bus = project.messageBus.connect(this)
        bus.subscribe(PanelMessageListener.TOPIC, PanelMessageListener { msg ->
            when {
                Logic.msgType(msg) == "socle-surfer-open" -> if (channel.openRequest(msg)) {
                    val test = Logic.surferTest(msg.get("traceTest")?.takeIf { it.isJsonPrimitive }?.asString,
                        SocleProjectService.getInstance(project).lastTest)
                    ApplicationManager.getApplication().invokeLater({ open(test) }, project.disposed)
                }
                Logic.route("panel", msg) == "surfer" -> channel.command(msg)
            }
        })
        bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileClosed(source: FileEditorManager, closed: VirtualFile) {
                if (closed === file) closeTab()
            }
        })
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(LafManagerListener.TOPIC,
            LafManagerListener { browser?.router?.setTheme(currentThemeKind()) })
    }

    /** Whether a Simulate gave the panel a trace, for Show waveforms. */
    val traced: Boolean get() = channel.traced

    /** Opens the tab beside the editor (or brings it forward) on the server's last URL, titled after `test` (null keeps it). */
    fun open(test: String?) {
        val url = SocleProjectService.getInstance(project).panelUrl ?: return
        if (test != null) lastTest = test
        val title = Logic.surferTitle(lastTest)
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val f = file
        if (f == null || !manager.isFileOpen(f)) {
            val created = SocleTabFile(SocleTabFile.Kind.SURFER, title)
            file = created
            if (manager.openFiles.isEmpty()) manager.openFile(created, false)
            else manager.splitters.openInRightSplit(created, false)
        } else {
            f.title = title
            manager.updateFilePresentation(f)
            manager.openFile(f, false)
        }
        point(url)
    }

    /** Points an open tab at the panel's server `url` (a restarted server has a new port); nothing without a tab. */
    fun retarget(url: String) {
        if (file != null) point(url)
    }

    private fun point(url: String) {
        val changed = wantedUrl?.let(Logic::relayUrl) != Logic.relayUrl(url)
        wantedUrl = url
        val b = browser ?: return
        // A new page reports afresh: its commands wait again.
        if (changed) channel.tabCreated()
        b.router.show(url)
    }

    /** Called by the editor provider when the tab is created. */
    fun browserFor(f: SocleTabFile): RelayBrowser {
        browser?.let { return it }
        val d = Disposer.newDisposable(this, "Socle Surfer tab")
        tabDisposable = d
        return RelayBrowser(d, currentThemeKind(), Logic::surferFrameUrl, channel::fromTab).also { b ->
            browser = b
            channel.tabCreated()
            wantedUrl?.let { b.router.show(it) }
        }
    }

    private fun closeTab() {
        tabDisposable?.let { Disposer.dispose(it) }
        tabDisposable = null
        browser = null
        file = null
        wantedUrl = null
        channel.tabClosed()
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): SurferTab = project.getService(SurferTab::class.java)
    }
}
