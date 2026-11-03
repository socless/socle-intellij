package socle.intellij.panel

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import socle.intellij.SidebarModel

class RelayRouterTest {
    private val loads = mutableListOf<String>()
    private val posts = mutableListOf<String>()
    private fun router(theme: String = "dark") = RelayRouter(theme, "SEND(s);", { loads += it }, { posts += it })
    private fun type(json: String) = JsonParser.parseString(json).asJsonObject.get("type").asString

    @Test
    fun handlesOnlyTheCurrentRelay() {
        val r = router()
        assertFalse(r.handles("http://127.0.0.1:4711/__socle-intellij-host"))
        r.show("http://127.0.0.1:4711/")
        assertTrue(r.handles("http://127.0.0.1:4711/__socle-intellij-host"))
        assertFalse(r.handles("http://127.0.0.1:4712/__socle-intellij-host"))
        assertFalse(r.handles("http://127.0.0.1:4711/design.json"))
    }

    @Test
    fun theSameUrlDoesNotReload() {
        val r = router()
        r.show("http://127.0.0.1:4711/")
        r.show("http://127.0.0.1:4711/")
        assertEquals(listOf("http://127.0.0.1:4711/__socle-intellij-host"), loads)
    }

    @Test
    fun aNewPortRepointsTheRelay() {
        val r = router()
        r.show("http://127.0.0.1:4711/")
        r.show("http://127.0.0.1:5000/")
        assertEquals("http://127.0.0.1:5000/__socle-intellij-host", loads.last())
        assertTrue(r.handles("http://127.0.0.1:5000/__socle-intellij-host"))
        assertTrue(r.page()!!.contains("http://127.0.0.1:5000/?host=vscode&amp;theme=dark"))
    }

    @Test
    fun pageFramesTheUiWithTheThemeAndTheBridge() {
        val r = router("light")
        assertNull(r.page())
        r.show("http://127.0.0.1:4711/")
        val page = r.page()!!
        assertTrue(page.contains("http://127.0.0.1:4711/?host=vscode&amp;theme=light"))
        assertTrue(page.contains("SEND(s);"))
    }

    @Test
    fun aThemeChangePostsOnce() {
        val r = router("dark")
        r.show("http://127.0.0.1:4711/")
        r.setTheme("dark")
        assertEquals(0, posts.size)
        r.setTheme("light")
        assertEquals(1, posts.size)
        assertEquals("socle-theme", type(posts[0]))
        assertTrue(posts[0].contains("\"kind\":\"light\""))
    }

    @Test
    fun navigateWaitsForTheFirstState() {
        val r = router()
        r.show("http://127.0.0.1:4711/")
        r.navigate(SidebarModel.navigateMessage(listOf("A"), null))
        r.navigate(SidebarModel.navigateMessage(listOf("A", "b"), null))
        assertEquals(0, posts.size)
        val state = r.onUiMessage("""{"type":"socle-state","path":["A"],"selection":null,"details":null}""")
        assertEquals("socle-state", state!!.get("type").asString)
        assertEquals(1, posts.size) // only the last navigate
        assertTrue(posts[0].contains("\"b\""))
        r.navigate(SidebarModel.navigateMessage(listOf("A"), null))
        assertEquals(2, posts.size)
    }

    @Test
    fun aReloadWaitsForTheStateAgain() {
        val r = router()
        r.show("http://127.0.0.1:4711/")
        r.onUiMessage("""{"type":"socle-state","path":[],"selection":null,"details":null}""")
        r.show("http://127.0.0.1:5000/")
        r.navigate(SidebarModel.navigateMessage(listOf("A"), null))
        assertEquals(0, posts.size)
    }

    @Test
    fun malformedUiMessagesAreDropped() {
        val r = router()
        assertNull(r.onUiMessage("not json"))
        assertNull(r.onUiMessage("[1]"))
    }
}
