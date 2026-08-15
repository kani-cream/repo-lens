package com.kanicream.repolens.structure

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.structure.CodeStructure

/**
 * Supplies the structural view of a file for one family of languages.
 *
 * This is the seam language providers plug into. Core registers no implementation of its
 * own; each provider (UAST for Java/Kotlin, Go, JavaScript/TypeScript) is contributed by
 * an optional descriptor that only loads when its language plugin is present.
 * Implementations are called inside a read action.
 */
internal interface CodeStructureProvider {

    /** Cheap check so unsupported files never pay for a parse attempt. */
    fun supports(project: Project, file: VirtualFile): Boolean

    /** Returns the declarations in [file], or `null` when it cannot be parsed after all. */
    fun structure(project: Project, file: VirtualFile): CodeStructure?

    companion object {
        val EP_NAME: ExtensionPointName<CodeStructureProvider> =
            ExtensionPointName.create("com.kanicream.repolens.codeStructureProvider")

        /** First provider that can handle [file], or `null` when none can. */
        fun structureOf(project: Project, file: VirtualFile): CodeStructure? =
            EP_NAME.extensionList
                .firstOrNull { it.supports(project, file) }
                ?.structure(project, file)
    }
}
