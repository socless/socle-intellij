package socle.intellij.panel

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** Opens a [SocleTabFile] as its relay browser (the panel's or the Surfer tab's). */
class SocleTabEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is SocleTabFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val f = file as SocleTabFile
        val browser = when (f.kind) {
            SocleTabFile.Kind.PANEL -> SoclePanel.getInstance(project).browserFor(f)
            SocleTabFile.Kind.SURFER -> SurferTab.getInstance(project).browserFor(f)
        }
        return SocleTabEditor(f, browser)
    }

    override fun getEditorTypeId(): String = "socle-tab"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class SocleTabEditor(private val file: SocleTabFile, private val browser: RelayBrowser) : UserDataHolderBase(), FileEditor {
    override fun getComponent(): JComponent = browser.component
    override fun getPreferredFocusedComponent(): JComponent = browser.component
    override fun getName(): String = file.title
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
}
