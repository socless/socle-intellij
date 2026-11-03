package socle.intellij.panel

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import socle.intellij.Logic

/**
 * The Surfer tab's commands until its first status: the latest of each kind, drained Load, Show, Focus,
 * then Hello (the status it asks for reports the rest). Ported from socle-vscode's `SurferQueue`.
 */
class SurferQueue(private val send: (JsonObject) -> Unit) {
    private var isReady = false
    private val queued = LinkedHashMap<String, JsonObject>()

    private fun rank(kind: String): Int = when (kind) {
        "Load" -> 0
        "Show" -> 1
        "Focus" -> 2
        "Hello" -> 3
        else -> 4
    }

    @Synchronized
    fun offer(cmd: JsonObject) {
        if (isReady) { send(cmd); return }
        val k = Logic.cmdKind(cmd)
        // A new trace: the queued Show and Focus were of the old one.
        if (k == "Load") { queued.remove("Show"); queued.remove("Focus") }
        queued.remove(k)
        queued[k] = cmd
    }

    @Synchronized
    fun ready(): List<JsonObject> {
        val out = queued.entries.sortedWith(compareBy({ rank(it.key) }, { it.key })).map { it.value }
        queued.clear()
        isReady = true
        out.forEach(send)
        return out
    }

    @Synchronized
    fun reset() { isReady = false }
}

/**
 * Which `socle-surfer-open` requests open the Surfer tab: a forced one always; an unforced one only for a
 * trace not opened before, so a reloaded panel does not bring back a tab the user closed.
 */
class SurferOpens {
    private val seen = mutableSetOf<String>()

    /** Whether any request so far carried a trace (a Simulate gave the panel one). */
    val traced: Boolean @Synchronized get() = seen.isNotEmpty()

    @Synchronized
    fun admit(trace: String, force: Boolean): Boolean {
        val ok = force || (trace.isNotEmpty() && trace !in seen)
        if (trace.isNotEmpty()) seen += trace
        return ok
    }
}

/**
 * The Surfer channel between the design panel and the Surfer tab, without the browsers: commands from the
 * panel wait in a [SurferQueue] until the tab's first status, statuses go to the panel, and the panel hears
 * `socle-surfer-closed` when the tab closes (or when it says Hello with no tab).
 */
class SurferChannel(private val toPanel: (JsonObject) -> Unit, toTab: (JsonObject) -> Unit) {
    private val queue = SurferQueue(toTab)
    private val opens = SurferOpens()
    @Volatile var tabOpen = false; private set

    val traced: Boolean get() = opens.traced

    /** A `socle-surfer-open` from the panel: whether to open (or bring forward) the tab. */
    fun openRequest(msg: JsonObject): Boolean {
        val trace = (msg.get("trace") as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: ""
        val force = (msg.get("force") as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean ?: false
        return opens.admit(trace, force)
    }

    /** A `socle-surfer-cmd` from the panel. */
    fun command(msg: JsonObject) {
        if (Logic.answersClosed(msg, tabOpen)) toPanel(CLOSED()) else queue.offer(msg)
    }

    /** A message from the tab: a status goes to the panel, and the first one releases the queued commands. */
    fun fromTab(msg: JsonObject) {
        if (Logic.route("surfer", msg) != "panel") return
        queue.ready()
        toPanel(msg)
    }

    /** A new tab, or the tab loaded a new page: its commands wait for its first status again. */
    fun tabCreated() { tabOpen = true; queue.reset() }

    fun tabClosed() {
        tabOpen = false
        queue.reset()
        toPanel(CLOSED())
    }

    companion object {
        @Suppress("FunctionName")
        fun CLOSED(): JsonObject = JsonObject().apply { addProperty("type", "socle-surfer-closed") }
    }
}
