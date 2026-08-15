package com.kanicream.repolens.vcs.git

import com.intellij.openapi.diagnostic.logger
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

    /**
     * The window-bounded log only changes when HEAD moves, so one cached entry keyed by
     * (root, HEAD, window) turns repeat analyses from a repository-wide log walk into a
     * map lookup. Single entry on purpose: no growth, trivially correct.
     */
    private data class CacheKey(val rootPath: String, val head: String, val days: Int)

    @Volatile
    private var cache: Pair<CacheKey, Map<String, FileHistory>>? = null

    override fun repositoryHistory(project: Project, days: Int): Map<String, FileHistory>? {
        val repository = firstRepository(project) ?: return null
        val head = repository.currentRevision
        if (head == null) {
            LOG.info("history cache bypass: currentRevision unavailable")
            return queryHistory(project, repository, days)
        }
        val key = CacheKey(repository.root.path, head, days)
        cache?.let { (cachedKey, value) ->
            if (cachedKey == key) {
                LOG.info("history cache hit (${value.size} paths)")
                return value
            }
        }
        val fresh = queryHistory(project, repository, days) ?: return null
        LOG.info("history cache miss: queried ${fresh.size} paths")
        cache = key to fresh
        return fresh
    }

    private fun queryHistory(project: Project, repository: GitRepository, days: Int): Map<String, FileHistory>? {
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

    companion object {
        private val LOG = logger<GitHistoryProviderImpl>()
    }
}
