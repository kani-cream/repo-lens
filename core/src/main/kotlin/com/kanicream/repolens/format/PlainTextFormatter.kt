package com.kanicream.repolens.format

/**
 * Renders findings as plain text for pasting into issues, chats, or notes.
 *
 * Copy Result produces one pipe-separated line per finding; Copy with Code appends each
 * finding's bounded snippet. Same non-goals as the Markdown formatter: no analysis, no
 * side effects, no external calls.
 */
object PlainTextFormatter {

    /** One line per finding: `Warning | Large Method | path:5-96 | symbol | 92 / 80 | reason`. */
    fun formatResult(items: List<CopyItem>): String =
        items.joinToString("\n") { resultLine(it) } + "\n"

    /** [formatResult] plus each finding's code snippet, findings separated by blank lines. */
    fun formatWithCode(items: List<CopyItem>): String = buildString {
        items.forEachIndexed { index, item ->
            if (index > 0) appendLine()
            appendLine(resultLine(item))
            item.snippet?.takeIf { it.lines.isNotEmpty() }?.let { snippet ->
                var line = snippet.startLine
                snippet.lines.forEach { text ->
                    appendLine("  ${line.toString().padStart(4)} | $text")
                    line++
                }
                if (snippet.isTruncated) {
                    appendLine("       | ... omitted ${snippet.omittedLineCount} lines ...")
                }
            }
        }
    }

    private fun resultLine(item: CopyItem): String {
        val finding = item.finding
        val fields = mutableListOf(
            finding.severity.displayName,
            finding.checkName,
            "${finding.location.filePath}:${finding.location.lineRangeText}",
        )
        finding.symbol?.let { fields += it.displayName }
        finding.measuredValue?.let { value ->
            val threshold = finding.threshold?.let { " / ${MetricFormat.format(it)}" }.orEmpty()
            fields += MetricFormat.format(value) + threshold
        }
        fields += finding.message
        return fields.joinToString(" | ")
    }
}
