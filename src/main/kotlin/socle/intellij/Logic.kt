package socle.intellij

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.nio.file.Path

/** The plugin's pure decisions, ported from Socle for VS Code's `Logic`. No IDE calls, so plain tests run them. */
object Logic {
    const val MIN_JAVA = 21

    /** The path the plugin serves its relay page on, at the panel server's own origin. */
    const val RELAY_PATH = "/__socle-intellij-host"

    private const val TITLE_MAX = 60

    private fun nonBlank(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    /** The java to launch: the setting if non-blank, else `$JAVA_HOME/bin/java`, else `java` on the PATH. */
    fun javaCommand(setting: String?, javaHome: String?): String =
        nonBlank(setting) ?: nonBlank(javaHome)?.let { "$it/bin/java" } ?: "java"

    /** The language server jar: the setting if non-blank, else the bundled one. */
    fun serverJar(setting: String?, bundled: Path): String = nonBlank(setting) ?: bundled.toString()

    private val versionRe = Regex("""version "([0-9][^"]*)"""")

    /** The major Java version from `java -version` output: `"1.8.0_402"` is 8, `"21.0.12"` is 21. */
    fun parseJavaMajor(output: String): Int? {
        val v = versionRe.find(output)?.groupValues?.get(1) ?: return null
        val parts = v.split(Regex("[^0-9]+"))
        val major = if (parts.size > 1 && parts[0] == "1") parts[1] else parts[0]
        return major.toIntOrNull()
    }

    /** Why `javaCmd` cannot run the server (`major` null when it did not run or did not parse), or null. */
    fun javaProblem(javaCmd: String, major: Int?): String? {
        val fix = "Set the Java path in Settings | Tools | Socle to a Java $MIN_JAVA+ executable, then restart the Socle language server."
        return when {
            major == null -> "Socle needs Java $MIN_JAVA or newer, but '$javaCmd' could not be run. $fix"
            major < MIN_JAVA -> "Socle needs Java $MIN_JAVA or newer, but '$javaCmd' is Java $major. $fix"
            else -> null
        }
    }

    /** The server's JVM arguments: an IPv4-only stack, as in VS Code (WSL relays only IPv4 loopback). */
    fun serverArgs(jar: String): List<String> = listOf("-Djava.net.preferIPv4Stack=true", "-jar", jar)

    /** The server's flat `initializationOptions`: the settings, plus `panel: "client"` when the plugin hosts the panel. */
    fun initializationOptions(settings: Map<String, Any>, hostPanel: Boolean): Map<String, Any> =
        if (hostPanel) linkedMapOf<String, Any>("panel" to "client").apply { putAll(settings) } else LinkedHashMap(settings)

    private val originRe = Regex("""^([A-Za-z][A-Za-z0-9+.-]*://[^/?#\s"'<>;]+)""")

    /** The origin (`scheme://host:port`) of a URL, or null. */
    fun origin(url: String): String? = originRe.find(url)?.groupValues?.get(1)

    /** The relay page's URL for a panel server URL: the same origin, at [RELAY_PATH]. */
    fun relayUrl(panelUrl: String): String = (origin(panelUrl) ?: panelUrl.trimEnd('/')) + RELAY_PATH

    /** The UI URL the relay page frames: embedded mode (`host=vscode`) and the initial theme kind. */
    fun panelFrameUrl(panelUrl: String, theme: String): String = withQuery(panelUrl, "host=vscode&theme=$theme")

    /** The UI URL of the Surfer tab: `host=vscode&view=surfer` and the initial theme kind. */
    fun surferFrameUrl(panelUrl: String, theme: String): String = withQuery(panelUrl, "host=vscode&view=surfer&theme=$theme")

    private fun withQuery(url: String, query: String): String {
        val i = url.indexOf('#')
        val (base, frag) = if (i < 0) url to "" else url.substring(0, i) to url.substring(i)
        val sep = if (base.contains("?")) "&" else "?"
        return "$base$sep$query$frag"
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * The relay page: a full-size iframe on `frameUrl`. Messages from the iframe (its window and exact
     * origin only, of the types that go up) reach the plugin through `sendToHostJs`, a statement that
     * sends the JSON string `s` (a `JBCefJSQuery` injection). The plugin calls `socleFromHost(json)` to
     * post one of the types that go down to the iframe, rebuilt with only its own fields.
     */
    fun relayHtml(frameUrl: String, sendToHostJs: String): String {
        val o = origin(frameUrl) ?: "null"
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; } iframe { border: 0; width: 100%; height: 100%; display: block; }</style>
</head>
<body>
<iframe src="${escapeHtml(frameUrl)}"></iframe>
<script>
(function () {
  var frame = document.querySelector('iframe');
  var origin = "$o";
  var up = ['socle-state', 'socle-surfer-open', 'socle-surfer-cmd', 'socle-surfer-status'];
  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || typeof d !== 'object' || e.source !== frame.contentWindow || e.origin !== origin) return;
    if (up.indexOf(d.type) < 0) return;
    var s = JSON.stringify(d);
    $sendToHostJs
  });
  window.socleFromHost = function (json) {
    var d = JSON.parse(json), m = null;
    if (d.type === 'socle-navigate') m = { type: d.type, path: d.path, select: d.select === undefined ? null : d.select };
    else if (d.type === 'socle-theme') m = { type: d.type, kind: d.kind };
    else if (d.type === 'socle-surfer-cmd') m = { type: d.type, cmd: d.cmd };
    else if (d.type === 'socle-surfer-status') m = { type: d.type, status: d.status };
    else if (d.type === 'socle-surfer-closed') m = { type: d.type };
    if (m && frame.contentWindow) frame.contentWindow.postMessage(m, origin);
  };
})();
</script>
</body>
</html>
"""
    }

    /** The panel's tab title: `Socle: <test>`, the test cut to 60 characters (the last one "…"). */
    fun panelTitle(test: String?): String = nonBlank(test)?.let { "Socle: ${shortTest(it)}" } ?: "Socle design"

    /** The Surfer tab's title: `Surfer: <test>`, cut as in [panelTitle], or `Surfer`. */
    fun surferTitle(test: String?): String = nonBlank(test)?.let { "Surfer: ${shortTest(it)}" } ?: "Surfer"

    private fun shortTest(t: String): String {
        if (t.length <= TITLE_MAX) return t
        var cut = t.substring(0, TITLE_MAX - 1)
        if (Character.isHighSurrogate(cut.last())) cut = cut.dropLast(1)
        return "$cut…"
    }

    /** Why Show waveforms has nothing to show, or null when it can. */
    fun showWavesProblem(viewer: String?, hasTrace: Boolean): String? = when {
        nonBlank(viewer) != "surfer-panel" ->
            "Socle: the Surfer tab is for the surfer-panel viewer. Set the wave viewer to surfer-panel in Settings | Tools | Socle, then Simulate."
        !hasTrace -> "Socle: Run Simulate first."
        else -> null
    }

    /** The test the Surfer tab is titled after: the test part of `traceTest` (`<suite>#<test>`), else `fallback`. */
    fun surferTest(traceTest: String?, fallback: String?): String? =
        nonBlank(traceTest)?.let { t -> nonBlank(t.substringAfter('#', t)) } ?: fallback

    private fun str(o: JsonObject, key: String): String? =
        (o.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /** A message's `type`, when it is a string. */
    fun msgType(msg: JsonObject): String? = str(msg, "type")

    /** A `socle-surfer-cmd`'s command kind (`cmd.$type`), "" when it has none. */
    fun cmdKind(msg: JsonObject): String = (msg.get("cmd") as? JsonObject)?.let { str(it, "\$type") } ?: ""

    /** Whether the plugin answers the panel's command itself with `socle-surfer-closed`: a Hello with no Surfer tab. */
    fun answersClosed(msg: JsonObject, tabOpen: Boolean): Boolean = !tabOpen && cmdKind(msg) == "Hello"

    /** Where a Surfer message goes: a cmd from the panel to "surfer", a status from the tab to "panel"; else null. */
    fun route(from: String, msg: JsonObject): String? = when (from to msgType(msg)) {
        "panel" to "socle-surfer-cmd" -> "surfer"
        "surfer" to "socle-surfer-status" -> "panel"
        else -> null
    }
}

/** At most `max` restarts in any `windowMs` window. */
class RestartBudget(private val max: Int, private val windowMs: Long) {
    private var stamps = listOf<Long>()

    fun allow(nowMs: Long): Boolean {
        stamps = stamps.filter { nowMs - it < windowMs }
        val ok = stamps.size < max
        if (ok) stamps = stamps + nowMs
        return ok
    }
}
