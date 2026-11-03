package socle.intellij.actions

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.awt.RelativePoint
import javax.swing.Icon
import socle.intellij.SidebarModel
import socle.intellij.SocleProjectService

/**
 * A Socle icon on each ChiselSim test line the server listed (`socle/tests`), on the line's first token;
 * a click offers View design and Simulate on that test.
 */
class SocleLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = "Socle tests"

    override fun getIcon(): Icon = ICON

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null || element is PsiWhiteSpace) return null
        val project = element.project
        val suites = SocleProjectService.getInstance(project).suites
        if (suites.isEmpty()) return null
        val psiFile = element.containingFile ?: return null
        val vf = psiFile.originalFile.virtualFile ?: return null
        val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
        val start = element.textRange.startOffset
        val line = doc.getLineNumber(start)
        val uri = SocleProjectService.fileUri(vf)
        val (suite, test) = SidebarModel.testsInFile(suites, uri).firstOrNull { it.second.line == line } ?: return null
        val text = doc.charsSequence
        var i = doc.getLineStartOffset(line)
        while (i < start && text[i].isWhitespace()) i++
        if (i != start) return null
        val tip = "Socle: ${test.name}"
        val handler = GutterIconNavigationHandler<PsiElement> { e, _ ->
            val group = DefaultActionGroup(
                TestAction(SocleProjectService.VIEW_DESIGN, "View Design", uri, suite, test.name),
                TestAction(SocleProjectService.SIMULATE, "Simulate", uri, suite, test.name),
            )
            JBPopupFactory.getInstance().createActionGroupPopup(tip, group, DataManager.getInstance().getDataContext(e.component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false).show(RelativePoint(e))
        }
        return LineMarkerInfo(element, element.textRange, ICON, { tip }, handler, GutterIconRenderer.Alignment.LEFT, { tip })
    }

    companion object {
        val ICON: Icon = IconLoader.getIcon("/icons/socleGutter.svg", SocleLineMarkerProvider::class.java)
    }
}
