package com.kanicream.repolens.platform

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.model.AnalysisScopeType

/**
 * Turns the scope the user picked into concrete entry points.
 *
 * Called on the EDT, because "current file" and "selected files" describe UI state that
 * must be read at the moment the user starts the run — not later, on a background
 * thread, when the selection may already have moved.
 */
internal object ScopeResolver {

    fun resolve(
        project: Project,
        scopeType: AnalysisScopeType,
        selectedFiles: List<VirtualFile>,
    ): ScopeResolution = when (scopeType) {
        AnalysisScopeType.PROJECT -> ScopeResolution.Resolved(ResolvedScope.WholeProject)

        AnalysisScopeType.CURRENT_FILE -> currentFile(project)
            ?.let { ScopeResolution.Resolved(ResolvedScope.ExplicitFiles(listOf(it))) }
            ?: ScopeResolution.Unavailable("No file is open in the editor")

        AnalysisScopeType.MODULE -> currentFile(project)
            ?.let { ScopeResolution.Resolved(ResolvedScope.ContainingModule(it)) }
            ?: ScopeResolution.Unavailable("Open a file to analyze the module that contains it")

        AnalysisScopeType.SELECTED_FILES -> selectedFiles.filter { it.isValid }
            .takeIf { it.isNotEmpty() }
            ?.let { ScopeResolution.Resolved(ResolvedScope.ExplicitFiles(it)) }
            ?: ScopeResolution.Unavailable(
                "Select files in the Project View and choose Analyze with Repo Lens",
            )
    }

    private fun currentFile(project: Project): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.takeIf { it.isValid }
}
