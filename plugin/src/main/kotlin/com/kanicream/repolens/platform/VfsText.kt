package com.kanicream.repolens.platform

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/** Text access shared by the analysis context and the clipboard snippet loader. */
internal object VfsText {

    /**
     * Loads the current text of [file], preferring an open in-memory document so unsaved
     * editor changes are analyzed. Returns `null` for binary or unreadable files.
     * Must be called under a read action.
     */
    fun load(file: VirtualFile): String? {
        if (!file.isValid || file.fileType.isBinary) return null
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.text }
        return try {
            VfsUtilCore.loadText(file)
        } catch (e: IOException) {
            null
        }
    }
}
