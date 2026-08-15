package com.kanicream.repolens.platform

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.AnalyzedFile
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.ChangeStatus
import com.kanicream.repolens.model.FileChangeInfo
import com.kanicream.repolens.text.TextLines
import com.kanicream.repolens.scope.PathExclusions
import com.kanicream.repolens.vcs.BranchDiffProvider
import com.kanicream.repolens.vcs.BranchDiffResult
import com.kanicream.repolens.vcs.ScopeUnavailableException

/**
 * Platform adapter that turns a [ResolvedScope] into analyzable files.
 *
 * Only the file listing happens here, inside one read action; contents are read later,
 * per file, so no long read lock is held while analyzers run. Listing does not depend on
 * indexes, so Tier 0 analysis also works while the IDE is indexing.
 */
internal class VfsAnalysisContext(
    private val project: Project,
    override val request: AnalysisRequest,
    private val scope: ResolvedScope,
) : AnalysisContext {

    private val exclusions = PathExclusions(request.settings.excludePatterns)

    // Analyzers run sequentially within one analysis, so a plain field is a safe
    // per-run memo. Without it every analyzer repeats the full scope walk.
    private var cachedFiles: List<AnalyzedFile>? = null

    override suspend fun files(): List<AnalyzedFile> =
        cachedFiles ?: when (scope) {
            is ResolvedScope.BranchDiff -> branchDiffFiles(scope)
            else -> listFiles()
        }.also { cachedFiles = it }

    /**
     * Runs git off the EDT (we are on a background dispatcher here) and only then maps
     * paths under a read action. An unusable diff surfaces as a reasoned failure, not an
     * empty result that reads as "no changes".
     */
    private suspend fun branchDiffFiles(scope: ResolvedScope.BranchDiff): List<AnalyzedFile> {
        val provider = BranchDiffProvider.first()
            ?: throw ScopeUnavailableException("Branch Diff needs the Git plugin, which is not available")
        val success = when (val result = provider.resolve(project, scope.baseBranchSetting)) {
            is BranchDiffResult.Unavailable -> throw ScopeUnavailableException(result.reason)
            is BranchDiffResult.Success -> result
        }
        return readAction {
            val diffed = success.files.mapNotNull { (file, change) ->
                if (!file.isValid || file.isDirectory || file.fileType.isBinary) return@mapNotNull null
                val relativePath = ProjectPaths.relativePath(project, file)
                if (exclusions.isExcluded(relativePath)) return@mapNotNull null
                VfsAnalyzedFile(project, file, relativePath, change)
            }
            // The whole content of an untracked file is the addition.
            val untracked = success.untracked.mapNotNull { file ->
                if (!file.isValid || file.isDirectory || file.fileType.isBinary) return@mapNotNull null
                val relativePath = ProjectPaths.relativePath(project, file)
                if (exclusions.isExcluded(relativePath)) return@mapNotNull null
                val lineCount = VfsText.load(file)?.let(TextLines::physicalLineCount) ?: 0
                VfsAnalyzedFile(
                    project, file, relativePath,
                    FileChangeInfo(ChangeStatus.ADDED, addedLines = lineCount, deletedLines = 0),
                )
            }
            diffed + untracked
        }
    }

    private suspend fun listFiles(): List<AnalyzedFile> = readAction {
        val collector = FileCollector()
        when (scope) {
            is ResolvedScope.WholeProject ->
                ProjectFileIndex.getInstance(project).iterateContent(collector)

            is ResolvedScope.ContainingModule -> {
                val module = ProjectFileIndex.getInstance(project).getModuleForFile(scope.anchor)
                module?.let { ModuleRootManager.getInstance(it).fileIndex.iterateContent(collector) }
            }

            is ResolvedScope.ExplicitFiles -> scope.files.forEach { file ->
                if (file.isDirectory) {
                    collectRecursively(file, collector)
                } else {
                    // An explicit pick outranks the exclusion rules.
                    collector.add(file, applyExclusions = false)
                }
            }

            is ResolvedScope.DerivedFiles -> scope.files.forEach { file ->
                if (file.isDirectory) collectRecursively(file, collector) else collector.add(file)
            }

            is ResolvedScope.BranchDiff -> error("Branch Diff is resolved by branchDiffFiles")
        }
        collector.files
    }

    private fun collectRecursively(root: VirtualFile, collector: FileCollector) {
        VfsUtilCore.visitChildrenRecursively(
            root,
            object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    ProgressManager.checkCanceled()
                    if (!file.isDirectory) collector.add(file)
                    return true
                }
            },
        )
    }

    private inner class FileCollector : ContentIterator {
        val files = mutableListOf<AnalyzedFile>()

        override fun processFile(fileOrDir: VirtualFile): Boolean {
            ProgressManager.checkCanceled()
            if (!fileOrDir.isDirectory) add(fileOrDir)
            return true
        }

        fun add(file: VirtualFile, applyExclusions: Boolean = true) {
            if (file.fileType.isBinary) return
            val relativePath = ProjectPaths.relativePath(project, file)
            if (applyExclusions && exclusions.isExcluded(relativePath)) return
            files += VfsAnalyzedFile(project, file, relativePath)
        }
    }
}
