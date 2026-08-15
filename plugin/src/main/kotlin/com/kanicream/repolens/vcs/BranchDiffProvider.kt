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
        /**
         * Files git does not track yet. They never appear in `git diff`, but a reviewer
         * of the branch would absolutely look at them; the caller counts their lines
         * (the whole file is the addition).
         */
        val untracked: List<VirtualFile> = emptyList(),
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

/**
 * Supplies Git history evidence. Implemented by the Git descriptor; both queries run on
 * a background thread and are bounded by the caller (a day window, a fixed file set).
 * `null` maps mean "history unavailable", which enrichment treats as pass-through.
 */
interface GitHistoryProvider {

    /** One repository-wide log query within [days]; per-path stats for the whole window. */
    fun repositoryHistory(project: Project, days: Int): Map<String, com.kanicream.repolens.enrich.FileHistory>?

    /** Blame for one file: 1-based line to committer time (epoch millis). */
    fun lineAges(project: Project, repositoryRelativePath: String): Map<Int, Long>?

    companion object {
        val EP_NAME: ExtensionPointName<GitHistoryProvider> =
            ExtensionPointName.create("com.kanicream.repolens.gitHistoryProvider")

        fun first(): GitHistoryProvider? = EP_NAME.extensionList.firstOrNull()
    }
}
