package com.kanicream.repolens.vcs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.model.FileChangeInfo

/** Outcome of resolving the Branch Diff scope. */
sealed interface BranchDiffResult {
    /** [baseDescription] names what the diff was taken against, for the status line. */
    data class Success(
        val baseDescription: String,
        val files: List<Pair<VirtualFile, FileChangeInfo>>,
    ) : BranchDiffResult

    /** Diff cannot be computed right now; [reason] is shown to the user as-is. */
    data class Unavailable(val reason: String) : BranchDiffResult
}

/**
 * Computes the changed files against a base branch. Implemented by the Git descriptor
 * (loaded only when the Git plugin is enabled), so nothing here names a VCS. Called on a
 * background thread — resolving a diff runs external commands.
 */
interface BranchDiffProvider {

    /** [baseBranchSetting] is the user's configured base; blank means auto-detect. */
    fun resolve(project: Project, baseBranchSetting: String): BranchDiffResult

    companion object {
        val EP_NAME: ExtensionPointName<BranchDiffProvider> =
            ExtensionPointName.create("com.kanicream.repolens.branchDiffProvider")

        fun first(): BranchDiffProvider? = EP_NAME.extensionList.firstOrNull()
    }
}

/**
 * Thrown when a scope that resolved optimistically turns out to be unusable once the
 * background work runs (no repository, base branch missing). The analysis service turns
 * it into the user-facing failure message without logging a stack trace.
 */
class ScopeUnavailableException(reason: String) : Exception(reason)
