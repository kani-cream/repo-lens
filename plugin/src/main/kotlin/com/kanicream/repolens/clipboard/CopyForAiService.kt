package com.kanicream.repolens.clipboard

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.kanicream.repolens.format.AiCopyItem
import com.kanicream.repolens.format.AiCopyRequest
import com.kanicream.repolens.format.CodeSnippetBuilder
import com.kanicream.repolens.format.MarkdownAiFormatter
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.platform.ProjectPaths
import com.kanicream.repolens.platform.VfsText
import com.kanicream.repolens.settings.RepoLensSettings
import com.kanicream.repolens.text.TextLines
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds the "Copy for AI" Markdown for the selected findings and puts it on the system
 * clipboard. No AI API is invoked and nothing leaves the machine; where the user pastes
 * the text is out of Repo Lens' responsibility.
 */
@Service(Service.Level.PROJECT)
class CopyForAiService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    /**
     * Reads code snippets in the background (bounded by the copy settings), formats the
     * findings as Markdown, and copies the result. [onCopied] runs on the EDT afterwards.
     */
    fun copyForAi(findings: List<Finding>, scopeName: String, onCopied: () -> Unit = {}) {
        if (findings.isEmpty()) return
        val copySettings = RepoLensSettings.getInstance(project).snapshot().copy
        coroutineScope.launch {
            val items = findings.map { finding ->
                val fileLines = loadFileLines(finding.location.filePath)
                val snippet = fileLines?.let {
                    CodeSnippetBuilder.build(it, finding.location, copySettings)
                }
                AiCopyItem(finding, snippet)
            }
            val markdown = MarkdownAiFormatter.format(
                AiCopyRequest(projectName = project.name, scopeName = scopeName, items = items),
            )
            withContext(Dispatchers.EDT) {
                CopyPasteManager.getInstance().setContents(StringSelection(markdown))
                onCopied()
            }
        }
    }

    private suspend fun loadFileLines(path: String): List<String>? = readAction {
        ProjectPaths.resolve(project, path)?.let(VfsText::load)
    }?.let(TextLines::split)

    companion object {
        fun getInstance(project: Project): CopyForAiService = project.service()
    }
}
