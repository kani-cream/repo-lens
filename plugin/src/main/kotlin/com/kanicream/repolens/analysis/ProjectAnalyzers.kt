package com.kanicream.repolens.analysis

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project

/**
 * All analyzers available in this project: the built-in catalog plus any contributed by
 * optional language descriptors (e.g. Unused Candidate, which needs UAST). The analysis
 * service and the settings checkboxes both come here, so they cannot drift apart.
 */
object ProjectAnalyzers {

    private val EP_NAME: ProjectExtensionPointName<RepoLensAnalyzer> =
        ProjectExtensionPointName("com.kanicream.repolens.analyzer")

    fun all(project: Project): List<RepoLensAnalyzer> =
        DefaultAnalyzers.create() + EP_NAME.getExtensions(project)
}
