package socle.intellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import socle.intellij.SocleProjectService
import socle.intellij.panel.SoclePanel
import socle.intellij.settings.SocleConfigurable

/** The Tools | Socle actions: they need a project. */
abstract class SocleProjectAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

/** Shows the design panel on the last design, else says why there is none. */
class ShowPanelAction : SocleProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (SoclePanel.getInstance(project).showLast()) return
        val message = SocleProjectService.getInstance(project).problem ?: NO_DESIGN
        NotificationGroupManager.getInstance().getNotificationGroup("Socle")
            .createNotification(message, NotificationType.INFORMATION).notify(project)
    }

    companion object {
        const val NO_DESIGN = "Socle: no design yet. Use View design on a ChiselSim test first."
    }
}

/** Asks the server for the project's tests again. */
class RefreshTestsAction : SocleProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        SocleProjectService.getInstance(e.project ?: return).requestTests()
    }
}

/** Opens Settings | Tools | Socle. */
class OpenSettingsAction : SocleProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SocleConfigurable::class.java)
    }
}
