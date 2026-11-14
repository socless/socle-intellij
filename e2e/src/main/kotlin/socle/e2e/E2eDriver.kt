package socle.e2e

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.tree.TreeUtil
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService
import socle.intellij.panel.SoclePanel

/**
 * Drives one View design in a real IDE: opens SOCLE_E2E_SPEC (a test file relative to the project),
 * views its first test when the tests arrive, waits for the panel's UI state, navigates into the
 * first child instance, waits for that state, then (Socle 0.30.0 and later) logs the Classes rows,
 * selects a class instance in the tool window's Classes tab, waits for that state, then exits.
 * Each step is a line in SOCLE_E2E_OUT;
 * the last line is PASS or FAIL.
 */
class E2eDriver : ProjectActivity {
    override suspend fun execute(project: Project) {
        val out = System.getenv("SOCLE_E2E_OUT") ?: return
        val spec = System.getenv("SOCLE_E2E_SPEC") ?: return
        val log = File(out)
        fun line(s: String) = synchronized(log) { log.appendText(s + "\n") }
        fun finish(ok: Boolean, why: String) {
            line(if (ok) "PASS $why" else "FAIL $why")
            ApplicationManager.getApplication().invokeLater { ApplicationManager.getApplication().exit(true, true, false) }
        }
        line("start ${project.basePath}")
        val timer = Executors.newSingleThreadScheduledExecutor()
        timer.schedule({ finish(false, "timeout") }, 9, TimeUnit.MINUTES)

        // A fresh profile shows first-run dialogs (the new UI onboarding): close any modal dialog, logging it.
        timer.scheduleWithFixedDelay({
            ApplicationManager.getApplication().invokeLater({
                for (w in java.awt.Window.getWindows()) {
                    if (w is java.awt.Dialog && w.isModal && w.isVisible) {
                        line("closing dialog '${w.title}'")
                        val wrapper = com.intellij.openapi.ui.DialogWrapper.findInstance(w.focusOwner ?: (w as? javax.swing.JDialog)?.contentPane)
                        if (wrapper != null) wrapper.close(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE) else w.dispose()
                    }
                }
            }, com.intellij.openapi.application.ModalityState.any())
        }, 2, 2, TimeUnit.SECONDS)

        val service = SocleProjectService.getInstance(project)
        val fileUri = File(project.basePath, spec).toPath().toUri().toString()
        var viewed = false
        var firstState: SidebarModel.UiState? = null
        var classTarget: Pair<List<String>, String?>? = null
        project.messageBus.connect(service).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun suitesUpdated(suites: List<SidebarModel.SuiteInfo>) {
                line("suites ${suites.map { it.suite }}")
                val s = suites.firstOrNull { it.file == fileUri } ?: return
                if (viewed) return
                viewed = true
                line("view ${s.suite} / ${s.tests[0].name}")
                service.executeCommand(SocleProjectService.VIEW_DESIGN, s.file, s.suite, s.tests[0].name)
                    .whenComplete { _, e -> if (e != null) finish(false, "viewDesign refused: $e") }
            }
            override fun openPanel(url: String, test: String?) = line("openPanel $url $test")
            override fun status(status: String, suite: String, test: String, message: String) = line("status $status $suite $test $message")
            override fun serverProblem(message: String?) { if (message != null) finish(false, "server problem: $message") }
            override fun uiState(state: SidebarModel.UiState) {
                line("uiState path=${state.path} top=${state.design?.top} changed=${state.designChanged}")
                val d = state.design
                if (firstState == null && d != null) {
                    firstState = state
                    val child = SidebarModel.hierChildren(d, SidebarModel.hierRoots(d).first()).firstOrNull()
                    if (child == null) { finish(true, "design shown, no child to navigate to"); return }
                    line("navigate $child")
                    ApplicationManager.getApplication().invokeLater {
                        val (p, sel) = SidebarModel.navigateTarget(d, child)!!
                        SoclePanel.getInstance(project).navigate(SidebarModel.navigateMessage(p, sel))
                    }
                } else if (classTarget != null) {
                    val (p, sel) = classTarget!!
                    if (state.path == p && (sel == null || state.selection == sel))
                        finish(true, "Classes instance shown: path=${state.path} selection=${state.selection}")
                } else if (firstState != null && state.path != firstState!!.path || state.selection != null) {
                    line("navigated to ${state.path} selection=${state.selection}")
                    // The Classes view (Socle 0.30.0 and later): its rows, then one of its instances selected
                    // in the tool window's Classes tab, whose selection navigates the panel.
                    val fd = firstState!!.design!!
                    val rows = SidebarModel.classRows(firstState.classes)
                    fun dump(rs: List<SidebarModel.Row>, depth: Int): Unit = rs.forEach {
                        line("classes ${"  ".repeat(depth)}${it.label} (${it.description})${if (it.path.isEmpty()) "" else " -> ${it.path}"}")
                        dump(it.children, depth + 1)
                    }
                    dump(rows, 0)
                    fun all(rs: List<SidebarModel.Row>): List<SidebarModel.Row> = rs.flatMap { listOf(it) + all(it.children) }
                    if (rows.isEmpty()) { finish(true, "no class view from this server (older than Socle 0.30.0)"); return }
                    // An instance whose target is not where the schematic already is, so the move shows.
                    val (inst, target) = all(rows).filter { it.path.isNotEmpty() }
                        .map { it to (SidebarModel.navigateTarget(fd, it.path) ?: (it.path to null)) }
                        .lastOrNull { it.second != (state.path to state.selection) }
                        ?: run { finish(true, "every Classes instance is where the schematic is: Classes navigation not exercised"); return }
                    classTarget = target
                    ApplicationManager.getApplication().invokeLater {
                        val content = ToolWindowManager.getInstance(project).getToolWindow("Socle")?.contentManager?.findContent("Classes")
                        val tree = ((content?.component as? JScrollPane)?.viewport?.view as? JTree)
                            ?: run { finish(false, "no Classes tab in the Socle tool window"); return@invokeLater }
                        line("Classes tab: ${tree.rowCount} rows shown (classes closed)")
                        val node = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
                            (it.userObject as? SidebarModel.Row)?.path == inst.path
                        } ?: run { finish(false, "no Classes row for ${inst.path}"); return@invokeLater }
                        line("select in the Classes tab ${inst.label} -> ${inst.path}")
                        tree.selectionPath = TreePath(node.path)
                    }
                }
            }
        })
        ApplicationManager.getApplication().invokeLater {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(File(project.basePath, spec).path)
            if (vf == null) finish(false, "no file $spec") else FileEditorManager.getInstance(project).openFile(vf, true)
            line("opened $spec")
            // The tool window's tabs follow the panel from their creation: open it before the design comes.
            ToolWindowManager.getInstance(project).getToolWindow("Socle")?.show()
        }
    }
}
