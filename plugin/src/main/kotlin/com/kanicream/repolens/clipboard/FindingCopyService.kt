package com.kanicream.repolens.clipboard

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.kanicream.repolens.format.AiCopyRequest
import com.kanicream.repolens.format.CodeSnippetBuilder
import com.kanicream.repolens.format.CopyItem
import com.kanicream.repolens.format.MarkdownAiFormatter
import com.kanicream.repolens.format.PlainTextFormatter
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

/** The three clipboard exports offered by the tool window. */
enum class CopyStyle(val actionName: String) {
    /** Plain one-line-per-finding summary, no code. */
    RESULT("Copy"),

    /** The summary plus each finding's bounded code snippet. */
    WITH_CODE("Copy with Code"),

    /** Markdown structured for pasting into an external AI chat. */
    FOR_AI("Copy for AI"),
}

/**
 * Formats selected findings and puts them on the system clipboard. No AI API is invoked
 * and nothing leaves the machine; where the user pastes the text is out of Repo Lens'
 * responsibility. Snippets are read in the background under short read actions and are
 * always bounded by the copy settings.
 */
@Service(Service.Level.PROJECT)
class FindingCopyService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    /** Copies [findings] in the given [style]; [onCopied] runs on the EDT afterwards. */
    fun copy(
        findings: List<Finding>,
        style: CopyStyle,
        scopeName: String,
        onCopied: () -> Unit = {},
    ) {
        if (findings.isEmpty()) return
        val copySettings = RepoLensSettings.getInstance(project).snapshot().copy
        coroutineScope.launch {
            val items = findings.map { finding ->
                val snippet = if (style == CopyStyle.RESULT) {
                    null
                } else {
                    loadFileLines(finding.location.filePath)?.let {
                        CodeSnippetBuilder.build(it, finding.location, copySettings)
                    }
                }
                CopyItem(finding, snippet)
            }
            val text = when (style) {
                CopyStyle.RESULT -> PlainTextFormatter.formatResult(items)
                CopyStyle.WITH_CODE -> PlainTextFormatter.formatWithCode(items)
                CopyStyle.FOR_AI -> MarkdownAiFormatter.format(
                    AiCopyRequest(projectName = project.name, scopeName = scopeName, items = items),
                )
            }
            withContext(Dispatchers.EDT) {
                CopyPasteManager.getInstance().setContents(StringSelection(text))
                onCopied()
            }
        }
    }

    private suspend fun loadFileLines(path: String): List<String>? = readAction {
        ProjectPaths.resolve(project, path)?.let(VfsText::load)
    }?.let(TextLines::split)

    companion object {
        fun getInstance(project: Project): FindingCopyService = project.service()
    }
}
