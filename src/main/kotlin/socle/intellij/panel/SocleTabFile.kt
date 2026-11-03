package socle.intellij.panel

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile

/** The virtual file behind a Socle tab (the design panel or the Surfer tab); its title can change. */
class SocleTabFile(val kind: Kind, @Volatile var title: String) : LightVirtualFile(title) {
    enum class Kind { PANEL, SURFER }

    override fun getPresentableName(): String = title
    override fun getFileType(): FileType = PlainTextFileType.INSTANCE
    override fun isWritable(): Boolean = false
}
