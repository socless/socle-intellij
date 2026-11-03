package socle.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.MouseEvent
import java.nio.file.Path
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService
import socle.intellij.actions.TestAction

/** The Tests view: the project's ChiselSim suites and tests with their run state; View design / Simulate on a test. */
class TestsPanel(private val project: Project, parent: Disposable) {
    private val service get() = SocleProjectService.getInstance(project)
    private val view = RowTree()
    private val rootUri: String? = project.basePath?.let { Path.of(it).toUri().toString() }

    /** A test row's id to (file URI, suite, test, 0-based line). */
    private data class TestRef(val file: String, val suite: String, val test: String, val line: Int)
    @Volatile private var refs: Map<String, TestRef> = emptyMap()

    val component = SimpleToolWindowPanel(true, true).apply {
        val am = ActionManager.getInstance()
        val bar = DefaultActionGroup(listOfNotNull(am.getAction("socle.refreshTests"), am.getAction("socle.showPanel"), am.getAction("socle.openSettings")))
        toolbar = am.createActionToolbar("SocleTests", bar, true).also { it.targetComponent = view.tree }.component
        setContent(ScrollPaneFactory.createScrollPane(view.tree))
    }

    init {
        project.messageBus.connect(parent).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun suitesUpdated(suites: List<SidebarModel.SuiteInfo>) = refresh()
            override fun status(status: String, suite: String, test: String, message: String) = refresh()
            override fun serverProblem(message: String?) = refresh()
            override fun panelClosed() = refresh()
        })
        PopupHandler.installPopupMenu(view.tree, DefaultActionGroup().also { g ->
            g.add(object : com.intellij.openapi.actionSystem.ActionGroup() {
                override fun getChildren(e: com.intellij.openapi.actionSystem.AnActionEvent?) = selected()?.let { r ->
                    arrayOf(
                        TestAction(SocleProjectService.VIEW_DESIGN, "View Design", r.file, r.suite, r.test),
                        TestAction(SocleProjectService.SIMULATE, "Simulate", r.file, r.suite, r.test),
                    )
                } ?: emptyArray()
            })
        }, ActionPlaces.POPUP)
        object : com.intellij.ui.DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val r = selected() ?: return false
                val vf = VirtualFileManager.getInstance().findFileByUrl(r.file) ?: return false
                OpenFileDescriptor(project, vf, r.line, 0).navigate(true)
                return true
            }
        }.installOn(view.tree)
        refresh()
    }

    private fun selected(): TestRef? = view.rowOf(view.tree.selectionPath)?.let { refs[it.id] }

    /** The rows: one per suite, its tests under it. */
    fun rows(): List<SidebarModel.Row> {
        val tracker = service.tracker
        val next = mutableMapOf<String, TestRef>()
        val rows = service.suites.map { s ->
            SidebarModel.suiteRow(s, rootUri).copy(children = s.tests.map { t ->
                val st = synchronized(tracker) { tracker.state(SidebarModel.TestKey(s.suite, t.name)) }
                SidebarModel.testRow(s.suite, s.file, t, st).also { next[it.id] = TestRef(s.file, s.suite, t.name, t.line) }
            })
        }
        refs = next
        return rows
    }

    /** Why there are no rows, else null. */
    fun placeholder(): String? = SidebarModel.testsMessage(service.suitesLoaded, service.suites, service.problem)

    private fun refresh() = view.show(rows(), placeholder() ?: if (!service.suitesLoaded) "Open a Scala file to start the Socle server." else null)
}
