package socle.e2e

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService
import socle.intellij.panel.SoclePanel

/**
 * Drives one View design in a real IDE: opens SOCLE_E2E_SPEC (a test file relative to the project),
 * views its first test when the tests arrive, waits for the panel's UI state, navigates into the
 * first child instance, waits for that state, then exits. Each step is a line in SOCLE_E2E_OUT;
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
                } else if (firstState != null && state.path != firstState!!.path || state.selection != null) {
                    finish(true, "navigated to ${state.path} selection=${state.selection}")
                }
            }
        })
        ApplicationManager.getApplication().invokeLater {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(File(project.basePath, spec).path)
            if (vf == null) finish(false, "no file $spec") else FileEditorManager.getInstance(project).openFile(vf, true)
            line("opened $spec")
        }
    }
}
