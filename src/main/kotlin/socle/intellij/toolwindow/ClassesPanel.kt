package socle.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.jcef.JBCefApp
import javax.swing.JComponent
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.panel.SoclePanel

/** The Classes view: the design's modules by Scala class, then by constructor arguments, then their
 *  instances; a selected instance drives the panel's schematic as a Hierarchy row does. */
class ClassesPanel(private val project: Project, parent: Disposable) {
    val view = RowTree()
    @Volatile private var design: SidebarModel.DesignInfo? = null
    @Volatile private var classes: List<SidebarModel.ClassInfo> = emptyList()

    val component: JComponent = ScrollPaneFactory.createScrollPane(view.tree)

    init {
        project.messageBus.connect(parent).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            // The class view comes with the design only: a drill or a selection keeps it (and its open rows).
            override fun uiState(state: SidebarModel.UiState) {
                if (!state.designChanged) return
                design = state.design
                classes = state.classes
                refresh()
            }
            override fun panelClosed() { design = null; classes = emptyList(); refresh() }
        })
        view.tree.addTreeSelectionListener { e ->
            val path = view.rowOf(e.newLeadSelectionPath)?.path?.takeIf { it.isNotEmpty() } ?: return@addTreeSelectionListener
            val (to, select) = design?.let { SidebarModel.navigateTarget(it, path) } ?: (path to null)
            SoclePanel.getInstance(project).navigate(SidebarModel.navigateMessage(to, select))
        }
        refresh()
    }

    fun rows(): List<SidebarModel.Row> = SidebarModel.classRows(classes)

    private fun refresh() = view.show(rows(), placeholder(), expandAll = false)

    companion object {
        fun placeholder(): String =
            if (JBCefApp.isSupported()) "Classes appear here for a design opened from a test (View design)."
            else HierarchyPanel.placeholder()
    }
}
