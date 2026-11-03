package socle.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import javax.swing.JComponent
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents

/** The Details view: what is selected in the panel's schematic (name, class, parameters, ports). */
class DetailsPanel(project: Project, parent: Disposable) {
    private val view = RowTree()
    @Volatile private var details: SidebarModel.DetailsInfo? = null

    val component: JComponent = ScrollPaneFactory.createScrollPane(view.tree)

    init {
        project.messageBus.connect(parent).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun uiState(state: SidebarModel.UiState) { details = state.details; refresh() }
            override fun panelClosed() { details = null; refresh() }
        })
        refresh()
    }

    fun rows(): List<SidebarModel.Row> = details?.let(SidebarModel::detailRows) ?: emptyList()

    private fun refresh() = view.show(rows(), HierarchyPanel.placeholder())
}
