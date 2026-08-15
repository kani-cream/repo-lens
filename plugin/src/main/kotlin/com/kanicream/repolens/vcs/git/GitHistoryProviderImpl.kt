package com.kanicream.repolens.vcs.git

import com.intellij.openapi.project.Project
import com.kanicream.repolens.enrich.FileHistory
import com.kanicream.repolens.vcs.GitHistoryProvider
import com.kanicream.repolens.vcs.GitLogParser
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * History through git commands: one bounded `git log --name-only` per analysis for the
 * whole repository, and `git blame --line-porcelain` per file that needs line ages.
 * Failures return null and enrichment silently passes findings through.
 */
internal class GitHistoryProviderImpl : GitHistoryProvider {

    override fun repositoryHistory(project: Project, days: Int): Map<String, FileHistory>? {
        val repository = firstRepository(project) ?: return null
        val output = git(
            project, repository, GitCommand.LOG,
            "--since=$days days ago", "--format=%x01%an%x02%ct", "--name-only", "--no-merges",
        ) ?: return null
        return GitLogParser.parse(output)
    }

    override fun lineAges(project: Project, repositoryRelativePath: String): Map<Int, Long>? {
        val repository = firstRepository(project) ?: return null
        val output = git(
            project, repository, GitCommand.BLAME,
            "--line-porcelain", "--", repositoryRelativePath,
        ) ?: return null
        return GitLogParser.parseBlame(output)
    }

    private fun firstRepository(project: Project): GitRepository? =
        GitRepositoryManager.getInstance(project).repositories.firstOrNull()

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
}
