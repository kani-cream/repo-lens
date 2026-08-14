package com.kanicream.repolens.platform

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.AnalyzedFile
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.scope.PathExclusions

/**
 * Platform adapter that resolves the Project scope to analyzable files.
 *
 * Only listing files happens here (a single read action over the project content
 * index); file content is loaded lazily per file so no long read lock is held while
 * analyzers run. Content iteration does not depend on smart mode, so Tier 0 analysis
 * also works while indexing.
 */
internal class ProjectAnalysisContext(
    private val project: Project,
    override val request: AnalysisRequest,
) : AnalysisContext {

    private val exclusions = PathExclusions(request.settings.excludePatterns)

    override suspend fun files(): List<AnalyzedFile> = readAction {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val result = mutableListOf<AnalyzedFile>()
        fileIndex.iterateContent { file ->
            ProgressManager.checkCanceled()
            if (!file.isDirectory && !file.fileType.isBinary) {
                val relativePath = ProjectPaths.relativePath(project, file)
                if (!exclusions.isExcluded(relativePath)) {
                    result += VfsAnalyzedFile(file, relativePath)
                }
            }
            true
        }
        result
    }
}
