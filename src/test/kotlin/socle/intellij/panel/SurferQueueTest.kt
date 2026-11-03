package socle.intellij.panel

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurferQueueTest {
    private fun cmd(t: String, n: Int): JsonObject =
        JsonParser.parseString("""{"type":"socle-surfer-cmd","cmd":{"${'$'}type":"$t","n":$n}}""").asJsonObject
    private fun tags(xs: List<JsonObject>): List<String> =
        xs.map { val c = it.getAsJsonObject("cmd"); c.get("\$type").asString + c.get("n").asInt }

    private val sent = mutableListOf<JsonObject>()
    private val q = SurferQueue { sent += it }

    @Test fun latestOfEachKindDrainedLoadShowFocus() {
        q.offer(cmd("Show", 1)); q.offer(cmd("Focus", 1)); q.offer(cmd("Show", 2))
        q.offer(cmd("Load", 1)); q.offer(cmd("Focus", 2)); q.offer(cmd("Show", 3))
        assertTrue(sent.isEmpty())
        val out = q.ready()
        assertEquals(listOf("Load1", "Show3", "Focus2"), tags(out))
        assertEquals(tags(out), tags(sent))
    }

    @Test fun aShowBeforeAnyLoadNeverDropsTheLoad() {
        q.offer(cmd("Show", 1)); q.offer(cmd("Load", 1)); q.offer(cmd("Show", 2))
        assertEquals(listOf("Load1", "Show2"), tags(q.ready()))
    }

    @Test fun aNewLoadClearsTheQueuedShowAndFocus() {
        q.offer(cmd("Load", 1)); q.offer(cmd("Show", 1)); q.offer(cmd("Focus", 1)); q.offer(cmd("Load", 2))
        assertEquals(listOf("Load2"), tags(q.ready()))
    }

    @Test fun aShowAfterItsLoadIsKept() {
        q.offer(cmd("Load", 1)); q.offer(cmd("Show", 1)); q.offer(cmd("Load", 2)); q.offer(cmd("Show", 2))
        assertEquals(listOf("Load2", "Show2"), tags(q.ready()))
    }

    @Test fun helloIsDrainedLast() {
        q.offer(cmd("Hello", 1)); q.offer(cmd("Load", 1)); q.offer(cmd("Focus", 1)); q.offer(cmd("Show", 1))
        assertTrue(sent.isEmpty())
        assertEquals(listOf("Load1", "Show1", "Focus1", "Hello1"), tags(q.ready()))
    }

    @Test fun onceReadyOffersPassThroughUntilReset() {
        q.ready()
        q.offer(cmd("Hello", 2))
        assertEquals(listOf("Hello2"), tags(sent))
        q.reset()
        q.offer(cmd("Load", 3))
        assertEquals(listOf("Hello2"), tags(sent))
    }

    @Test fun opensANewTraceOnceAgainOnlyWhenForced() {
        val o = SurferOpens()
        assertFalse(o.traced)
        assertTrue(o.admit("t1", false)); assertFalse(o.admit("t1", false))
        assertTrue(o.admit("t1", true)); assertFalse(o.admit("t1", false))
        assertTrue(o.admit("t2", false))
        assertFalse(o.admit("", false)); assertTrue(o.admit("", true))
        assertTrue(o.traced)
    }

    // ── the channel between the panel and the tab ──

    private val toPanel = mutableListOf<JsonObject>()
    private val toTab = mutableListOf<JsonObject>()
    private val ch = SurferChannel(toPanel = { toPanel += it }, toTab = { toTab += it })
    private fun status() = JsonParser.parseString("""{"type":"socle-surfer-status","status":{"n":1}}""").asJsonObject

    @Test fun commandsWaitForTheTabsFirstStatus() {
        ch.tabCreated()
        ch.command(cmd("Load", 1)); ch.command(cmd("Show", 1))
        assertTrue(toTab.isEmpty())
        ch.fromTab(status())
        assertEquals(listOf("Load1", "Show1"), tags(toTab))
        assertEquals(listOf("socle-surfer-status"), toPanel.map { it.get("type").asString })
    }

    @Test fun helloWithoutATabIsAnsweredClosed() {
        ch.command(cmd("Hello", 1))
        assertEquals(listOf("socle-surfer-closed"), toPanel.map { it.get("type").asString })
        assertTrue(toTab.isEmpty())
    }

    @Test fun closingTheTabTellsThePanel() {
        ch.tabCreated()
        ch.fromTab(status())
        toPanel.clear()
        ch.tabClosed()
        assertEquals(listOf("socle-surfer-closed"), toPanel.map { it.get("type").asString })
        ch.tabCreated()
        ch.command(cmd("Load", 2))
        assertTrue(toTab.isEmpty()) // a new tab waits for its own first status
    }

    @Test fun aSecondOpenOfTheSameTraceWithoutForceIsRefused() {
        fun open(trace: String, force: Boolean) =
            JsonParser.parseString("""{"type":"socle-surfer-open","trace":"$trace","traceTest":"a.S#t","force":$force}""").asJsonObject
        assertTrue(ch.openRequest(open("/w/t.vcd", false)))
        assertFalse(ch.openRequest(open("/w/t.vcd", false)))
        assertTrue(ch.openRequest(open("/w/t.vcd", true)))
        assertTrue(ch.traced)
    }
}
