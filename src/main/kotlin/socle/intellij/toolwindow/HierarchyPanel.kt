package socle.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.jcef.JBCefApp
import javax.swing.JComponent
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.panel.SoclePanel

/** The Hierarchy view: the instance tree of the panel's design; a selected row drives the panel's schematic. */
class HierarchyPanel(private val project: Project, parent: Disposable) {
    private val view = RowTree()
    @Volatile private var design: SidebarModel.DesignInfo? = null
    @Volatile private var reveal: String? = null
    /** True while the view selects the panel's own location: that selection must not navigate back. */
    private var syncing = false

    val component: JComponent = ScrollPaneFactory.createScrollPane(view.tree)

    init {
        project.messageBus.connect(parent).subscribe(SocleEvents.TOPIC, object : SocleEvents {
            override fun uiState(state: SidebarModel.UiState) {
                if (state.designChanged) design = state.design
                reveal = design?.let { d -> SidebarModel.revealPath(d, state.path, state.selection)?.let(SidebarModel::hierId) }
                refresh()
            }
            override fun panelClosed() { design = null; reveal = null; refresh() }
        })
        view.tree.addTreeSelectionListener { e ->
            if (syncing) return@addTreeSelectionListener
            val d = design ?: return@addTreeSelectionListener
            val path = pathOf(view.rowOf(e.newLeadSelectionPath)) ?: return@addTreeSelectionListener
            val (to, select) = SidebarModel.navigateTarget(d, path) ?: return@addTreeSelectionListener
            SoclePanel.getInstance(project).navigate(SidebarModel.navigateMessage(to, select))
        }
        PopupHandler.installPopupMenu(view.tree, DefaultActionGroup(object : AnAction("Open Source") {
            override fun actionPerformed(e: AnActionEvent) = openSource()
        }), ActionPlaces.POPUP)
        refresh()
    }

    private fun pathOf(r: SidebarModel.Row?): List<String>? = r?.id?.removePrefix("hier:")?.split('/')

    private fun openSource() {
        val d = design ?: return
        val path = pathOf(view.rowOf(view.tree.selectionPath)) ?: return
        val src = SidebarModel.moduleSource(d, path) ?: return
        val file = SidebarModel.sourcePath(src.file, project.basePath) ?: return
        val vf = LocalFileSystem.getInstance().findFileByPath(file) ?: return
        OpenFileDescriptor(project, vf, maxOf(src.line - 1, 0), 0).navigate(true)
    }

    /** The rows: the top module, its instances under it. */
    fun rows(): List<SidebarModel.Row> {
        val d = design ?: return emptyList()
        fun build(path: List<String>): SidebarModel.Row? =
            SidebarModel.hierRow(d, path)?.copy(children = SidebarModel.hierChildren(d, path).mapNotNull(::build))
        return SidebarModel.hierRoots(d).mapNotNull(::build)
    }

    private fun refresh() {
        view.show(rows(), placeholder())
        val id = reveal ?: return
        ApplicationManager.getApplication().invokeLater {
            syncing = true
            try { view.select(id) } finally { syncing = false }
        }
    }

    companion object {
        /** The empty view's text: the panel must be open in the IDE for this view to follow it. */
        fun placeholder(): String =
            if (JBCefApp.isSupported()) "Use View design on a ChiselSim test."
            else "The design opens in your browser (no JCEF in this IDE), so this view stays empty."
    }
}
