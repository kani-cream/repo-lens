package com.kanicream.repolens.format

import com.kanicream.repolens.analysis.structure.CircularDependencyAnalyzer
import com.kanicream.repolens.analysis.tier0.LargeDiffAnalyzer
import com.kanicream.repolens.enrich.GitMetadataKeys
import com.kanicream.repolens.model.Finding

/** One finding plus its optional code excerpt, shared by all copy formats. */
data class CopyItem(
    val finding: Finding,
    val snippet: CodeSnippet?,
)

/** Input for [MarkdownAiFormatter]; carries no IDE types. */
data class AiCopyRequest(
    val projectName: String,
    val scopeName: String,
    val items: List<CopyItem>,
)

/**
 * Turns findings into Markdown suitable for pasting into any external AI chat.
 *
 * This formatter never calls an AI API, never talks to the network, and contains no
 * provider-specific behavior; producing text is its entire job (docs/design.md §9).
 */
object MarkdownAiFormatter {

    const val REVIEW_PROMPT: String =
        "Please review whether this finding represents a meaningful design or maintainability concern."

    fun format(request: AiCopyRequest): String {
        require(request.items.isNotEmpty()) { "At least one finding is required" }
        val body = if (request.items.size == 1) {
            formatSingle(request.items.first())
        } else {
            formatMultiple(request)
        }
        return body + "\n" + REVIEW_PROMPT + "\n"
    }

    private fun formatSingle(item: CopyItem): String = buildString {
        appendLine("## Repo Lens Finding")
        appendLine()
        appendFindingBody(item)
    }

    private fun formatMultiple(request: AiCopyRequest): String = buildString {
        appendLine("# Repo Lens Review Context")
        appendLine()
        appendLine("Project: ${request.projectName}")
        appendLine("Scope: ${request.scopeName}")
        appendLine("Findings: ${request.items.size}")
        appendLine()
        request.items.forEachIndexed { index, item ->
            appendLine("## ${index + 1}. ${item.finding.checkName}")
            appendLine()
            appendFindingBody(item)
        }
    }

    private fun StringBuilder.appendFindingBody(item: CopyItem) {
        val finding = item.finding
        appendLine("- Issue: ${finding.checkName}")
        appendLine("- Severity: ${finding.severity.displayName}")
        appendLine("- File: `${finding.location.filePath}`")
        finding.symbol?.let { appendLine("- Symbol: `${it.displayName}`") }
        appendLine("- Location: `${finding.location.lineRangeText}`")
        finding.measuredValue?.let { appendLine("- Value: ${MetricFormat.format(it)}") }
        finding.threshold?.let { appendLine("- Threshold: ${MetricFormat.format(it)}") }
        finding.confidence?.let { appendLine("- Confidence: ${it.displayName}") }
        finding.metadata[LargeDiffAnalyzer.METADATA_ADDED]?.let { added ->
            val deleted = finding.metadata[LargeDiffAnalyzer.METADATA_DELETED] ?: "0"
            val status = finding.metadata[LargeDiffAnalyzer.METADATA_STATUS] ?: "changed"
            appendLine("- Diff: +$added −$deleted ($status)")
        }
        finding.metadata[GitMetadataKeys.COMMITS]?.let { commits ->
            val authors = finding.metadata[GitMetadataKeys.AUTHORS] ?: "?"
            val window = finding.metadata[GitMetadataKeys.WINDOW_DAYS] ?: "?"
            appendLine("- Git: $commits commit(s) by $authors author(s) in the last $window days")
        }
        finding.metadata[GitMetadataKeys.TODO_AGE_DAYS]?.let { age ->
            val longLived =
                if (finding.metadata[GitMetadataKeys.TODO_LONG_LIVED] != null) " (long-lived)" else ""
            appendLine("- Marker age: $age day(s)$longLived")
        }
        appendLine()
        appendLine("### Reason")
        appendLine()
        appendLine(finding.message)
        appendLine()
        finding.metadata[CircularDependencyAnalyzer.METADATA_EVIDENCE]?.let { evidence ->
            appendLine("### Dependency cycle")
            appendLine()
            evidence.lines().forEach { appendLine("- $it") }
            appendLine()
        }
        item.snippet?.let { appendSnippet(finding.location.filePath, it) }
    }

    private fun StringBuilder.appendSnippet(filePath: String, snippet: CodeSnippet) {
        if (snippet.lines.isEmpty()) return
        appendLine("### Code")
        appendLine()
        val fence = fenceFor(snippet)
        appendLine(fence + languageHint(filePath))
        snippet.lines.forEach { appendLine(it) }
        if (snippet.isTruncated) {
            appendLine("... omitted ${snippet.omittedLineCount} lines ...")
        }
        appendLine(fence)
        appendLine()
    }

    /** Uses a longer fence when the snippet itself contains backtick fences. */
    private fun fenceFor(snippet: CodeSnippet): String {
        val longestRun = snippet.lines.maxOfOrNull { line ->
            Regex("`+").findAll(line).maxOfOrNull { it.value.length } ?: 0
        } ?: 0
        return "`".repeat(maxOf(3, longestRun + 1))
    }

    private fun languageHint(filePath: String): String =
        when (filePath.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "go" -> "go"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "xml" -> "xml"
            "json" -> "json"
            "yml", "yaml" -> "yaml"
            "md" -> "markdown"
            else -> ""
        }
}
