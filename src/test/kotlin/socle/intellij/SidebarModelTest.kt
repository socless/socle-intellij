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
