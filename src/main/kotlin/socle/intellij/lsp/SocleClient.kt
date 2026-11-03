package socle.intellij.lsp

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import socle.intellij.SocleEvents
import socle.intellij.SocleProjectService
import socle.intellij.settings.SocleSettings

/** The client side of Socle's protocol: the server's socle notifications go to the project's [SocleEvents]. */
class SocleClient(project: Project) : LanguageClientImpl(project) {
    private val service get() = SocleProjectService.getInstance(project)
    private val events get() = project.messageBus.syncPublisher(SocleEvents.TOPIC)

    init {
        service.client = this
    }

    private fun str(o: JsonObject, key: String): String? = (o.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    @JsonNotification("socle/openPanel")
    fun openPanel(params: JsonObject) {
        val url = str(params, "url") ?: return
        val test = str(params, "test")
        service.onOpenPanel(url, test)
        events.openPanel(url, test)
    }

    @JsonNotification("socle/status")
    fun status(params: JsonObject) {
        val status = str(params, "status") ?: return
        val suite = str(params, "suite") ?: ""
        val test = str(params, "test") ?: ""
        service.onStatus(status, suite, test)
        events.status(status, suite, test, str(params, "message") ?: "")
    }

    @JsonNotification("socle/testsChanged")
    fun testsChanged() {
        events.testsChanged()
    }

    /** `workspace/didChangeConfiguration` settings: the `socle` section. */
    fun settingsForServer(): Map<String, Any> = mapOf("socle" to SocleSettings.getInstance().state.toServerSettings())

    override fun createSettings(): Any = settingsForServer()

    override fun handleServerStatusChanged(status: ServerStatus) {
        super.handleServerStatusChanged(status)
        if (status == ServerStatus.started) service.serverStarted()
    }
}
