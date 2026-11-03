package socle.intellij

import com.intellij.util.messages.Topic

/** What happens in a project's Socle session, on the project's message bus. Any thread: UI listeners hop to the EDT. */
interface SocleEvents {
    /** The server asks to show the live UI at `url` (`socle/openPanel`). */
    fun openPanel(url: String, test: String?) {}

    /** The run state changed (`socle/status`); the run tracker is already updated. */
    fun status(status: String, suite: String, test: String, message: String) {}

    /** The project's tests changed after a save (`socle/testsChanged`). */
    fun testsChanged() {}

    /** A `socle/tests` answer arrived. */
    fun suitesUpdated(suites: List<SidebarModel.SuiteInfo>) {}

    /** The UI in the panel posted its state (`socle-state`). */
    fun uiState(state: SidebarModel.UiState) {}

    /** The design panel was closed. */
    fun panelClosed() {}

    /** Why the server cannot run (`null`: no problem any more). */
    fun serverProblem(message: String?) {}

    companion object {
        @JvmField
        val TOPIC: Topic<SocleEvents> = Topic.create("Socle events", SocleEvents::class.java)
    }
}
