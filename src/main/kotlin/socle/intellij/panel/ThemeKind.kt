package socle.intellij.panel

import com.intellij.ide.ui.LafManager
import com.intellij.ui.JBColor

/** The UI theme kind for the IDE's current look and feel: `hc`, `dark` or `light`. */
fun currentThemeKind(): String {
    val name = try { LafManager.getInstance().currentUIThemeLookAndFeel?.name ?: "" } catch (_: Exception) { "" }
    return when {
        name.contains("contrast", ignoreCase = true) -> "hc"
        !JBColor.isBright() -> "dark"
        else -> "light"
    }
}
