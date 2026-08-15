package com.kanicream.repolens.platform

import com.intellij.openapi.vfs.VirtualFile

/**
 * What an analysis run should walk, captured on the EDT before the run starts.
 *
 * Only the entry points are captured here; expanding them into a file list needs a read
 * action and therefore happens on the background thread that runs the analysis.
 */
internal sealed interface ResolvedScope {

    /** Everything under the project content roots. */
    data object WholeProject : ResolvedScope

    /** The module owning [anchor], resolved when the run starts. */
    data class ContainingModule(val anchor: VirtualFile) : ResolvedScope

    /**
     * Files and directories the user pointed at explicitly. Directories are expanded
     * recursively; the files themselves are analyzed even when an exclusion rule would
     * normally hide them, because an explicit pick outranks a default filter.
     */
    data class ExplicitFiles(val files: List<VirtualFile>) : ResolvedScope

    /**
     * Files computed for the user rather than picked by them, such as the VCS changes.
     * Exclusion rules apply, so a regenerated lock file does not become a finding.
     */
    data class DerivedFiles(val files: List<VirtualFile>) : ResolvedScope

    /**
     * Diff against [baseBranchSetting] (blank = auto-detect). Resolved lazily on the
     * background thread, because computing it runs git commands.
     */
    data class BranchDiff(val baseBranchSetting: String) : ResolvedScope
}

/** Outcome of turning a scope choice into something analyzable. */
internal sealed interface ScopeResolution {
    data class Resolved(val scope: ResolvedScope) : ScopeResolution

    /** The scope cannot run right now; [reason] is shown to the user as-is. */
    data class Unavailable(val reason: String) : ScopeResolution
}
