package com.kanicream.repolens.navigation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.platform.ProjectPaths

/**
 * Opens the editor at a finding's location. Navigation lives here and only here:
 * analyzers and formatters never move the caret.
 */
@Service(Service.Level.PROJECT)
class FindingNavigator(private val project: Project) {

    /**
     * Navigates to the finding's file and start line. Returns `false` when the file can
     * no longer be resolved (deleted or renamed since the analysis). Call on the EDT.
     */
    fun navigate(finding: Finding): Boolean {
        val file = ProjectPaths.resolve(project, finding.location.filePath) ?: return false
        if (!file.isValid) return false
        val line = finding.location.startLine - 1
        OpenFileDescriptor(project, file, line, 0).navigate(true)
        return true
    }

    companion object {
        fun getInstance(project: Project): FindingNavigator = project.service()
    }
}
