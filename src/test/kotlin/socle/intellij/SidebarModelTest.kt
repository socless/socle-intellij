package socle.intellij

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import socle.intellij.SidebarModel.RunState.Failed
import socle.intellij.SidebarModel.RunState.Idle
import socle.intellij.SidebarModel.RunState.Running
import socle.intellij.SidebarModel.RunState.Shown
import socle.intellij.SidebarModel.RunState.Simulating

// Ported from socle-vscode's SidebarModelTest: payload parsing, run states, tree rows.
class SidebarModelTest {
    private fun json(s: String): JsonElement = JsonParser.parseString(s)

    private val testsPayload = json(
        """{"suites":[
          {"suite":"deephier.DeepSocSpec","file":"file:///ws/example/test/src/deephier/DeepSocSpec.scala",
           "tests":[{"name":"DeepSoc elaborates and steps","line":7},{"name":"Cluster with 3 cores steps","line":21}]},
          {"suite":"gcd.GcdSpec","file":"file:///ws/example/test/src/gcd/GcdSpec.scala",
           "tests":[{"name":"GCD computes gcd(48, 18)","line":8}]}
        ]}""")

    private val state = json(
        """{"type":"socle-state",
         "design":{"top":"DeepSoc","modules":[
           {"name":"DeepSoc","scalaClass":"deephier.DeepSoc","kind":"internal","source":{"file":"/ws/DeepSoc.scala","line":162},
            "children":[{"name":"cluster0","type":"Cluster"},{"name":"pipe","type":"Queue4"}]},
           {"name":"Cluster","scalaClass":"deephier.Cluster","kind":"internal","source":{"file":"src/Cluster.scala","line":119},
            "children":[{"name":"core","type":"Core"}]},
           {"name":"Core","scalaClass":"","kind":"internal","source":null,"children":[]},
           {"name":"Queue4","scalaClass":"","kind":"internal","source":null,"children":[]}
         ]},
         "path":["DeepSoc","cluster0"],
         "selection":"core",
         "details":{"name":"core","module":"Core","scalaClass":"deephier.Core","kind":"internal",
           "params":[{"name":"width","type":"Int","value":"32"},{"name":"n","type":"","value":"4"}],
           "ports":[{"name":"clock","dir":"in","type":"Clock"},{"name":"io","dir":"out","type":"CoreIO"}]}
        }""")

    private val design get() = SidebarModel.parseUiState(state)!!.design!!

    // ── parseTests ──

    @Test
    fun parseTestsKeepsOrder() {
        val s = SidebarModel.parseTests(testsPayload)
        assertEquals(listOf("deephier.DeepSocSpec", "gcd.GcdSpec"), s.map { it.suite })
        assertEquals(
            SidebarModel.SuiteInfo("gcd.GcdSpec", "file:///ws/example/test/src/gcd/GcdSpec.scala",
                listOf(SidebarModel.TestInfo("GCD computes gcd(48, 18)", 8))), s[1])
    }

    @Test
    fun parseTestsSkipsMalformed() {
        val s = SidebarModel.parseTests(json("""{"suites":[{"suite":"a.A","file":"file:///a","tests":[{"name":"x","line":"1"},{"name":"y","line":2}]},
            {"suite":3,"file":"f","tests":[]},"junk",{"suite":"b.B","file":"file:///b","tests":"no"}]}"""))
        assertEquals(listOf(SidebarModel.SuiteInfo("a.A", "file:///a", listOf(SidebarModel.TestInfo("y", 2)))), s)
    }

    @Test
    fun parseTestsNothingUsable() {
        assertEquals(emptyList<Any>(), SidebarModel.parseTests(null))
        assertEquals(emptyList<Any>(), SidebarModel.parseTests(JsonNull.INSTANCE))
        assertEquals(emptyList<Any>(), SidebarModel.parseTests(json("""{"suites":7}""")))
    }

    @Test
    fun testsInFileFiltersByUri() {
        val s = SidebarModel.parseTests(testsPayload)
        assertEquals(listOf(7, 21), SidebarModel.testsInFile(s, "file:///ws/example/test/src/deephier/DeepSocSpec.scala").map { it.second.line })
        assertEquals("gcd.GcdSpec", SidebarModel.testsInFile(s, "file:///ws/example/test/src/gcd/GcdSpec.scala").single().first)
        assertEquals(emptyList<Any>(), SidebarModel.testsInFile(s, "file:///ws/Other.scala"))
    }

    @Test
    fun relativePath() {
        assertEquals("example/A.scala", SidebarModel.relativePath("file:///ws/example/A.scala", "file:///ws"))
        assertEquals("example/A.scala", SidebarModel.relativePath("file:///ws/example/A.scala", "file:///ws/"))
        assertEquals("/other/A.scala", SidebarModel.relativePath("file:///other/A.scala", "file:///ws"))
        assertEquals("/ws2/A.scala", SidebarModel.relativePath("file:///ws2/A.scala", "file:///ws"))
        assertEquals("/a b/A.scala", SidebarModel.relativePath("file:///a%20b/A.scala", null))
        assertEquals("/a+b/A.scala", SidebarModel.relativePath("file:///a+b/A.scala", null))
    }

    // ── RunTracker ──

    private val a = SidebarModel.TestKey("gcd.GcdSpec", "t1")
    private val b = SidebarModel.TestKey("deephier.DeepSocSpec", "t2")

    @Test
    fun runTrackerViewRunningThenShown() {
        val r = SidebarModel.RunTracker()
        assertEquals(Idle, r.state(a))
        r.onStatus("running", a.suite, a.test)
        assertEquals(Running, r.state(a)); assertEquals(Idle, r.state(b))
        r.onStatus("ok", a.suite, a.test)
        assertEquals(Shown, r.state(a))
        r.onStatus("running", b.suite, b.test)
        assertEquals(Shown, r.state(a)); assertEquals(Running, r.state(b))
        r.onStatus("ok", b.suite, b.test)
        assertEquals(Idle, r.state(a)); assertEquals(Shown, r.state(b))
    }

    @Test
    fun runTrackerFailedThenGood() {
        val r = SidebarModel.RunTracker()
        r.onStatus("ok", a.suite, a.test)
        r.onStatus("running", b.suite, b.test)
        r.onStatus("error", b.suite, b.test)
        assertEquals(Failed, r.state(b)); assertEquals(Shown, r.state(a))
        r.onStatus("ok", b.suite, b.test)
        assertEquals(Shown, r.state(b)); assertEquals(Idle, r.state(a))
    }

    @Test
    fun runTrackerSimulation() {
        val r = SidebarModel.RunTracker()
        r.onStatus("ok", a.suite, a.test)
        r.onStatus("simulating", b.suite, b.test)
        assertEquals(Simulating, r.state(b)); assertEquals(Shown, r.state(a))
        r.onStatus("ok", a.suite, a.test)
        assertEquals(Idle, r.state(b)); assertEquals(Shown, r.state(a))
    }

    @Test
    fun runTrackerViewStartedAndRefused() {
        val r = SidebarModel.RunTracker()
        r.viewStarted(a)
        assertEquals(Running, r.state(a))
        r.viewNotStarted(b)
        assertEquals(Running, r.state(a))
        r.viewNotStarted(a)
        assertEquals(Idle, r.state(a))
        r.viewStarted(a)
        r.onStatus("ok", a.suite, a.test)
        assertEquals(Shown, r.state(a))
    }

    @Test
    fun runTrackerPanelClosedAndReset() {
        val r = SidebarModel.RunTracker()
        r.onStatus("ok", a.suite, a.test)
        r.onStatus("error", b.suite, b.test)
        r.panelClosed()
        assertEquals(Idle, r.state(a)); assertEquals(Failed, r.state(b))
        r.onStatus("running", a.suite, a.test)
        r.reset()
        assertEquals(Idle, r.state(a)); assertEquals(Idle, r.state(b))
    }

    @Test
    fun runTrackerErrorWithoutSuite() {
        val r = SidebarModel.RunTracker()
        r.onStatus("error", "", "t1")
        assertEquals(Idle, r.state(SidebarModel.TestKey("", "t1")))
    }

    // ── rows ──

    @Test
    fun testAndSuiteRows() {
        val t = SidebarModel.TestInfo("GCD computes gcd(48, 18)", 8)
        fun row(st: SidebarModel.RunState) = SidebarModel.testRow("gcd.GcdSpec", "file:///ws/G.scala", t, st)
        assertEquals(t.name, row(Idle).label); assertEquals("", row(Idle).description); assertEquals("beaker", row(Idle).icon)
        assertEquals("test", row(Idle).contextValue)
        assertEquals("test:file:///ws/G.scala#gcd.GcdSpec#GCD computes gcd(48, 18)", row(Idle).id)
        assertEquals("shown", row(Shown).description); assertEquals("eye", row(Shown).icon)
        assertEquals("running…", row(Running).description); assertEquals("sync~spin", row(Running).icon)
        assertEquals("simulating…", row(Simulating).description)
        assertEquals("error", row(Failed).icon); assertEquals("failed", row(Failed).description)
        val s = SidebarModel.suiteRow(SidebarModel.SuiteInfo("gcd.GcdSpec", "file:///ws/G.scala", listOf(t)), "file:///ws")
        assertEquals("GcdSpec", s.label); assertEquals("G.scala", s.description); assertEquals("suite", s.contextValue); assertTrue(s.expandable)
        val s2 = SidebarModel.suiteRow(SidebarModel.SuiteInfo("gcd.GcdSpec", "file:///ws/other/G.scala", listOf(t)), "file:///ws")
        assertNotEquals(s.id, s2.id)
        assertNotEquals(row(Idle).id, SidebarModel.testRow("gcd.GcdSpec", "file:///ws/other/G.scala", t, Idle).id)
    }

    @Test
    fun testsMessage() {
        val one = listOf(SidebarModel.SuiteInfo("a.A", "file:///a", listOf(SidebarModel.TestInfo("x", 1))))
        assertNull(SidebarModel.testsMessage(false, emptyList(), null))
        assertEquals("No ChiselSim tests in this project.", SidebarModel.testsMessage(true, emptyList(), null))
        assertNull(SidebarModel.testsMessage(true, one, null))
        assertEquals("no java", SidebarModel.testsMessage(true, one, "no java"))
    }

    // ── parseUiState ──

    @Test
    fun parseUiStateFull() {
        val u = SidebarModel.parseUiState(state)!!
        assertEquals(listOf("DeepSoc", "cluster0"), u.path); assertEquals("core", u.selection)
        assertTrue(u.designChanged)
        val d = u.design!!
        assertEquals("DeepSoc", d.top)
        assertEquals(SidebarModel.SourceInfo("src/Cluster.scala", 119), d.modules["Cluster"]!!.source)
        assertNull(d.modules["Core"]!!.source)
        assertEquals(listOf("cluster0" to "Cluster", "pipe" to "Queue4"), d.modules["DeepSoc"]!!.children)
        val det = u.details!!
        assertEquals("core", det.name); assertEquals("Core", det.module); assertEquals("deephier.Core", det.scalaClass)
        assertEquals(listOf(Triple("width", "Int", "32"), Triple("n", "", "4")), det.params)
        assertEquals(listOf(Triple("clock", "in", "Clock"), Triple("io", "out", "CoreIO")), det.ports)
    }

    @Test
    fun parseUiStateNoDesignYet() {
        val u = SidebarModel.parseUiState(json("""{"type":"socle-state","design":null,"path":[],"selection":null,"details":null}"""))!!
        assertTrue(u.designChanged); assertNull(u.design); assertNull(u.details); assertEquals(emptyList<String>(), u.path)
    }

    @Test
    fun parseUiStateDesignUnchanged() {
        val u = SidebarModel.parseUiState(json("""{"type":"socle-state","path":["A"],"selection":null,"details":null}"""))!!
        assertFalse(u.designChanged); assertEquals(listOf("A"), u.path)
    }

    @Test
    fun parseUiStateRejects() {
        assertNull(SidebarModel.parseUiState(json("""{"type":"socle-theme","kind":"dark"}""")))
        assertNull(SidebarModel.parseUiState(json("""{"type":"socle-state","path":"x"}""")))
        assertNull(SidebarModel.parseUiState(json("""{"type":"socle-state","path":[1]}""")))
        assertNull(SidebarModel.parseUiState(json("""{"type":"socle-state","path":[],"selection":3}""")))
        assertNull(SidebarModel.parseUiState(json("""{"type":"socle-state","path":[],"design":{"top":1}}""")))
        assertNull(SidebarModel.parseUiState(null))
        assertNull(SidebarModel.parseUiState(json("\"socle-state\"")))
    }

    // ── hierarchy ──

    @Test
    fun hierRootsAndChildren() {
        val d = design
        assertEquals(listOf(listOf("DeepSoc")), SidebarModel.hierRoots(d))
        assertEquals(listOf(listOf("DeepSoc", "cluster0"), listOf("DeepSoc", "pipe")), SidebarModel.hierChildren(d, listOf("DeepSoc")))
        assertEquals(listOf(listOf("DeepSoc", "cluster0", "core")), SidebarModel.hierChildren(d, listOf("DeepSoc", "cluster0")))
        assertEquals(emptyList<Any>(), SidebarModel.hierChildren(d, listOf("DeepSoc", "pipe")))
        assertEquals(emptyList<Any>(), SidebarModel.hierChildren(d, listOf("DeepSoc", "nope")))
    }

    @Test
    fun hierRows() {
        val d = design
        val top = SidebarModel.hierRow(d, listOf("DeepSoc"))!!
        assertEquals("DeepSoc", top.label); assertEquals("DeepSoc", top.description); assertEquals("hier:DeepSoc", top.id); assertTrue(top.expandable)
        val c = SidebarModel.hierRow(d, listOf("DeepSoc", "cluster0"))!!
        assertEquals("cluster0", c.label); assertEquals("Cluster", c.description); assertEquals("deephier.Cluster (module Cluster)", c.tooltip)
        val leaf = SidebarModel.hierRow(d, listOf("DeepSoc", "pipe"))!!
        assertEquals("Queue4", leaf.description); assertFalse(leaf.expandable)
        assertNull(SidebarModel.hierRow(d, listOf("DeepSoc", "nope")))
    }

    @Test
    fun navigateTarget() {
        val d = design
        assertEquals(listOf("DeepSoc", "cluster0") to null, SidebarModel.navigateTarget(d, listOf("DeepSoc", "cluster0")))
        assertEquals(listOf("DeepSoc") to "pipe", SidebarModel.navigateTarget(d, listOf("DeepSoc", "pipe")))
        assertEquals(listOf("DeepSoc", "cluster0") to "core", SidebarModel.navigateTarget(d, listOf("DeepSoc", "cluster0", "core")))
        assertEquals(listOf("DeepSoc") to null, SidebarModel.navigateTarget(d, listOf("DeepSoc")))
        assertNull(SidebarModel.navigateTarget(d, listOf("DeepSoc", "nope")))
    }

    @Test
    fun moduleSource() {
        val d = design
        assertEquals(SidebarModel.SourceInfo("src/Cluster.scala", 119), SidebarModel.moduleSource(d, listOf("DeepSoc", "cluster0")))
        assertNull(SidebarModel.moduleSource(d, listOf("DeepSoc", "pipe")))
    }

    @Test
    fun revealPath() {
        val d = design
        assertEquals(listOf("DeepSoc", "cluster0"), SidebarModel.revealPath(d, listOf("DeepSoc", "cluster0")))
        assertNull(SidebarModel.revealPath(d, listOf("Cluster")))
        assertNull(SidebarModel.revealPath(d, emptyList()))
        assertEquals(listOf("DeepSoc", "pipe"), SidebarModel.revealPath(d, listOf("DeepSoc"), "pipe"))
        assertEquals(listOf("DeepSoc"), SidebarModel.revealPath(d, listOf("DeepSoc"), "io.out"))
    }

    // ── details ──

    @Test
    fun detailRows() {
        val det = SidebarModel.parseUiState(state)!!.details!!
        val rows = SidebarModel.detailRows(det)
        assertEquals(listOf("core", "deephier.Core", "Parameters", "Ports"), rows.map { it.label })
        assertEquals("Core", rows[0].description); assertEquals("Scala class", rows[1].description)
        assertEquals(listOf("width: Int = 32", "n = 4"), rows[2].children.map { it.label })
        assertEquals(listOf("clock" to "in Clock", "io" to "out CoreIO"), rows[3].children.map { it.label to it.description })
        assertEquals("2", rows[3].description); assertEquals(rows.size, rows.map { it.id }.distinct().size)
        val bare = SidebarModel.detailRows(det.copy(scalaClass = "", params = emptyList()))
        assertEquals(listOf("core", "Ports"), bare.map { it.label })
    }

    // ── fold groups ──

    // Cluster folds cores_0..cores_2 (group "cores"); `ctl` is not a copy.
    private val folded = json(
        """{"type":"socle-state",
          | "design":{"top":"Top","modules":[
          |   {"name":"Top","scalaClass":"x.Top","kind":"internal","source":null,"children":[{"name":"cl","type":"Cluster"}]},
          |   {"name":"Cluster","scalaClass":"x.Cluster","kind":"internal","source":null,
          |    "children":[{"name":"cores_0","type":"Core","foldGroup":"cores"},{"name":"ctl","type":"Ctl"},
          |                {"name":"cores_1","type":"Core_1","foldGroup":"cores"},{"name":"cores_2","type":"Core_2","foldGroup":"cores"}]},
          |   {"name":"Core","scalaClass":"x.Core","kind":"internal","source":{"file":"src/Core.scala","line":3},"children":[]},
          |   {"name":"Core_1","scalaClass":"x.Core","kind":"internal","source":null,"children":[]},
          |   {"name":"Core_2","scalaClass":"x.Core","kind":"internal","source":null,"children":[]},
          |   {"name":"Ctl","scalaClass":"x.Ctl","kind":"internal","source":null,"children":[]}
          | ]},
          | "path":["Top","cl"], "selection":"cores",
          | "details":{"name":"cores","module":"Core","scalaClass":"x.Core","kind":"internal","params":[],"ports":[],"copies":3}
          |}""".trimMargin())
    private val cl = listOf("Top", "cl")
    private val g = cl + "#cores"

    @Test
    fun foldGroupIsOneNodeWithItsCopiesUnderIt() {
        val d = SidebarModel.parseUiState(folded)!!.design!!
        assertEquals(mapOf("cores_0" to "cores", "cores_1" to "cores", "cores_2" to "cores"), d.modules["Cluster"]!!.groups)
        assertEquals(listOf(g, cl + "ctl"), SidebarModel.hierChildren(d, cl))
        assertEquals(listOf(cl + "cores_0", cl + "cores_1", cl + "cores_2"), SidebarModel.hierChildren(d, g))
    }

    @Test
    fun foldGroupRow() {
        val d = SidebarModel.parseUiState(folded)!!.design!!
        val r = SidebarModel.hierRow(d, g)!!
        assertEquals("cores × 3", r.label); assertEquals("Core", r.description); assertEquals("group", r.contextValue)
        assertEquals("hier:Top/cl/#cores", r.id); assertTrue(r.expandable); assertTrue(r.tooltip.contains("3 generator copies of x.Core"))
        assertNull(SidebarModel.hierRow(d, cl + "#nope"))
    }

    @Test
    fun foldGroupNavigationSourceAndReveal() {
        val d = SidebarModel.parseUiState(folded)!!.design!!
        assertEquals(cl to "cores", SidebarModel.navigateTarget(d, g))
        assertEquals(cl to "cores_1", SidebarModel.navigateTarget(d, cl + "cores_1"))
        assertEquals(SidebarModel.SourceInfo("src/Core.scala", 3), SidebarModel.moduleSource(d, g))
        assertEquals(g, SidebarModel.revealPath(d, cl, "cores"))
        assertEquals(cl + "cores_2", SidebarModel.revealPath(d, cl, "cores_2"))
    }

    @Test
    fun foldGroupDetailsGiveTheCopies() {
        val rows = SidebarModel.detailRows(SidebarModel.parseUiState(folded)!!.details!!)
        assertEquals(listOf("cores", "3 generator copies", "x.Core", "Ports"), rows.map { it.label })
        assertTrue(SidebarModel.detailRows(SidebarModel.parseUiState(state)!!.details!!).none { it.id == "detail:copies" })
    }

    @Test
    fun olderServerWithoutFoldGroupsListsEveryCopy() {
        val d = SidebarModel.parseUiState(state)!!.design!!
        assertTrue(d.modules.values.all { it.groups.isEmpty() })
        assertEquals(listOf(listOf("DeepSoc", "cluster0"), listOf("DeepSoc", "pipe")), SidebarModel.hierChildren(d, listOf("DeepSoc")))
    }

    // ── classes ──

    // Cluster: one parameterization; Core: only without parameters; Lane: two that could not be
    // compared (the same label); then a library class.
    private val withClasses = json(
        """{"type":"socle-state","path":["DeepSoc"],"selection":null,"details":null,
          | "design":{"top":"DeepSoc","modules":[{"name":"DeepSoc","children":[]}]},
          | "classes":[
          |  {"scalaClass":"deephier.Cluster","name":"Cluster","library":false,"modules":2,"parameterizations":[
          |    {"label":"(nCores = 2)","unknown":false,"params":[{"name":"nCores","type":"Int","value":"2"}],"instances":[
          |      {"path":["DeepSoc","cluster0"],"label":"cluster0","module":"Cluster"},
          |      {"path":["DeepSoc","cluster1"],"label":"cluster1","module":"Cluster_1"}]}]},
          |  {"scalaClass":"deephier.Core","name":"Core","library":false,"modules":2,"parameterizations":[
          |    {"label":"(no parameters)","unknown":false,"params":[],"instances":[
          |      {"path":["DeepSoc","cluster0","cores_0"],"label":"cluster0/cores_0","module":"Core"},
          |      {"path":["DeepSoc","cluster0","cores_1"],"label":"cluster0/cores_1","module":"Core_1"}]}]},
          |  {"scalaClass":"x.Lane","name":"Lane","library":false,"modules":2,"parameterizations":[
          |    {"label":"(f = <unreadable>)","unknown":true,"params":[{"name":"f","type":"","value":"<unreadable>"}],
          |     "instances":[{"path":["DeepSoc","l0"],"label":"l0","module":"Lane"}]},
          |    {"label":"(f = <unreadable>)","unknown":true,"params":[{"name":"f","type":"","value":"<unreadable>"}],
          |     "instances":[{"path":["DeepSoc","l1"],"label":"l1","module":"Lane_1"}]}]},
          |  {"scalaClass":"chisel3.util.Queue","name":"Queue","library":true,"modules":1,"parameterizations":[
          |    {"label":"(entries = 4)","unknown":false,"params":[{"name":"entries","type":"Int","value":"4"}],
          |     "instances":[{"path":["DeepSoc","pipe"],"label":"pipe","module":"Queue4"}]}]}
          | ]}""".trimMargin())
    private val classRows get() = SidebarModel.classRows(SidebarModel.parseUiState(withClasses)!!.classes)
    private fun all(rs: List<SidebarModel.Row>): List<SidebarModel.Row> = rs.flatMap { listOf(it) + all(it.children) }

    @Test
    fun classRowsAsDelivered() {
        val rows = classRows
        assertEquals(listOf("Cluster", "Core", "Lane", "Queue"), rows.map { it.label })
        assertEquals(listOf("2", "2", "2", "1"), rows.map { it.description })
        assertEquals("deephier.Cluster", rows[0].tooltip)
        assertTrue(rows.all { it.expandable && !it.expanded })
        assertEquals(listOf("symbol-class", "symbol-class", "symbol-class", "package"), rows.map { it.icon })
    }

    @Test
    fun classParameterizationIsOpenWithItsInstances() {
        val p = classRows[0].children.single()
        assertEquals("(nCores = 2)", p.label); assertEquals("2", p.description); assertTrue(p.expandable && p.expanded)
        assertEquals("modules built with the same constructor arguments", p.tooltip)
        assertEquals(listOf("cluster0", "cluster1"), p.children.map { it.label })
        val i = p.children[1]
        assertEquals(listOf("DeepSoc", "cluster1"), i.path); assertEquals("module Cluster_1\nDeepSoc/cluster1", i.tooltip)
        assertFalse(i.expandable)
    }

    @Test
    fun classWithoutParametersListsItsInstancesDirectly() {
        val core = classRows[1]
        assertEquals(listOf("cluster0/cores_0", "cluster0/cores_1"), core.children.map { it.label })
        assertEquals(listOf("cores_0", "cores_1"), core.children.map { it.path.last() })
    }

    @Test
    fun classParameterizationNotComparedIsMarked() {
        val lane = classRows[2].children
        assertEquals(listOf("(f = <unreadable>) ?", "(f = <unreadable>) ?"), lane.map { it.label })
        assertTrue(lane.all { it.tooltip == SidebarModel.UNKNOWN_PARAMS }); assertEquals(listOf("1", "1"), lane.map { it.description })
    }

    @Test
    fun classRowIdsAreDistinctAndOnlyInstancesNavigate() {
        val ids = all(classRows).map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(listOf("cluster0", "cluster1", "cluster0/cores_0", "cluster0/cores_1", "l0", "l1", "pipe"),
            all(classRows).filter { it.path.isNotEmpty() }.map { it.label })
    }

    @Test
    fun olderServerOrNoDesignGivesNoClassView() {
        assertEquals(emptyList<Any>(), SidebarModel.parseUiState(state)!!.classes)
        assertEquals(emptyList<Any>(), SidebarModel.classRows(emptyList()))
        assertEquals(emptyList<Any>(),
            SidebarModel.parseUiState(json("""{"type":"socle-state","design":null,"path":[],"selection":null,"details":null}"""))!!.classes)
    }

    @Test
    fun malformedClassEntriesAreSkippedAndTheStateKept() {
        val u = SidebarModel.parseUiState(json(
            """{"type":"socle-state","path":["T"],"selection":null,"details":null,
              | "design":{"top":"T","modules":[{"name":"T","children":[]}]},
              | "classes":[7,{"name":"NoClass"},{"scalaClass":"a.A","library":"true","parameterizations":[
              |   3,{"label":"(no parameters)","unknown":"yes","params":[],"instances":[{"path":[],"label":"x"},{"path":["T",1],"label":"y"},
              |     {"path":["T","ok"],"label":"ok"}]}]}]}""".trimMargin()))!!
        assertEquals("T", u.design!!.top); assertEquals(listOf("T"), u.path)
        assertEquals(listOf(SidebarModel.ClassInfo("a.A", "A", false, 0,
            listOf(SidebarModel.ClassParams("(no parameters)", false, emptyList(),
                listOf(SidebarModel.ClassInstance(listOf("T", "ok"), "ok", "")))))), u.classes)
        assertEquals(emptyList<Any>(), SidebarModel.parseUiState(json(
            """{"type":"socle-state","path":[],"selection":null,"details":null,"design":{"top":"T","modules":[]},"classes":"x"}"""))!!.classes)
    }

    // ── editor side ──

    @Test
    fun sourcePath() {
        assertEquals("/abs/A.scala", SidebarModel.sourcePath("/abs/A.scala", "/ws"))
        assertEquals("C:\\x\\A.scala", SidebarModel.sourcePath("C:\\x\\A.scala", "/ws"))
        assertEquals("/ws/src/A.scala", SidebarModel.sourcePath("src/A.scala", "/ws"))
        assertNull(SidebarModel.sourcePath("src/A.scala", null))
    }

    @Test
    fun navigateMessage() {
        val m = SidebarModel.navigateMessage(listOf("DeepSoc", "cluster0"), null)
        assertEquals("socle-navigate", m.get("type").asString)
        assertEquals(listOf("DeepSoc", "cluster0"), m.getAsJsonArray("path").map { it.asString })
        assertTrue(m.get("select").isJsonNull)
        assertEquals("pipe", SidebarModel.navigateMessage(listOf("DeepSoc"), "pipe").get("select").asString)
    }
}
