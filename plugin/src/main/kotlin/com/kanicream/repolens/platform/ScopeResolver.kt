package com.kanicream.repolens.platform

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.RepoLensBundle
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
            ?: ScopeResolution.Unavailable(RepoLensBundle.message("error.no.open.file"))

        AnalysisScopeType.MODULE -> currentFile(project)
            ?.let { ScopeResolution.Resolved(ResolvedScope.ContainingModule(it)) }
            ?: ScopeResolution.Unavailable(RepoLensBundle.message("error.no.file.for.module"))

        AnalysisScopeType.SELECTED_FILES -> selectedFiles.filter { it.isValid }
            .takeIf { it.isNotEmpty() }
            ?.let { ScopeResolution.Resolved(ResolvedScope.ExplicitFiles(it)) }
            ?: ScopeResolution.Unavailable(RepoLensBundle.message("error.no.selection"))

        AnalysisScopeType.LOCAL_CHANGES -> localChanges(project)

        AnalysisScopeType.BRANCH_DIFF ->
            if (BranchDiffProvider.first() == null) {
                ScopeResolution.Unavailable(RepoLensBundle.message("error.no.git.plugin"))
            } else {
                // The actual git work happens on the background thread inside the run.
                ScopeResolution.Resolved(ResolvedScope.BranchDiff(baseBranchSetting))
            }
    }

    private fun localChanges(project: Project): ScopeResolution {
        if (!VcsFacade.hasActiveVcs(project)) {
            return ScopeResolution.Unavailable(RepoLensBundle.message("error.no.vcs"))
        }
        val changed = VcsFacade.locallyChangedFiles(project)
        return if (changed.isEmpty()) {
            ScopeResolution.Unavailable(RepoLensBundle.message("error.no.local.changes"))
        } else {
            ScopeResolution.Resolved(ResolvedScope.DerivedFiles(changed))
        }
    }

    private fun currentFile(project: Project): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.takeIf { it.isValid }
}
