package socle.intellij.lsp

import com.google.gson.JsonElement
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import socle.intellij.SocleProjectService

/** The Socle server's methods beyond LSP (Socle's docs/lsp-protocol.md). */
interface SocleServerApi : LanguageServer {
    @JsonRequest("socle/tests")
    fun tests(): CompletableFuture<JsonElement>

    @JsonNotification("socle/cursor")
    fun cursor(params: SocleProjectService.CursorParams)
}
