package socle.intellij.toolwindow

import com.google.gson.JsonParser
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import socle.intellij.SidebarModel
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService

class ToolWindowModelTest : BasePlatformTestCase() {
    private val service get() = SocleProjectService.getInstance(project)
    private val events get() = project.messageBus.syncPublisher(SocleEvents.TOPIC)

    private val state = SidebarModel.parseUiState(JsonParser.parseString("""
        {"type":"socle-state","path":["Top"],"selection":"alu",
         "design":{"top":"Top","modules":[
           {"name":"Top","scalaClass":"a.Top","kind":"module","children":[{"name":"alu","type":"Alu"}]},
           {"name":"Alu","scalaClass":"a.Alu","kind":"module","children":[]}]},
         "details":{"name":"alu","module":"Alu","scalaClass":"a.Alu","kind":"module","params":[],
                    "ports":[{"name":"io_a","dir":"in","type":"UInt<8>"}]}}
    """))!!

    /** The light project is shared with other tests: start and end with a clean session. */
    private fun reset() {
        service.updateSuites(emptyList())
        service.clearProblem()
        synchronized(service.tracker) { service.tracker.reset() }
    }

    override fun setUp() {
        super.setUp()
        reset()
    }

    override fun tearDown() {
        try { reset() } finally { super.tearDown() }
    }

    fun testSuitesBecomeSuiteRowsWithTheirTestsRunState() {
        val panel = TestsPanel(project, testRootDisposable)
        val file = Path.of(project.basePath ?: "/p").resolve("src/GcdSpec.scala").toUri().toString()
        service.updateSuites(listOf(SidebarModel.SuiteInfo("gcd.GcdSpec", file, listOf(SidebarModel.TestInfo("computes", 4)))))
        events.status("running", "gcd.GcdSpec", "computes", "")
        service.onStatus("running", "gcd.GcdSpec", "computes")
        val rows = panel.rows()
        assertEquals(1, rows.size)
        assertEquals("GcdSpec", rows[0].label)
        assertTrue(rows[0].description, rows[0].description.endsWith("GcdSpec.scala") && !rows[0].description.startsWith("file:"))
        assertEquals(listOf("computes"), rows[0].children.map { it.label })
        assertEquals("running…", rows[0].children[0].description)
        assertNull(panel.placeholder())
    }

    fun testServerProblemIsTheTestsPlaceholder() {
        val panel = TestsPanel(project, testRootDisposable)
        service.reportProblem("Socle needs Java 21 or newer")
        assertEquals("Socle needs Java 21 or newer", panel.placeholder())
    }

    fun testUiStateFillsHierarchyAndDetailsAndPanelClosedEmptiesThem() {
        val hier = HierarchyPanel(project, testRootDisposable)
        val details = DetailsPanel(project, testRootDisposable)
        events.uiState(state)
        assertEquals(listOf("Top"), hier.rows().map { it.label })
        assertEquals(listOf("alu"), hier.rows()[0].children.map { it.label })
        assertEquals(listOf("alu", "a.Alu", "Ports"), details.rows().map { it.label })
        events.panelClosed()
        assertEquals(emptyList<SidebarModel.Row>(), hier.rows())
        assertEquals(emptyList<SidebarModel.Row>(), details.rows())
    }

    private val classesState = SidebarModel.parseUiState(JsonParser.parseString("""
        {"type":"socle-state","path":["Top"],"selection":null,"details":null,
         "design":{"top":"Top","modules":[
           {"name":"Top","scalaClass":"a.Top","kind":"module","children":[{"name":"alu","type":"Alu"},{"name":"alu2","type":"Alu_1"}]},
           {"name":"Alu","scalaClass":"a.Alu","kind":"module","children":[]},
           {"name":"Alu_1","scalaClass":"a.Alu","kind":"module","children":[]}]},
         "classes":[{"scalaClass":"a.Alu","name":"Alu","library":false,"modules":2,"parameterizations":[
           {"label":"(w = 8)","unknown":false,"params":[{"name":"w","type":"Int","value":"8"}],"instances":[
             {"path":["Top","alu"],"label":"alu","module":"Alu"}]},
           {"label":"(w = 16)","unknown":false,"params":[{"name":"w","type":"Int","value":"16"}],"instances":[
             {"path":["Top","alu2"],"label":"alu2","module":"Alu_1"}]}]}]}
    """))!!

    fun testClassesComeWithTheDesignAndPanelClosedEmptiesThem() {
        val classes = ClassesPanel(project, testRootDisposable)
        events.uiState(state)
        assertEquals(emptyList<SidebarModel.Row>(), classes.rows()) // an older server: no class view
        events.uiState(classesState)
        assertEquals(listOf("Alu"), classes.rows().map { it.label })
        events.uiState(classesState.copy(designChanged = false, design = null, classes = emptyList(), path = listOf("Top", "alu")))
        assertEquals(listOf("Alu"), classes.rows().map { it.label })
        events.panelClosed()
        assertEquals(emptyList<SidebarModel.Row>(), classes.rows())
    }

    fun testClassRowsStartClosedAndOpenOnTheirParameterizations() {
        val classes = ClassesPanel(project, testRootDisposable)
        events.uiState(classesState)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val tree = classes.view.tree
        assertEquals(1, tree.rowCount)
        tree.expandRow(0)
        assertEquals(5, tree.rowCount) // Alu, (w = 8), alu, (w = 16), alu2
        assertEquals("alu2", classes.view.rowOf(tree.getPathForRow(4))?.label)
    }

    fun testStateWithoutDesignKeepsTheHierarchy() {
        val hier = HierarchyPanel(project, testRootDisposable)
        events.uiState(state)
        events.uiState(state.copy(designChanged = false, design = null, path = listOf("Top", "alu")))
        assertEquals(listOf("Top"), hier.rows().map { it.label })
    }
}
