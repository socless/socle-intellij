package socle.intellij.panel

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import socle.intellij.Logic

/**
 * A JCEF browser showing the Socle UI through the relay page: the plugin serves the relay at
 * `<server origin>/__socle-intellij-host` itself (the request never reaches the server), so the
 * framed UI sees a parent of its own origin; its messages come back through a `JBCefJSQuery`.
 */
class RelayBrowser(
    parent: Disposable,
    theme: String,
    frameUrl: (String, String) -> String,
    private val onMessage: (JsonObject) -> Unit,
) : Disposable {
    private val browser = JBCefBrowser.createBuilder().setCreateImmediately(true).build()
    private val query = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase)

    val router = RelayRouter(
        theme,
        query.inject("s"),
        load = { url -> LOG.info("Socle relay: load $url"); browser.loadURL(url) },
        post = { json -> browser.cefBrowser.executeJavaScript("window.socleFromHost && window.socleFromHost(${JsonPrimitive(json)});", browser.cefBrowser.url, 0) },
        frameUrl = frameUrl,
    )

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, this)
        Disposer.register(this, browser)
        Disposer.register(this, query)
        query.addHandler { json ->
            LOG.debug("Socle relay: from UI ${json.take(120)}")
            router.onUiMessage(json)?.let(onMessage)
            null
        }
        browser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                b: CefBrowser?, frame: CefFrame?, request: CefRequest, isNavigation: Boolean, isDownload: Boolean,
                initiator: String?, disableDefault: BoolRef?,
            ): CefResourceRequestHandler? {
                LOG.debug("Socle relay: request ${request.url} navigation=$isNavigation")
                if (!router.handles(request.url)) return null
                val page = router.page() ?: return null
                LOG.info("Socle relay: serving ${request.url}")
                return object : CefResourceRequestHandlerAdapter() {
                    override fun getResourceHandler(b: CefBrowser?, f: CefFrame?, r: CefRequest?): CefResourceHandler = HtmlResource(page)
                }
            }
        }, browser.cefBrowser)
        browser.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                LOG.debug("Socle relay: loaded ${frame?.url} status=$httpStatusCode main=${frame?.isMain}")
            }
            override fun onLoadError(b: CefBrowser?, frame: CefFrame?, errorCode: org.cef.handler.CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                LOG.info("Socle relay: load error $errorCode $errorText $failedUrl")
            }
        }, browser.cefBrowser)
    }

    override fun dispose() {}

    /** Serves one HTML string. */
    private class HtmlResource(html: String) : CefResourceHandlerAdapter() {
        private val bytes = html.toByteArray(StandardCharsets.UTF_8)
        private var offset = 0

        override fun processRequest(request: CefRequest?, callback: CefCallback): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(response: CefResponse, length: IntRef, redirect: StringRef?) {
            response.mimeType = "text/html"
            response.status = 200
            response.setHeaderByName("Cache-Control", "no-store", true)
            length.set(bytes.size)
        }

        override fun readResponse(out: ByteArray, bytesToRead: Int, read: IntRef, callback: CefCallback?): Boolean {
            if (offset >= bytes.size) { read.set(0); return false }
            val n = minOf(bytesToRead, bytes.size - offset)
            System.arraycopy(bytes, offset, out, 0, n)
            offset += n
            read.set(n)
            return true
        }
    }

    companion object {
        private val LOG = logger<RelayBrowser>()

        /** Whether a URL is a relay page URL (any port). */
        fun isRelay(url: String): Boolean = url.endsWith(Logic.RELAY_PATH)
    }
}
