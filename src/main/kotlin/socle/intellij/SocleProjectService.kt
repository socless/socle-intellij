package socle.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerManager
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.ExecuteCommandParams
import socle.intellij.lsp.SocleServerApi

/** One project's Socle session: the server's last panel URL, the tests, the run states and the server problem. */
@Service(Service.Level.PROJECT)
class SocleProjectService(private val project: Project) : Disposable {
    @Volatile var panelUrl: String? = null; private set
    @Volatile var lastTest: String? = null; private set
    @Volatile var suites: List<SidebarModel.SuiteInfo> = emptyList(); private set
    @Volatile var suitesLoaded: Boolean = false; private set
    @Volatile var problem: String? = null; private set
    /** The running server's client, for configuration pushes. */
    @Volatile var client: socle.intellij.lsp.SocleClient? = null
    val tracker = SidebarModel.RunTracker()
    private val restarts = RestartBudget(3, 3 * 60 * 1000L)

    /**
     * The server process stopped without being asked to: whether to start it again (at most 3 times in
     * 3 minutes, as in VS Code); past that the problem is reported. Starts the user asks for are not counted.
     */
    @Synchronized
    fun onUnexpectedStop(nowMs: Long): Boolean {
        val ok = restarts.allow(nowMs)
        if (!ok) reportProblem(STOPPED)
        return ok
    }

    init {
        project.messageBus.connect(this).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun testsChanged() { requestTests() }
            override fun panelClosed() { tracker.panelClosed() }
        })
    }

    private val events get() = project.messageBus.syncPublisher(SocleEvents.TOPIC)

    /** Records the panel the server asked for (before listeners hear of it). */
    fun onOpenPanel(url: String, test: String?) {
        panelUrl = url
        if (!test.isNullOrBlank()) lastTest = test
    }

    /** Records a run state from the server (before listeners hear of it). */
    fun onStatus(status: String, suite: String, test: String) = synchronized(tracker) { tracker.onStatus(status, suite, test) }

    /** Asks the server for the project's tests (starting it if needed); listeners get `suitesUpdated`. */
    fun requestTests() {
        server().thenCompose { it.tests() }.whenComplete { result, error ->
            if (error != null) { thisLogger().info("socle/tests failed", error); return@whenComplete }
            updateSuites(SidebarModel.parseTests(result))
        }
    }

    /** The project's tests are now `list`; listeners get `suitesUpdated`. */
    fun updateSuites(list: List<SidebarModel.SuiteInfo>) {
        suites = list
        suitesLoaded = true
        events.suitesUpdated(list)
    }

    /** `socle/cursor`: the class around (`uri`, 0-based `line`) becomes the panel's focus. */
    fun sendCursor(uri: String, line: Int) {
        server().thenAccept { it.cursor(CursorParams(uri, line)) }
    }

    /** Runs `socle.viewDesign` or `socle.simulate` on one test; the future fails when the server refused it. */
    fun executeCommand(command: String, uri: String, suite: String, test: String): CompletableFuture<Any?> {
        if (command == VIEW_DESIGN) synchronized(tracker) { tracker.viewStarted(SidebarModel.TestKey(suite, test)) }
        return server().thenCompose { it.workspaceService.executeCommand(ExecuteCommandParams(command, listOf(uri, suite, test))) }
            .whenComplete { _, error ->
                if (error != null && command == VIEW_DESIGN) synchronized(tracker) { tracker.viewNotStarted(SidebarModel.TestKey(suite, test)) }
            }
    }

    /** The server cannot run: tell the user once per problem and the listeners. */
    fun reportProblem(message: String) {
        if (problem == message) return
        problem = message
        NotificationGroupManager.getInstance().getNotificationGroup("Socle").createNotification(message, NotificationType.ERROR).notify(project)
        events.serverProblem(message)
    }

    fun clearProblem() {
        if (problem == null) return
        problem = null
        events.serverProblem(null)
    }

    /** A (re)started server knows of no run. */
    fun serverStarted() {
        synchronized(tracker) { tracker.reset() }
        clearProblem()
        requestTests()
    }

    private fun server(): CompletableFuture<SocleServerApi> =
        LanguageServerManager.getInstance(project).getLanguageServer(SERVER_ID)
            .thenCompose { item -> item?.initializedServer ?: CompletableFuture.failedFuture(IllegalStateException("Socle server not available")) }
            .thenApply { it as SocleServerApi }

    override fun dispose() {}

    data class CursorParams(val uri: String, val line: Int)

    companion object {
        const val SERVER_ID = "socle"
        const val STOPPED = "The Socle language server stopped 3 times in 3 minutes and will not be restarted. " +
            "Restart it from the Language Servers view to try again."
        const val VIEW_DESIGN = "socle.viewDesign"
        const val SIMULATE = "socle.simulate"

        /** A file's URI as the server writes it (`file:///...`); a non-local file keeps its own URL. */
        fun fileUri(file: com.intellij.openapi.vfs.VirtualFile): String =
            file.fileSystem.getNioPath(file)?.toUri()?.toString() ?: file.url

        fun getInstance(project: Project): SocleProjectService = project.getService(SocleProjectService::class.java)
    }
}
