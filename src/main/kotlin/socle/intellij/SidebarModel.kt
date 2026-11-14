package socle.intellij

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * The tool window's pure model, ported from socle-vscode's `SidebarModel`: the `socle/tests` and
 * `socle-state` payloads, per-test run states and the rows of the four trees (Tests, Hierarchy,
 * Classes, Details). Payload reading is tolerant: a malformed part is skipped, never thrown.
 */
object SidebarModel {
    private fun obj(v: JsonElement?): JsonObject? = v as? JsonObject
    private fun str(v: JsonElement?): String? = (v as? JsonPrimitive)?.takeIf { it.isString }?.asString
    private fun int(v: JsonElement?): Int? = (v as? JsonPrimitive)?.takeIf { it.isNumber }?.asDouble
        ?.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toInt()
    private fun arr(v: JsonElement?): JsonArray? = v as? JsonArray
    private fun isNull(v: JsonElement?): Boolean = v == null || v is JsonNull

    // ── Tests ──

    data class TestInfo(val name: String, val line: Int)
    data class SuiteInfo(val suite: String, val file: String, val tests: List<TestInfo>)

    /** The `socle/tests` result: `{suites: [{suite, file, tests: [{name, line}]}]}`. */
    fun parseTests(result: JsonElement?): List<SuiteInfo> =
        arr(obj(result)?.get("suites"))?.mapNotNull { s ->
            val o = obj(s) ?: return@mapNotNull null
            val suite = str(o["suite"]) ?: return@mapNotNull null
            val file = str(o["file"]) ?: return@mapNotNull null
            val ts = arr(o["tests"]) ?: return@mapNotNull null
            val tests = ts.mapNotNull { t ->
                val x = obj(t) ?: return@mapNotNull null
                val n = str(x["name"]) ?: return@mapNotNull null
                val l = int(x["line"]) ?: return@mapNotNull null
                TestInfo(n, l)
            }
            if (tests.isEmpty()) null else SuiteInfo(suite, file, tests)
        } ?: emptyList()

    /** The tests of the file `fileUri`, each with its suite (for the gutter). */
    fun testsInFile(suites: List<SuiteInfo>, fileUri: String): List<Pair<String, TestInfo>> =
        suites.filter { it.file == fileUri }.flatMap { s -> s.tests.map { s.suite to it } }

    /** `fileUri` relative to the project folder `rootUri`, else its decoded path. */
    fun relativePath(fileUri: String, rootUri: String?): String {
        val root = rootUri?.let { if (it.endsWith("/")) it else "$it/" }
        return if (root != null && fileUri.startsWith(root)) decode(fileUri.substring(root.length))
        else decode(fileUri.removePrefix("file://"))
    }

    private fun decode(s: String): String =
        try { URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8) } catch (_: IllegalArgumentException) { s }

    data class TestKey(val suite: String, val test: String)

    enum class RunState { Idle, Shown, Running, Simulating, Failed }

    /** Per-test run state from the server's `socle/status` and the plugin's own View design commands. */
    class RunTracker {
        private var shown: TestKey? = null
        private var running: TestKey? = null
        private var simulating: TestKey? = null
        private val failed = mutableSetOf<TestKey>()

        fun onStatus(status: String, suite: String, test: String) {
            val key = if (suite.isNotEmpty() && test.isNotEmpty()) TestKey(suite, test) else null
            when (status) {
                "running" -> { running = key; simulating = null }
                "simulating" -> { simulating = key; running = null }
                "ok" -> { running = null; simulating = null; key?.let { shown = it; failed -= it } }
                "error" -> { running = null; simulating = null; key?.let { failed += it } }
                else -> { running = null; simulating = null }
            }
        }

        /** A view started from the Tests view: running until the server says otherwise. */
        fun viewStarted(key: TestKey) { running = key }

        /** The View design command for `key` was refused. */
        fun viewNotStarted(key: TestKey) { if (running == key) running = null }

        fun panelClosed() { shown = null }

        fun reset() { shown = null; running = null; simulating = null; failed.clear() }

        fun state(key: TestKey): RunState = when {
            running == key -> RunState.Running
            simulating == key -> RunState.Simulating
            key in failed -> RunState.Failed
            shown == key -> RunState.Shown
            else -> RunState.Idle
        }
    }

    /** One tree row, independent of Swing: `icon` is a VS Code codicon id the tool window maps to an IDE icon.
     *  `expanded`: an expandable row that opens with its parent (Classes); `path`: the instance it shows (Classes). */
    data class Row(
        val id: String,
        val label: String,
        val description: String = "",
        val tooltip: String = "",
        val icon: String = "",
        val contextValue: String = "",
        val expandable: Boolean = false,
        val children: List<Row> = emptyList(),
        val expanded: Boolean = false,
        val path: List<String> = emptyList(),
    )

    fun suiteRow(s: SuiteInfo, rootUri: String?): Row {
        val rel = relativePath(s.file, rootUri)
        return Row("suite:${s.file}#${s.suite}", s.suite.substringAfterLast('.'), rel, "${s.suite}\n$rel", "symbol-class", "suite", expandable = true)
    }

    fun testRow(suite: String, file: String, t: TestInfo, st: RunState): Row {
        val (icon, desc) = when (st) {
            RunState.Idle -> "beaker" to ""
            RunState.Shown -> "eye" to "shown"
            RunState.Running -> "sync~spin" to "running…"
            RunState.Simulating -> "sync~spin" to "simulating…"
            RunState.Failed -> "error" to "failed"
        }
        return Row("test:$file#$suite#${t.name}", t.name, desc, "$suite: ${t.name}", icon, "test")
    }

    /** The Tests view's message: why it is empty, else null. */
    fun testsMessage(loaded: Boolean, suites: List<SuiteInfo>, problem: String?): String? =
        problem ?: if (loaded && suites.isEmpty()) "No ChiselSim tests in this project." else null

    // ── socle-state (from the UI in the panel) ──

    data class SourceInfo(val file: String, val line: Int)
    /** `groups`: a generator copy's fold group, by child name (Socle 0.27.0 and later send it). */
    data class ModuleInfo(val name: String, val scalaClass: String, val kind: String, val source: SourceInfo?,
                          val children: List<Pair<String, String>>, val groups: Map<String, String> = emptyMap())
    data class DesignInfo(val top: String, val modules: Map<String, ModuleInfo>)
    /** `copies`: the generator copies a folded box stands for (0 for any other instance). */
    data class DetailsInfo(val name: String, val module: String, val scalaClass: String, val kind: String,
                           val params: List<Triple<String, String, String>>, val ports: List<Triple<String, String, String>>,
                           val copies: Int = 0)

    /** The class view (Socle 0.30.0 and later send it with the design): a module class, its
     *  parameterizations (the modules built with the same arguments), their instances. */
    data class ClassInstance(val path: List<String>, val label: String, val module: String)
    data class ClassParams(val label: String, val unknown: Boolean, val params: List<Triple<String, String, String>>,
                           val instances: List<ClassInstance>)
    data class ClassInfo(val scalaClass: String, val name: String, val library: Boolean, val modules: Int,
                         val parameterizations: List<ClassParams>)

    /** `designChanged` false: the message left the design as it was (the UI sends it only when it changes).
     *  `classes` comes with the design: meaningful only when `designChanged` (empty from an older server). */
    data class UiState(val designChanged: Boolean, val design: DesignInfo?, val path: List<String>,
                       val selection: String?, val details: DetailsInfo?, val classes: List<ClassInfo> = emptyList())

    private fun parseModule(v: JsonElement): ModuleInfo? {
        val o = obj(v) ?: return null
        val name = str(o["name"]) ?: return null
        val kids = arr(o["children"]) ?: return null
        val source = obj(o["source"])?.let { s -> str(s["file"])?.let { f -> int(s["line"])?.let { SourceInfo(f, it) } } }
        return ModuleInfo(name, str(o["scalaClass"]) ?: "", str(o["kind"]) ?: "", source,
            kids.mapNotNull { c -> obj(c)?.let { x -> str(x["name"])?.let { n -> str(x["type"])?.let { n to it } } } },
            kids.mapNotNull { c ->
                obj(c)?.let { x -> str(x["name"])?.let { n -> str(x["foldGroup"])?.takeIf { it.isNotEmpty() }?.let { n to it } } }
            }.toMap())
    }

    private fun triples(v: JsonElement?, a: String, b: String, c: String): List<Triple<String, String, String>> =
        arr(v)?.mapNotNull { e ->
            val x = obj(e) ?: return@mapNotNull null
            val p = str(x[a]) ?: return@mapNotNull null
            val q = str(x[b]) ?: return@mapNotNull null
            val r = str(x[c]) ?: return@mapNotNull null
            Triple(p, q, r)
        } ?: emptyList()

    /** A `{type: "socle-state", design?, path, selection, details}` message, or null when it is not a valid one. */
    fun parseUiState(msg: JsonElement?): UiState? {
        val o = obj(msg) ?: return null
        if (str(o["type"]) != "socle-state") return null
        val pathArr = arr(o["path"]) ?: return null
        val path = pathArr.map { str(it) ?: return null }
        val selection = if (isNull(o["selection"])) null else str(o["selection"]) ?: return null
        val designChanged = o.has("design")
        val design = if (!designChanged || isNull(o["design"])) null else {
            val d = obj(o["design"]) ?: return null
            val top = str(d["top"]) ?: return null
            val mods = arr(d["modules"]) ?: return null
            DesignInfo(top, mods.mapNotNull { parseModule(it) }.associateBy { it.name })
        }
        val details = if (isNull(o["details"])) null else {
            val x = obj(o["details"]) ?: return null
            val n = str(x["name"]) ?: return null
            val m = str(x["module"]) ?: return null
            DetailsInfo(n, m, str(x["scalaClass"]) ?: "", str(x["kind"]) ?: "",
                triples(x["params"], "name", "type", "value"), triples(x["ports"], "name", "dir", "type"),
                int(x["copies"]) ?: 0)
        }
        val classes = if (designChanged) arr(o["classes"])?.mapNotNull(::parseClass) ?: emptyList() else emptyList()
        return UiState(designChanged, design, path, selection, details, classes)
    }

    private fun bool(v: JsonElement?): Boolean = (v as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean == true

    // A malformed class, parameterization or instance is skipped.
    private fun parseClass(v: JsonElement): ClassInfo? {
        val o = obj(v) ?: return null
        val sc = str(o["scalaClass"]) ?: return null
        val ps = arr(o["parameterizations"]) ?: return null
        return ClassInfo(sc, str(o["name"]) ?: sc.substringAfterLast('.'), bool(o["library"]), int(o["modules"]) ?: 0,
            ps.mapNotNull { p ->
                val x = obj(p) ?: return@mapNotNull null
                val label = str(x["label"]) ?: return@mapNotNull null
                val insts = arr(x["instances"]) ?: return@mapNotNull null
                ClassParams(label, bool(x["unknown"]), triples(x["params"], "name", "type", "value"), insts.mapNotNull { i ->
                    val y = obj(i) ?: return@mapNotNull null
                    val path = arr(y["path"])?.map { str(it) ?: return@mapNotNull null }?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val l = str(y["label"]) ?: return@mapNotNull null
                    ClassInstance(path, l, str(y["module"]) ?: "")
                })
            })
    }

    // ── Hierarchy ──

    /** The module at an instance path (top module name, then instance names). */
    fun moduleAt(d: DesignInfo, path: List<String>): ModuleInfo? {
        if (path.isEmpty()) return null
        var m = d.modules[path[0]]
        for (inst in path.drop(1)) m = m?.children?.find { it.first == inst }?.let { d.modules[it.second] }
        return m
    }

    fun hierRoots(d: DesignInfo): List<List<String>> = if (d.top in d.modules) listOf(listOf(d.top)) else emptyList()

    // A fold group's node: its module's path, then `#<group>` (no instance name starts
    // with `#`). The copies are its children, with their own paths.
    private const val GROUP_MARK = "#"

    private fun groupOf(path: List<String>): String? =
        path.lastOrNull()?.takeIf { it.startsWith(GROUP_MARK) }?.removePrefix(GROUP_MARK)

    /** The copies of `group` in the module at `parent`, in child order. */
    private fun copies(d: DesignInfo, parent: List<String>, group: String): List<Pair<String, String>> =
        moduleAt(d, parent)?.let { m -> m.children.filter { m.groups[it.first] == group } } ?: emptyList()

    /** A module's children, a fold group as one node where its first copy is; a group node's children are its copies. */
    fun hierChildren(d: DesignInfo, path: List<String>): List<List<String>> {
        groupOf(path)?.let { g -> return copies(d, path.dropLast(1), g).map { path.dropLast(1) + it.first } }
        val m = moduleAt(d, path) ?: return emptyList()
        val seen = mutableSetOf<String>()
        return m.children.mapNotNull { c ->
            when (val g = m.groups[c.first]) {
                null -> path + c.first
                else -> if (seen.add(g)) path + (GROUP_MARK + g) else null
            }
        }
    }

    fun hierId(path: List<String>): String = "hier:" + path.joinToString("/")

    /** Instance name, module (or Scala class) simple name. A fold group: `<group> × <n>`, its first copy's class. */
    fun hierRow(d: DesignInfo, path: List<String>): Row? {
        groupOf(path)?.let { g ->
            val cs = copies(d, path.dropLast(1), g)
            val m = cs.firstOrNull()?.let { d.modules[it.second] } ?: return null
            val of = m.scalaClass.ifEmpty { m.name }
            return Row(hierId(path), "$g × ${cs.size}", m.scalaClass.substringAfterLast('.').ifEmpty { m.name },
                "${cs.size} generator copies of $of, folded in the schematic", "layers", "group", expandable = true)
        }
        return moduleAt(d, path)?.let { m ->
            val cls = m.scalaClass.substringAfterLast('.')
            val desc = cls.ifEmpty { m.name }
            val tip = if (m.scalaClass.isNotEmpty()) "${m.scalaClass} (module ${m.name})" else "module ${m.name}"
            val icon = if (m.kind == "external" || m.kind == "intrinsic") "package" else "circuit-board"
            Row(hierId(path), path.last(), desc, tip, icon, "module", expandable = m.children.isNotEmpty())
        }
    }

    /** Where a Hierarchy row takes the schematic: into a module, or its parent with the leaf (or the
     *  folded box of a fold group) selected. */
    fun navigateTarget(d: DesignInfo, path: List<String>): Pair<List<String>, String?>? {
        groupOf(path)?.let { g -> return if (copies(d, path.dropLast(1), g).isEmpty()) null else path.dropLast(1) to g }
        return moduleAt(d, path)?.let { m ->
            if (m.children.isEmpty() && path.size > 1) path.dropLast(1) to path.last() else path to null
        }
    }

    /** A module's source; a fold group's is its first copy's. */
    fun moduleSource(d: DesignInfo, path: List<String>): SourceInfo? {
        groupOf(path)?.let { g -> return copies(d, path.dropLast(1), g).firstOrNull()?.let { d.modules[it.second] }?.source }
        return moduleAt(d, path)?.source
    }

    /** The Hierarchy row to reveal for the drill path (only one from the top), or the selected child
     *  instance under it, or the selected fold group. */
    fun revealPath(d: DesignInfo, path: List<String>, selection: String? = null): List<String>? {
        if (path.firstOrNull() != d.top) return null
        val m = moduleAt(d, path) ?: return null
        return when {
            selection == null -> path
            m.children.any { it.first == selection } -> path + selection
            m.groups.values.any { it == selection } -> path + (GROUP_MARK + selection)
            else -> path
        }
    }

    // ── Details ──

    fun detailRows(det: DetailsInfo): List<Row> {
        val rows = mutableListOf(Row("detail:name", det.name, det.module, "${det.name}: module ${det.module}", "symbol-module"))
        if (det.copies > 0) rows += Row("detail:copies", "${det.copies} generator copies", "folded",
            "${det.name} stands for ${det.copies} generator copies of ${det.module}", "layers")
        if (det.scalaClass.isNotEmpty()) rows += Row("detail:class", det.scalaClass, "Scala class", det.scalaClass, "symbol-class")
        if (det.params.isNotEmpty()) rows += Row("detail:params", "Parameters", det.params.size.toString(),
            icon = "symbol-parameter", expandable = true,
            children = det.params.mapIndexed { i, (n, t, v) ->
                val label = if (t.isNotEmpty()) "$n: $t = $v" else "$n = $v"
                Row("detail:param:$i", label, tooltip = label)
            })
        rows += Row("detail:ports", "Ports", det.ports.size.toString(), icon = "plug", expandable = true,
            children = det.ports.mapIndexed { i, (n, dir, t) ->
                val icon = when (dir.lowercase()) {
                    "in", "input", "slave" -> "arrow-right"
                    "out", "output", "master" -> "arrow-left"
                    else -> "arrow-both"
                }
                Row("detail:port:$i", n, "$dir $t", "$n: $dir $t", icon)
            })
        return rows
    }

    // ── Classes ──

    const val UNKNOWN_PARAMS = "some values could not be read: not compared"

    /** The Classes view: a row per class (collapsed, its module count), under it a row per
     *  parameterization (open, its instance count, `?` when not compared) and under that its
     *  instances, a selection showing each. A class built only without parameters lists its
     *  instances directly. Ids from the class, the parameterization's index and the path, as
     *  two parameterizations may share a label. */
    fun classRows(classes: List<ClassInfo>): List<Row> = classes.map { c ->
        val cid = "class:${c.scalaClass}"
        fun instances(pid: String, insts: List<ClassInstance>) = insts.map { i ->
            val p = i.path.joinToString("/")
            Row("$pid/$p", i.label, tooltip = "module ${i.module}\n$p", icon = "circuit-board", contextValue = "classInstance", path = i.path)
        }
        val single = c.parameterizations.singleOrNull()
        val kids = if (single != null && single.params.isEmpty()) instances(cid, single.instances)
        else c.parameterizations.mapIndexed { n, p ->
            val pid = "$cid#$n"
            Row(pid, if (p.unknown) "${p.label} ?" else p.label, p.instances.size.toString(),
                if (p.unknown) UNKNOWN_PARAMS else "modules built with the same constructor arguments",
                "symbol-parameter", "classParams", expandable = true, children = instances(pid, p.instances), expanded = true)
        }
        Row(cid, c.name, c.modules.toString(), c.scalaClass, if (c.library) "package" else "symbol-class", "class",
            expandable = kids.isNotEmpty(), children = kids)
    }

    // ── editor side ──

    /** A design source path made absolute against the project root (paths may be relative to it). */
    fun sourcePath(file: String, rootPath: String?): String? =
        if (file.startsWith("/") || Regex("^[A-Za-z]:[\\\\/].*").matches(file)) file
        else rootPath?.let { it.removeSuffix("/") + "/" + file }

    fun navigateMessage(path: List<String>, select: String?): JsonObject = JsonObject().apply {
        addProperty("type", "socle-navigate")
        add("path", JsonArray().apply { path.forEach { add(it) } })
        if (select == null) add("select", JsonNull.INSTANCE) else addProperty("select", select)
    }
}
