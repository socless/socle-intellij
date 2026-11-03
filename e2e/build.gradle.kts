// A test-only companion plugin for the end-to-end check (e2e/run.sh): never shipped.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion")) { useInstaller = false }
        plugin("com.redhat.devtools.lsp4ij", providers.gradleProperty("lsp4ijVersion").get())
        bundledPlugin("com.intellij.modules.jcef")
    }
    compileOnly(project(":"))
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        version = "0.0.0"
        ideaVersion { sinceBuild = "262"; untilBuild = provider { null } }
    }
}
