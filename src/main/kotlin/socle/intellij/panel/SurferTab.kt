package socle.intellij.panel

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** The embedded Surfer tab (Task 8). */
@Service(Service.Level.PROJECT)
class SurferTab(private val project: Project) {
    fun browserFor(f: SocleTabFile): RelayBrowser = throw IllegalStateException("The Surfer tab is not available yet")

    companion object {
        fun getInstance(project: Project): SurferTab = project.getService(SurferTab::class.java)
    }
}
