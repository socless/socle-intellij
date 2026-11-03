package socle.intellij.panel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import socle.intellij.Logic

/**
 * The state of one JCEF tab that shows the Socle UI through the relay page: which server it points at,
 * the page it serves, whether the UI listens yet (its first `socle-state`), the navigate waiting for that,
 * and the theme. Side effects go through `load` (navigate the browser) and `post` (a JSON message to the UI).
 * `frameUrl` builds the framed UI URL (the panel's or the Surfer tab's) from the server URL and the theme.
 */
class RelayRouter(
    private var theme: String,
    private val sendToHostJs: String,
    private val load: (String) -> Unit,
    private val post: (String) -> Unit,
    private val frameUrl: (String, String) -> String = Logic::panelFrameUrl,
) {
    @Volatile private var panelUrl: String? = null
    @Volatile private var relay: String? = null
    private var ready = false
    private var pending: JsonObject? = null

    /** Points the tab at the server `url`; reloads only when the server (its origin) changed. */
    @Synchronized
    fun show(url: String) {
        val next = Logic.relayUrl(url)
        panelUrl = url
        if (next == relay) return
        relay = next
        ready = false
        load(next)
    }

    /** Whether `url` is the relay page this tab serves. */
    fun handles(url: String): Boolean = relay != null && url == relay

    /** The relay page's HTML, or null before any [show]. */
    fun page(): String? = panelUrl?.let { Logic.relayHtml(frameUrl(it, theme), sendToHostJs) }

    /** A message from the UI (JSON text): parsed, or null when it is not an object. A `socle-state` means the UI listens. */
    @Synchronized
    fun onUiMessage(json: String): JsonObject? {
        val msg = try { JsonParser.parseString(json) as? JsonObject } catch (_: Exception) { null } ?: return null
        if (Logic.msgType(msg) == "socle-state" && !ready) {
            ready = true
            pending?.let { post(it.toString()) }
            pending = null
        }
        return msg
    }

    /** A `socle-navigate` for the UI: now when it listens, else the last one waits for it. */
    @Synchronized
    fun navigate(msg: JsonObject) {
        if (ready) post(msg.toString()) else pending = msg
    }

    /** Any other message for the UI (Surfer channel): posted as is. */
    fun send(msg: JsonObject) = post(msg.toString())

    /** The IDE theme changed: tell the UI (`socle-theme`), once per change. */
    @Synchronized
    fun setTheme(kind: String) {
        if (kind == theme) return
        theme = kind
        if (relay != null) post(JsonObject().apply { addProperty("type", "socle-theme"); addProperty("kind", kind) }.toString())
    }
}
