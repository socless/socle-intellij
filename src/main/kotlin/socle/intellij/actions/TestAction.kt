package socle.intellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import socle.intellij.SocleProjectService

/** View design or Simulate on one test: `command` (`socle.viewDesign` | `socle.simulate`) with [uri, suite, test]. */
class TestAction(
    private val command: String,
    text: String,
    private val uri: String,
    private val suite: String,
    private val test: String,
) : AnAction(text) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val label = templatePresentation.text
        SocleProjectService.getInstance(project).executeCommand(command, uri, suite, test).whenComplete { _, error ->
            if (error != null) {
                val why = generateSequence(error) { it.cause }.last().message ?: error.toString()
                NotificationGroupManager.getInstance().getNotificationGroup("Socle")
                    .createNotification("Socle: $label could not start: $why", NotificationType.WARNING).notify(project)
            }
        }
    }
}
