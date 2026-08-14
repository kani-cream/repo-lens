package com.kanicream.repolens.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * The only place that knows about VCS. Keeping it behind this facade means the scope
 * resolver and the analyzers stay independent of the version control API, and Branch
 * Diff can be added later without touching them.
 */
internal object VcsFacade {

    fun hasActiveVcs(project: Project): Boolean =
        ProjectLevelVcsManager.getInstance(project).hasActiveVcss()

    /**
     * Files that differ from the current revision, including untracked ones (docs/design.md
     * OD-04): for review purposes a newly added file is as interesting as a modified one.
     * Deleted files are dropped, since there is nothing left to analyze.
     */
    fun locallyChangedFiles(project: Project): List<VirtualFile> {
        val changeListManager = ChangeListManager.getInstance(project)
        val changed = changeListManager.affectedFiles
        val unversioned = changeListManager.unversionedFilesPaths.mapNotNull { it.virtualFile }
        return (changed + unversioned).filter { it.isValid && !it.isDirectory }.distinct()
    }
}
