package com.kanicream.repolens.vcs.git

import com.intellij.openapi.project.Project
import com.kanicream.repolens.vcs.BranchDiffProvider
import com.kanicream.repolens.vcs.BranchDiffResult
import com.kanicream.repolens.vcs.GitDiffParser
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Branch Diff through git: merge-base against the configured (or auto-detected) base
 * branch, then one numstat and one name-status diff including uncommitted work — the
 * full "what would a reviewer of this branch look at" set.
 *
 * Loaded via repo-lens-git.xml only when the Git plugin is enabled. Every failure is a
 * reasoned Unavailable, never an exception: no repository, no recognizable base branch,
 * or git itself failing are all normal states the user can fix.
 */
internal class GitBranchDiffProvider : BranchDiffProvider {

    override fun resolve(project: Project, baseBranchSetting: String): BranchDiffResult {
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
            ?: return BranchDiffResult.Unavailable("No Git repository is configured for this project")

        val base = resolveBase(repository, baseBranchSetting.trim())
            ?: return BranchDiffResult.Unavailable(
                if (baseBranchSetting.isBlank()) {
                    "No base branch found: none of ${AUTO_BASE_CANDIDATES.joinToString(", ")} exist. " +
                        "Set one in Settings | Tools | Repo Lens."
                } else {
                    "Base branch '${baseBranchSetting.trim()}' does not exist in this repository"
                },
            )

        val mergeBase = git(project, repository, GitCommand.MERGE_BASE, base, "HEAD")
            ?: return BranchDiffResult.Unavailable("Cannot determine the merge base with '$base'")
        val mergeBaseHash = mergeBase.firstOrNull()?.trim()
            ?: return BranchDiffResult.Unavailable("Cannot determine the merge base with '$base'")

        val numstat = git(project, repository, GitCommand.DIFF, "--numstat", "--find-renames", mergeBaseHash)
            ?: return BranchDiffResult.Unavailable("git diff failed against '$base'")
        val nameStatus = git(project, repository, GitCommand.DIFF, "--name-status", "--find-renames", mergeBaseHash)
            ?: return BranchDiffResult.Unavailable("git diff failed against '$base'")

        val changes = GitDiffParser.combine(
            GitDiffParser.parseNumstat(numstat),
            GitDiffParser.parseNameStatus(nameStatus),
        )
        val files = changes.mapNotNull { (path, info) ->
            repository.root.findFileByRelativePath(path)?.let { it to info }
        }

        // git diff never lists files git does not track yet, but a reviewer of this
        // branch would look at them - the same reasoning as Local Changes (OD-04).
        val untracked = git(project, repository, GitCommand.LS_FILES, "--others", "--exclude-standard")
            ?.mapNotNull { path -> repository.root.findFileByRelativePath(path.trim()) }
            ?: emptyList()

        return BranchDiffResult.Success(baseDescription = base, files = files, untracked = untracked)
    }

    private fun resolveBase(repository: GitRepository, configured: String): String? {
        val known = buildSet {
            repository.branches.localBranches.forEach { add(it.name) }
            repository.branches.remoteBranches.forEach { add(it.name) }
        }
        if (configured.isNotEmpty()) return configured.takeIf { it in known }
        return AUTO_BASE_CANDIDATES.firstOrNull { it in known }
    }

    private fun git(
        project: Project,
        repository: GitRepository,
        command: GitCommand,
        vararg parameters: String,
    ): List<String>? {
        val handler = GitLineHandler(project, repository.root, command)
        handler.addParameters(*parameters)
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.output else null
    }

    companion object {
        private val AUTO_BASE_CANDIDATES =
            listOf("origin/main", "origin/master", "main", "master")
    }
}
