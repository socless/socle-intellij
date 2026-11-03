import java.net.URI
import java.security.MessageDigest
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform")
}

group = "socless"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        // The free edition's platform (Maven artifact): the unified installer carries Ultimate
        // modules whose startup activities fail in the test sandbox.
        intellijIdeaCommunity(providers.gradleProperty("platformVersion")) { useInstaller = false }
        plugin("org.intellij.scala", providers.gradleProperty("scalaPluginVersion").get())
        plugin("com.redhat.devtools.lsp4ij", providers.gradleProperty("lsp4ijVersion").get())
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

/** Downloads the pinned socle-lsp jar (HTTPS, else `gh release download`) and checks its sha256;
  * SOCLE_LSP_JAR replaces it with a local jar, unchecked. */
abstract class ServerJar : DefaultTask() {
    @get:Input abstract val socleVersion: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input @get:Optional abstract val localJar: Property<String>
    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun fetch() {
        val out = output.get().asFile
        out.parentFile.mkdirs()
        val local = localJar.orNull?.takeIf { it.isNotBlank() }
        if (local != null) {
            File(local).copyTo(out, overwrite = true)
            logger.lifecycle("socle-lsp: using SOCLE_LSP_JAR $local (no hash check)")
            return
        }
        val v = socleVersion.get()
        val name = "socle-lsp-$v.jar"
        val url = "https://github.com/socless/socle/releases/download/v$v/$name"
        try {
            URI(url).toURL().openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            logger.lifecycle("socle-lsp: HTTPS download failed (${e.message}), trying gh release download")
            val tmp = temporaryDir
            val p = ProcessBuilder("gh", "release", "download", "v$v", "--repo", "socless/socle",
                "--pattern", name, "--dir", tmp.absolutePath, "--clobber").inheritIO().start()
            if (p.waitFor() != 0) throw GradleException("socle-lsp: could not download $name (HTTPS and gh both failed)")
            File(tmp, name).copyTo(out, overwrite = true)
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(out.readBytes()).joinToString("") { "%02x".format(it) }
        if (actual != sha256.get()) {
            out.delete()
            throw GradleException("socle-lsp: $name has sha256 $actual, expected ${sha256.get()} (socleLspSha256 in gradle.properties)")
        }
    }
}

val serverJar = tasks.register<ServerJar>("serverJar") {
    socleVersion = providers.gradleProperty("socleVersion")
    sha256 = providers.gradleProperty("socleLspSha256")
    localJar = providers.environmentVariable("SOCLE_LSP_JAR")
    output = layout.buildDirectory.file("serverJar/socle-lsp.jar")
}

tasks {
    prepareSandbox {
        from(serverJar) { into(pluginName.map { "$it/server" }) }
    }
    test {
        useJUnitPlatform()
    }
}
