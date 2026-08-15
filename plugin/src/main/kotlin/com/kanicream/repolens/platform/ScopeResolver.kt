package com.kanicream.repolens.platform

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.vcs.BranchDiffProvider
import com.kanicream.repolens.vcs.VcsFacade

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
        baseBranchSetting: String = "",
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

        AnalysisScopeType.LOCAL_CHANGES -> localChanges(project)

        AnalysisScopeType.BRANCH_DIFF ->
            if (BranchDiffProvider.first() == null) {
                ScopeResolution.Unavailable("Branch Diff needs the Git plugin, which is not available")
            } else {
                // The actual git work happens on the background thread inside the run.
                ScopeResolution.Resolved(ResolvedScope.BranchDiff(baseBranchSetting))
            }
    }

    private fun localChanges(project: Project): ScopeResolution {
        if (!VcsFacade.hasActiveVcs(project)) {
            return ScopeResolution.Unavailable("No version control system is configured for this project")
        }
        val changed = VcsFacade.locallyChangedFiles(project)
        return if (changed.isEmpty()) {
            ScopeResolution.Unavailable("No local changes to analyze")
        } else {
            ScopeResolution.Resolved(ResolvedScope.DerivedFiles(changed))
        }
    }

    private fun currentFile(project: Project): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.takeIf { it.isValid }
}
