package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * RL-T001: flags lines containing a configured review marker (TODO / FIXME by default).
 *
 * Tier 0 analyzer: matches markers as whole words, case-insensitively, in any text file.
 * This deliberately scans plain text instead of parsing comment syntax per language, so
 * it works even where no parser is available; a future refinement may delegate to the
 * platform's TODO index where that is safely available.
 */
class TodoMarkerAnalyzer : RepoLensAnalyzer {

    override val id: String = ID
    override val checkName: String = "TODO / FIXME"

    override fun supports(context: AnalysisContext): Boolean =
        context.settings.todoMarkers.any { it.isNotBlank() }

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val markers = context.settings.todoMarkers.map { it.trim() }.filter { it.isNotEmpty() }
        if (markers.isEmpty()) return emptyList()
        val pattern = Regex(
            "\\b(${markers.joinToString("|") { Regex.escape(it) }})\\b",
            RegexOption.IGNORE_CASE,
        )

        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val lines = file.lines() ?: continue
            lines.forEachIndexed { index, line ->
                val match = pattern.find(line) ?: return@forEachIndexed
                val marker = canonicalMarker(markers, match.groupValues[1])
                val lineNumber = index + 1
                val location = SourceLocation(file.relativePath, lineNumber, lineNumber)
                findings += Finding(
                    id = Finding.stableId(id, location),
                    analyzerId = id,
                    severity = Severity.INFO,
                    checkName = checkName,
                    message = "Line $lineNumber contains a $marker marker.",
                    location = location,
                    metadata = mapOf(
                        METADATA_MARKER to marker,
                        METADATA_TEXT to line.substring(match.range.first).trim(),
                    ),
                )
            }
        }
        return findings
    }

    private fun canonicalMarker(markers: List<String>, matched: String): String =
        markers.firstOrNull { it.equals(matched, ignoreCase = true) } ?: matched

    companion object {
        const val ID: String = "RL-T001"

        /** Metadata key: which configured marker matched (e.g. `FIXME`). */
        const val METADATA_MARKER: String = "todo.marker"

        /** Metadata key: the matched line text starting at the marker, trimmed. */
        const val METADATA_TEXT: String = "todo.text"
    }
}
