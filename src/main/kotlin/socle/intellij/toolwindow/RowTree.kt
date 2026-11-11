package socle.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import socle.intellij.SidebarModel.Row

/** A Swing tree of [Row]s: label, grey description, tooltip and an IDE icon for the row's codicon id. */
class RowTree {
    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    val tree = Tree(model).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = Renderer()
    }

    /** Replaces the rows (on the EDT): expands every row, shows `placeholder` when there are none. */
    fun show(rows: List<Row>, placeholder: String?) {
        ApplicationManager.getApplication().invokeLater {
            root.removeAllChildren()
            rows.forEach { root.add(node(it)) }
            model.reload()
            tree.emptyText.text = placeholder ?: ""
            TreeUtil.expandAll(tree)
        }
    }

    /** The row of a tree path, or null. */
    fun rowOf(path: TreePath?): Row? = ((path?.lastPathComponent as? DefaultMutableTreeNode)?.userObject) as? Row

    /** Selects the row with `id` without scrolling away from it; true when found. */
    fun select(id: String): Boolean {
        val n = TreeUtil.findNode(root) { (it.userObject as? Row)?.id == id } ?: return false
        val p = TreePath(n.path)
        tree.selectionPath = p
        tree.scrollPathToVisible(p)
        return true
    }

    private fun node(r: Row): DefaultMutableTreeNode = DefaultMutableTreeNode(r).apply { r.children.forEach { add(node(it)) } }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            val r = (value as? DefaultMutableTreeNode)?.userObject as? Row ?: return
            icon = iconFor(r.icon)
            append(r.label)
            if (r.description.isNotEmpty()) append("  ${r.description}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            toolTipText = r.tooltip.ifEmpty { null }
        }
    }

    companion object {
        /** The IDE icon for a VS Code codicon id from [socle.intellij.SidebarModel]. */
        fun iconFor(codicon: String): Icon? = when (codicon) {
            "symbol-class" -> AllIcons.Nodes.Class
            "beaker" -> AllIcons.RunConfigurations.TestState.Run
            "eye" -> AllIcons.Actions.Show
            "sync~spin" -> AnimatedIcon.Default.INSTANCE
            "error" -> AllIcons.General.Error
            "circuit-board", "symbol-module" -> AllIcons.Nodes.Module
            "package" -> AllIcons.Nodes.Package
            "layers" -> AllIcons.Nodes.ModuleGroup
            "symbol-parameter" -> AllIcons.Nodes.Parameter
            "plug" -> AllIcons.Nodes.Plugin
            "arrow-right" -> AllIcons.General.ArrowRight
            "arrow-left" -> AllIcons.General.ArrowLeft
            "arrow-both" -> AllIcons.Actions.SwapPanels
            else -> null
        }
    }
}
