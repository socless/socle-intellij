package socle.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.xmlb.XmlSerializerUtil

/** Settings | Tools | Socle: the Socle for VS Code settings, with the same names and meaning. */
class SocleConfigurable : BoundSearchableConfigurable("Socle", "socle.settings") {
    private val settings get() = SocleSettings.getInstance()

    /** The page edits a copy; apply copies it back and tells the listeners. */
    private val edited = SocleSettings.State()

    override fun createPanel(): DialogPanel {
        XmlSerializerUtil.copyBean(settings.state, edited)
        edited.schematicHide = settings.state.schematicHide.toMutableList()
        return panel {
            group("Language Server") {
                row("Java path:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle("Java Executable"))
                        .bindText(edited::javaPath).align(AlignX.FILL)
                        .comment("Java 21+ executable that runs the Socle language server. Empty: \$JAVA_HOME/bin/java, else java on the PATH.")
                }
                row("Server jar:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle("Socle Language Server Jar"))
                        .bindText(edited::serverJar).align(AlignX.FILL)
                        .comment("Socle language server jar. Empty: the one bundled with the plugin.")
                }
            }
            group("Simulation and Waveforms") {
                row("Wave flag:") {
                    textField().bindText(edited::waveFlag)
                        .comment("ChiselSim option that turns waveforms on for Simulate, passed to ScalaTest as -D&lt;flag&gt;=1 (emitVcd is ChiselSim's; a project may define its own, such as emitFst).")
                }
                row("Firtool options:") {
                    textField().bindText(edited::firtoolOpts).align(AlignX.FILL)
                        .comment("firtool options Simulate compiles the design with, passed to ScalaTest as -DfirtoolOpts=&lt;value&gt; (several separated by spaces; ChiselSim adds them to the test's own). The default keeps the ports and instances firtool would remove, so they are in the trace. Empty: none.")
                }
                row("Wave viewer:") {
                    comboBox(listOf("surfer", "surfer-panel", "custom")).bindItem({ edited.waveViewer }, { edited.waveViewer = it ?: "surfer" })
                        .comment("Where Simulate shows the trace: surfer (the external Surfer app), surfer-panel (Surfer embedded in the Socle panel), or custom (the custom viewer command).")
                }
                row("Surfer command:") {
                    textField().bindText(edited::surferCommand).align(AlignX.FILL)
                        .comment("The Surfer executable that Simulate starts on the trace (run as &lt;command&gt; --wcp-initiate &lt;port&gt; &lt;trace&gt;, or &lt;command&gt; &lt;trace&gt; for a Surfer without --wcp-initiate).")
                }
                row("Custom viewer command:") {
                    textField().bindText(edited::waveViewerCommand).align(AlignX.FILL)
                        .comment("Command line for the custom viewer, split on whitespace (no quoting, no shell); {file} is replaced by the trace path, for example: gtkwave {file}.")
                }
                row {
                    checkBox("Waves follow the selection").bindSelected(edited::wavesFollowSelection)
                        .comment("Surfer viewers only: the module selected in the Socle panel decides which signals Surfer shows, and a smaller selection moves its focus.")
                }
                row {
                    checkBox("Show clock and reset first").bindSelected(edited::wavesShowClockReset)
                        .comment("Surfer viewers only: list clock and reset first in the signal list.")
                }
                row {
                    checkBox("Fold bundles").bindSelected(edited::wavesFoldBundles)
                        .comment("surfer-panel only (the Surfer app's list has no groups): start bundle sub-groups folded in the signal list.")
                }
                row {
                    checkBox("Show wires").bindSelected(edited::wavesShowWires)
                        .comment("Surfer viewers only: include a Wires group (the module's wires and named nodes) in the signal list.")
                }
                row {
                    checkBox("Colour by direction").bindSelected(edited::wavesColorByDirection)
                        .comment("Surfer viewers only: colour signals by direction (inputs blue, outputs green); otherwise all ports are blue.")
                }
                row("Follow delay (ms):") {
                    spinner(0..60_000, 10).bindIntValue(edited::wavesFollowDelayMs)
                        .comment("Surfer viewers only: delay in milliseconds after a selection change before Surfer is updated.")
                }
            }
            group("Schematic") {
                row {
                    checkBox("Follow the cursor").bindSelected(edited::followCursor)
                        .comment("The class under the editor's cursor lights its boxes in the schematic and dims the rest. The panel's Focus chip turns it off until the cursor moves to another class.")
                }
                row {
                    checkBox("Colour components").bindSelected(edited::schematicColorizeComponents)
                        .comment("Colour the boxes by kind (instances, registers, operators, memories, state machines, logic blocks).")
                }
                row {
                    checkBox("Colour wires").bindSelected(edited::schematicColorizeWires)
                        .comment("Colour the wires by bundle type.")
                }
                row("Density:") {
                    comboBox(listOf("compact", "comfortable", "comfy")).bindItem({ edited.schematicDensity }, { edited.schematicDensity = it ?: "comfortable" })
                        .comment("How tight the panel's chrome is.")
                }
                row("Label size:") {
                    comboBox(listOf("normal", "large", "xl")).bindItem({ edited.schematicLabelSize }, { edited.schematicLabelSize = it ?: "normal" })
                        .comment("The size of port labels and box names, for reading a zoomed-out view.")
                }
                row {
                    checkBox("Auto optimize").bindSelected(edited::schematicAutoOptimize)
                        .comment("Lay every view out with the optimize pass (fewer crossings, in the background).")
                }
                row {
                    checkBox("Hint bar").bindSelected(edited::schematicHintBar)
                        .comment("The hint bar under the schematic, which says what the mouse does on what is under it.")
                }
                row {
                    checkBox("Show verification layers").bindSelected(edited::schematicShowLayers)
                        .comment("Draw verification layers (asserts, assumes, covers).")
                }
                row("Hide:") {
                    for ((kind, label) in HIDE_KINDS) {
                        checkBox(label).bindSelected(
                            { kind in edited.schematicHide },
                            { on -> if (on) { if (kind !in edited.schematicHide) edited.schematicHide.add(kind) } else edited.schematicHide.remove(kind) },
                        )
                    }
                }.rowComment("Node kinds to leave out of the schematic.")
            }
        }
    }

    override fun apply() {
        super.apply()
        XmlSerializerUtil.copyBean(edited, settings.state)
        // copyBean shares the list instance; give the stored state its own.
        settings.state.schematicHide = edited.schematicHide.toMutableList()
        ApplicationManager.getApplication().messageBus.syncPublisher(SocleSettingsListener.TOPIC).settingsChanged(settings.state)
    }

    companion object {
        val HIDE_KINDS = listOf(
            "op" to "operators", "reg" to "registers", "const" to "constants",
            "mem" to "memories", "fsm" to "state machines", "block" to "logic blocks",
        )
    }
}
