package socle.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil

/** The Socle settings: the keys, defaults and meaning of Socle for VS Code's `socle.*` settings. */
@Service(Service.Level.APP)
@State(name = "SocleSettings", storages = [Storage("socle.xml")])
class SocleSettings : PersistentStateComponent<SocleSettings.State> {
    class State {
        var javaPath: String = ""
        var serverJar: String = ""
        var waveFlag: String = "emitVcd"
        var firtoolOpts: String = "-preserve-values=named"
        var surferCommand: String = "surfer"
        var waveViewer: String = "surfer"
        var waveViewerCommand: String = ""
        var wavesFollowSelection: Boolean = true
        var wavesShowClockReset: Boolean = false
        var wavesFoldBundles: Boolean = false
        var wavesShowWires: Boolean = true
        var wavesColorByDirection: Boolean = true
        var wavesFollowDelayMs: Int = 150
        var followCursor: Boolean = true
        var schematicColorizeComponents: Boolean = false
        var schematicColorizeWires: Boolean = false
        var schematicDensity: String = "comfortable"
        var schematicLabelSize: String = "normal"
        var schematicAutoOptimize: Boolean = true
        var schematicHintBar: Boolean = true
        var schematicShowLayers: Boolean = false
        var schematicHide: MutableList<String> = mutableListOf()

        /** The settings the language server reads (all but the client's own `javaPath` and `serverJar`). */
        fun toServerSettings(): Map<String, Any> = linkedMapOf(
            "waveFlag" to waveFlag,
            "firtoolOpts" to firtoolOpts,
            "surferCommand" to surferCommand,
            "waveViewer" to waveViewer,
            "waveViewerCommand" to waveViewerCommand,
            "wavesFollowSelection" to wavesFollowSelection,
            "wavesShowClockReset" to wavesShowClockReset,
            "wavesFoldBundles" to wavesFoldBundles,
            "wavesShowWires" to wavesShowWires,
            "wavesColorByDirection" to wavesColorByDirection,
            "wavesFollowDelayMs" to wavesFollowDelayMs,
            "followCursor" to followCursor,
            "schematicColorizeComponents" to schematicColorizeComponents,
            "schematicColorizeWires" to schematicColorizeWires,
            "schematicDensity" to schematicDensity,
            "schematicLabelSize" to schematicLabelSize,
            "schematicAutoOptimize" to schematicAutoOptimize,
            "schematicHintBar" to schematicHintBar,
            "schematicShowLayers" to schematicShowLayers,
            "schematicHide" to schematicHide.toList(),
        )
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
    }

    companion object {
        fun getInstance(): SocleSettings = ApplicationManager.getApplication().getService(SocleSettings::class.java)
    }
}

/** Fired on the application bus after the settings page applies a change. */
fun interface SocleSettingsListener {
    fun settingsChanged(state: SocleSettings.State)

    companion object {
        @JvmField
        val TOPIC: Topic<SocleSettingsListener> = Topic.create("Socle settings", SocleSettingsListener::class.java)
    }
}
