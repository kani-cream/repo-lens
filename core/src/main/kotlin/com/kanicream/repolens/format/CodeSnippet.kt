package com.kanicream.repolens.format

import com.kanicream.repolens.model.CopySettings
import com.kanicream.repolens.model.SourceLocation

/**
 * Code excerpt attached to an exported finding.
 *
 * [startLine] is the 1-based line number of the first included line; [omittedLineCount]
 * counts lines dropped by the [CopySettings.maxCodeLines] cap.
 */
data class CodeSnippet(
    val startLine: Int,
    val lines: List<String>,
    val omittedLineCount: Int,
) {
    val isTruncated: Boolean get() = omittedLineCount > 0
}

/** Builds bounded [CodeSnippet]s; never exports a file wholesale. */
object CodeSnippetBuilder {

    /**
     * Extracts the finding range plus [CopySettings.contextLines] of surrounding context
     * from [fileLines], truncating at [CopySettings.maxCodeLines].
     */
    fun build(fileLines: List<String>, location: SourceLocation, settings: CopySettings): CodeSnippet {
        val contextLines = settings.contextLines.coerceAtLeast(0)
        val first = (location.startLine - contextLines).coerceAtLeast(1)
        val last = (location.endLine + contextLines).coerceAtMost(fileLines.size)
        if (first > last) return CodeSnippet(startLine = first, lines = emptyList(), omittedLineCount = 0)

        val selected = fileLines.subList(first - 1, last)
        val maxCodeLines = settings.maxCodeLines
        return if (maxCodeLines in 1 until selected.size) {
            CodeSnippet(first, selected.take(maxCodeLines), selected.size - maxCodeLines)
        } else {
            CodeSnippet(first, selected.toList(), 0)
        }
    }
}
