package socle.intellij.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPCodeLensFeature
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import com.intellij.psi.PsiFile
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageServer
import socle.intellij.Logic
import socle.intellij.SocleProjectService
import socle.intellij.settings.SocleSettings

/** LSP4IJ's entry for the Socle server (`com.redhat.devtools.lsp4ij.server` id `socle`, Scala files). */
class SocleServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val service = SocleProjectService.getInstance(project)
        val settings = SocleSettings.getInstance().state
        val java = Logic.javaCommand(settings.javaPath, System.getenv("JAVA_HOME"))
        val problem = Logic.javaProblem(java, javaMajor(java))
        if (problem != null) {
            service.reportProblem(problem)
            return Refused(problem)
        }
        val jar = Logic.serverJar(settings.serverJar, bundledJar())
        val cmd = GeneralCommandLine(listOf(java) + Logic.serverArgs(jar))
        project.basePath?.let { cmd.withWorkDirectory(it) }
        return OSProcessStreamConnectionProvider(cmd).also { p ->
            // A crash is restarted within the budget; a stop the user or LSP4IJ asked for is not "unexpected".
            p.addUnexpectedServerStopHandler {
                if (service.onUnexpectedStop(System.currentTimeMillis())) {
                    LanguageServerManager.getInstance(project).start(SocleProjectService.SERVER_ID)
                }
            }
        }
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl = SocleClient(project)

    override fun getServerInterface(): Class<out LanguageServer> = SocleServerApi::class.java

    override fun createClientFeatures(): LSPClientFeatures = SocleClientFeatures()

    /** A provider that never starts: the reason shows in LSP4IJ's server view. */
    private class Refused(private val problem: String) : OSProcessStreamConnectionProvider() {
        override fun start() { throw CannotStartProcessException(problem) }
    }

    companion object {
        /** The jar inside the installed plugin: `<plugin>/server/socle-lsp.jar`, beside the plugin's `lib/` that holds this class. */
        fun bundledJar(): Path =
            PathManager.getJarForClass(SocleServerFactory::class.java)!!.parent.parent.resolve("server").resolve("socle-lsp.jar")

        /** Runs `<java> -version` (10 s at most): its major version, or null when it cannot run. */
        fun javaMajor(java: String): Int? = try {
            val p = ProcessBuilder(java, "-version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); null } else Logic.parseJavaMajor(out)
        } catch (_: Exception) {
            null
        }
    }
}

/** Socle's client features: off in untrusted projects, no code lenses (the gutter has the actions), the panel hosted when JCEF runs. */
class SocleClientFeatures : LSPClientFeatures() {
    init {
        codeLensFeature = object : LSPCodeLensFeature() {
            override fun isEnabled(file: PsiFile): Boolean = false
        }
    }

    override fun isEnabled(file: VirtualFile): Boolean = TrustedProjects.isProjectTrusted(project)

    /** The design panel and the Surfer tab live on the server: it stays up when the last Scala editor closes. */
    override fun keepServerAlive(): Boolean = true

    override fun initializeParams(params: InitializeParams) {
        super.initializeParams(params)
        val hostPanel = JBCefApp.isSupported()
        params.initializationOptions = Logic.initializationOptions(SocleSettings.getInstance().state.toServerSettings(), hostPanel)
    }
}
