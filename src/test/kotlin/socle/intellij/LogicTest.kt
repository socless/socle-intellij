package socle.intellij

import com.google.gson.JsonParser
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogicTest {
    @Test
    fun javaCommandPrefersSettingThenJavaHome() {
        assertEquals("/opt/j/bin/java", Logic.javaCommand("/opt/j/bin/java", "/jdk"))
        assertEquals("/jdk/bin/java", Logic.javaCommand(" ", "/jdk"))
        assertEquals("java", Logic.javaCommand(null, null))
        assertEquals("java", Logic.javaCommand("", "  "))
    }

    @Test
    fun serverJarDefaultsToBundled() {
        assertEquals("/x/socle-lsp.jar", Logic.serverJar("/x/socle-lsp.jar", Path.of("/p/server/socle-lsp.jar")))
        assertEquals(Path.of("/p/server/socle-lsp.jar").toString(), Logic.serverJar(" ", Path.of("/p/server/socle-lsp.jar")))
    }

    @Test
    fun parseJavaMajor() {
        assertEquals(8, Logic.parseJavaMajor("openjdk version \"1.8.0_402\""))
        assertEquals(21, Logic.parseJavaMajor("openjdk version \"21.0.12\" 2026-08-18"))
        assertEquals(17, Logic.parseJavaMajor("java version \"17\""))
        assertNull(Logic.parseJavaMajor("bash: java: command not found"))
    }

    @Test
    fun javaProblem() {
        assertNull(Logic.javaProblem("java", 21))
        assertNull(Logic.javaProblem("java", 25))
        val old = Logic.javaProblem("java", 17)!!
        assertTrue(old.contains("Java 21 or newer"), old)
        assertTrue(old.contains("'java' is Java 17"), old)
        assertTrue(old.contains("Settings | Tools | Socle"), old)
        val missing = Logic.javaProblem("/no/java", null)!!
        assertTrue(missing.contains("'/no/java' could not be run"), missing)
    }

    @Test
    fun serverArgs() {
        assertEquals(listOf("-Djava.net.preferIPv4Stack=true", "-jar", "x.jar"), Logic.serverArgs("x.jar"))
    }

    @Test
    fun initializationOptions() {
        val settings = mapOf("waveFlag" to "emitFst", "followCursor" to false)
        val hosted = Logic.initializationOptions(settings, hostPanel = true)
        assertEquals("client", hosted["panel"])
        assertEquals("emitFst", hosted["waveFlag"])
        assertEquals(false, hosted["followCursor"])
        assertFalse(Logic.initializationOptions(settings, hostPanel = false).containsKey("panel"))
    }

    @Test
    fun relayUrlKeepsOriginOnly() {
        assertEquals("http://127.0.0.1:4711/__socle-intellij-host", Logic.relayUrl("http://127.0.0.1:4711/?x=1"))
        assertEquals("http://127.0.0.1:4711/__socle-intellij-host", Logic.relayUrl("http://127.0.0.1:4711"))
    }

    @Test
    fun frameUrls() {
        assertEquals("http://127.0.0.1:4711/?host=vscode&theme=dark", Logic.panelFrameUrl("http://127.0.0.1:4711/", "dark"))
        assertEquals("http://h:1/?a=1&host=vscode&theme=hc#x", Logic.panelFrameUrl("http://h:1/?a=1#x", "hc"))
        assertEquals("http://127.0.0.1:4711/?host=vscode&view=surfer&theme=light", Logic.surferFrameUrl("http://127.0.0.1:4711/", "light"))
    }

    @Test
    fun relayHtmlFramesTheEscapedUrlAndCallsTheHost() {
        val html = Logic.relayHtml("http://127.0.0.1:4711/?host=vscode&theme=dark", "window.__sent.push(s);")
        assertTrue(html.contains("<iframe src=\"http://127.0.0.1:4711/?host=vscode&amp;theme=dark\""), html)
        assertTrue(html.contains("window.__sent.push(s);"), html)
        assertTrue(html.contains("\"http://127.0.0.1:4711\""), html) // the exact frame origin
        assertTrue(html.contains("socleFromHost"), html)
    }

    @Test
    fun titles() {
        assertEquals("Socle design", Logic.panelTitle(null))
        assertEquals("Socle design", Logic.panelTitle(" "))
        assertEquals("Socle: t", Logic.panelTitle("t"))
        assertEquals("Socle: " + "a".repeat(59) + "…", Logic.panelTitle("a".repeat(70)))
        assertEquals("Surfer", Logic.surferTitle(null))
        assertEquals("Surfer: t", Logic.surferTitle("t"))
    }

    @Test
    fun showWavesProblem() {
        assertTrue(Logic.showWavesProblem("surfer", true)!!.contains("surfer-panel"))
        assertTrue(Logic.showWavesProblem(null, true)!!.contains("surfer-panel"))
        assertEquals("Socle: Run Simulate first.", Logic.showWavesProblem("surfer-panel", false))
        assertNull(Logic.showWavesProblem("surfer-panel", true))
    }

    @Test
    fun surferTest() {
        assertEquals("t", Logic.surferTest("a.Suite#t", "f"))
        assertEquals("whole", Logic.surferTest("whole", "f"))
        assertEquals("f", Logic.surferTest(" ", "f"))
        assertNull(Logic.surferTest(null, null))
    }

    @Test
    fun msgTypeAndRoute() {
        val cmd = JsonParser.parseString("""{"type":"socle-surfer-cmd","cmd":{"${'$'}type":"Hello"}}""").asJsonObject
        val status = JsonParser.parseString("""{"type":"socle-surfer-status","status":{}}""").asJsonObject
        assertEquals("socle-surfer-cmd", Logic.msgType(cmd))
        assertNull(Logic.msgType(JsonParser.parseString("""{"type":1}""").asJsonObject))
        assertEquals("Hello", Logic.cmdKind(cmd))
        assertEquals("surfer", Logic.route("panel", cmd))
        assertEquals("panel", Logic.route("surfer", status))
        assertNull(Logic.route("surfer", cmd))
        assertTrue(Logic.answersClosed(cmd, tabOpen = false))
        assertFalse(Logic.answersClosed(cmd, tabOpen = true))
    }

    @Test
    fun restartBudget() {
        val b = RestartBudget(3, 180_000)
        assertTrue(b.allow(0)); assertTrue(b.allow(1_000)); assertTrue(b.allow(2_000))
        assertFalse(b.allow(3_000))
        assertTrue(b.allow(181_000))
    }
}
