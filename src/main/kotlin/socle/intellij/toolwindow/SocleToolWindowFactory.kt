package socle.intellij.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** The Socle tool window: Tests, Hierarchy and Details. */
class SocleToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val parent = toolWindow.disposable
        val cf = ContentFactory.getInstance()
        val cm = toolWindow.contentManager
        cm.addContent(cf.createContent(TestsPanel(project, parent).component, "Tests", false))
        cm.addContent(cf.createContent(HierarchyPanel(project, parent).component, "Hierarchy", false))
        cm.addContent(cf.createContent(DetailsPanel(project, parent).component, "Details", false))
    }
}
