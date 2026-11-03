package socle.intellij.panel

import com.google.gson.JsonObject
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import socle.intellij.Logic
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService

/** The project's design panel: one editor tab, beside the code, on the server's live UI. */
@Service(Service.Level.PROJECT)
class SoclePanel(private val project: Project) : Disposable {
    private var file: SocleTabFile? = null
    private var browser: RelayBrowser? = null
    private var tabDisposable: Disposable? = null

    /** The URL to show: the browser may be created after [show] (editors open asynchronously), and loads it then. */
    private var wantedUrl: String? = null

    init {
        val bus = project.messageBus.connect(this)
        bus.subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun openPanel(url: String, test: String?) {
                ApplicationManager.getApplication().invokeLater({ show(url, test) }, project.disposed)
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

    /** Shows the panel on `url` (opening it beside the editor, or bringing it forward), titled after `test`. */
    fun show(url: String, test: String?) {
        val title = Logic.panelTitle(test ?: SocleProjectService.getInstance(project).lastTest)
        thisLogger().info("Socle panel: show $url (open=${browser != null})")
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val f = file
        if (f == null || !manager.isFileOpen(f)) {
            val created = SocleTabFile(SocleTabFile.Kind.PANEL, title)
            file = created
            if (manager.openFiles.isEmpty()) manager.openFile(created, false)
            else manager.splitters.openInRightSplit(created, false)
        } else {
            f.title = title
            manager.updateFilePresentation(f)
            manager.openFile(f, false)
        }
        wantedUrl = url
        browser?.router?.show(url)
        SurferTab.getInstance(project).retarget(url) // a restarted server: the Surfer tab follows it
    }

    /** Shows the panel on the server's last URL; false when there is none. */
    fun showLast(): Boolean {
        val url = SocleProjectService.getInstance(project).panelUrl ?: return false
        show(url, null)
        return true
    }

    /** The tab `f` shows this view (one IntelliJ may have reopened itself): closing it closes the view. */
    fun adopt(f: SocleTabFile) { file = f }

    /** Called by the editor provider when the tab is created. */
    fun browserFor(f: SocleTabFile): RelayBrowser {
        adopt(f)
        browser?.let { return it }
        val d = Disposer.newDisposable(this, "Socle panel tab")
        tabDisposable = d
        return RelayBrowser(d, currentThemeKind(), Logic::panelFrameUrl, ::onUiMessage).also { b ->
            browser = b
            // A tab IntelliJ reopened by itself has no wanted URL: the server's last one.
            (wantedUrl ?: SocleProjectService.getInstance(project).panelUrl)?.let { b.router.show(it) }
        }
    }

    private fun onUiMessage(msg: JsonObject) {
        thisLogger().debug("Socle panel: UI message ${Logic.msgType(msg)}")
        if (Logic.msgType(msg) == "socle-state") {
            SidebarModel.parseUiState(msg)?.let { project.messageBus.syncPublisher(SocleEvents.TOPIC).uiState(it) }
        } else {
            project.messageBus.syncPublisher(PanelMessageListener.TOPIC).message(msg)
        }
    }

    /** A `socle-navigate` for the UI (once it listens); brings the panel forward. */
    fun navigate(msg: JsonObject) {
        val f = file ?: return
        FileEditorManager.getInstance(project).openFile(f, false)
        browser?.router?.navigate(msg)
    }

    /** A Surfer channel message for the UI, if the panel is open. */
    fun post(msg: JsonObject) {
        browser?.router?.send(msg)
    }

    val isOpen: Boolean get() = browser != null

    private fun closeTab() {
        tabDisposable?.let { Disposer.dispose(it) }
        tabDisposable = null
        browser = null
        file = null
        project.messageBus.syncPublisher(SocleEvents.TOPIC).panelClosed()
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): SoclePanel = project.getService(SoclePanel::class.java)
    }
}

/** Messages from the panel's UI other than `socle-state` (the Surfer channel). */
fun interface PanelMessageListener {
    fun message(msg: JsonObject)

    companion object {
        @JvmField
        val TOPIC: Topic<PanelMessageListener> = Topic.create("Socle panel messages", PanelMessageListener::class.java)
    }
}
