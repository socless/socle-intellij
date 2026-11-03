package socle.intellij

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Alarm
import socle.intellij.settings.SocleSettingsListener

/** Per project: the cursor in Scala files goes to the server (`socle/cursor`, debounced 300 ms), and setting changes too. */
class SocleStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = SocleProjectService.getInstance(project)
        // Services that listen for the server's events from their constructor must exist before the first event.
        socle.intellij.panel.SoclePanel.getInstance(project)
        socle.intellij.panel.SurferTab.getInstance(project)
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, service)
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.project != project) return
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                if (!file.isInLocalFileSystem || file.extension != "scala") return
                val uri = SocleProjectService.fileUri(file)
                val line = event.newPosition.line
                alarm.cancelAllRequests()
                alarm.addRequest({ service.sendCursor(uri, line) }, CURSOR_DEBOUNCE_MS)
            }
        }, service as Disposable)
        // The gutter icons come from the server's test list: redraw them when it changes.
        project.messageBus.connect(service).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun suitesUpdated(suites: List<SidebarModel.SuiteInfo>) {
                DaemonCodeAnalyzer.getInstance(project).restart("Socle tests changed")
            }
        })
        ApplicationManager.getApplication().messageBus.connect(service).subscribe(SocleSettingsListener.TOPIC,
            SocleSettingsListener { service.client?.triggerChangeConfiguration() })
    }

    companion object {
        const val CURSOR_DEBOUNCE_MS = 300
    }
}
