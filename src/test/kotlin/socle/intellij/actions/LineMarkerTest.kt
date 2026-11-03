package socle.intellij.actions

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import socle.intellij.SidebarModel
import socle.intellij.SocleProjectService
import socle.intellij.lsp.SocleClientFeatures

class LineMarkerTest : BasePlatformTestCase() {
    private val source = (0 until 12).joinToString("\n") { i ->
        when (i) {
            0 -> "class GcdSpec {"
            4 -> "  it(\"computes\") {"
            9 -> "  it(\"resets\") {"
            11 -> "}"
            else -> "  val x$i = $i"
        }
    }

    /**
     * The 0-based lines the provider marks, over every element of the file. Not through highlighting:
     * that would also make LSP4IJ start the real server.
     */
    private fun socleLines(): List<Int> {
        val doc = myFixture.editor.document
        val provider = SocleLineMarkerProvider()
        return SyntaxTraverser.psiTraverser(myFixture.file).toList()
            .mapNotNull { provider.getLineMarkerInfo(it) }
            .map { doc.getLineNumber(it.startOffset) }.sorted()
    }

    fun testRegisteredForScala() {
        myFixture.configureByText("GcdSpec.scala", source)
        assertEquals("Scala", myFixture.file.language.id)
        assertTrue(LineMarkerProviders.getInstance().allForLanguage(myFixture.file.language).any { it is SocleLineMarkerProvider })
    }

    override fun tearDown() {
        try { SocleProjectService.getInstance(project).updateSuites(emptyList()) } finally { super.tearDown() }
    }

    fun testMarkersOnTheTestLines() {
        myFixture.configureByText("GcdSpec.scala", source)
        val uri = SocleProjectService.fileUri(myFixture.file.virtualFile)
        SocleProjectService.getInstance(project).updateSuites(listOf(
            SidebarModel.SuiteInfo("gcd.GcdSpec", uri, listOf(SidebarModel.TestInfo("computes", 4), SidebarModel.TestInfo("resets", 9))),
        ))
        assertEquals(listOf(4, 9), socleLines())
    }

    fun testNoMarkersWithoutSuites() {
        myFixture.configureByText("GcdSpec.scala", source)
        SocleProjectService.getInstance(project).updateSuites(emptyList())
        assertEquals(emptyList<Int>(), socleLines())
    }

    fun testNoSocleCodeLenses() {
        myFixture.configureByText("GcdSpec.scala", source)
        assertFalse(SocleClientFeatures().codeLensFeature.isEnabled(myFixture.file))
    }
}
