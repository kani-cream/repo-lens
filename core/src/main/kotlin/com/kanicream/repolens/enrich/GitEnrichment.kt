package com.kanicream.repolens.enrich

import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.model.Finding

/** Per-file history within the configured window. */
data class FileHistory(
    val commitCount: Int,
    val authorCount: Int,
    /** Epoch millis of the newest commit touching the file, 0 when unknown. */
    val lastModifiedEpochMillis: Long,
)

/** Metadata keys the enrichment writes and the UI / formatter read. */
object GitMetadataKeys {
    const val WINDOW_DAYS: String = "git.windowDays"
    const val COMMITS: String = "git.commits"
    const val AUTHORS: String = "git.authors"
    const val LAST_MODIFIED_DAYS_AGO: String = "git.lastModifiedDaysAgo"
    const val TODO_AGE_DAYS: String = "todo.ageDays"
    const val TODO_LONG_LIVED: String = "todo.longLived"
}

/**
 * Attaches Git evidence to findings after analysis.
 *
 * Pure: the platform gathers the inputs (one bounded log query per run, blame only for
 * files that carry TODO findings) and this function merges them. History is evidence for
 * review priority, never a verdict (docs/design.md §2.1); when Git data is missing the
 * findings pass through unchanged, which is the required degradation for projects
 * without usable history.
 */
object GitEnrichment {

    fun apply(
        findings: List<Finding>,
        historyByPath: Map<String, FileHistory>,
        lineAgeEpochMillisByPath: Map<String, Map<Int, Long>>,
        nowEpochMillis: Long,
        longLivedTodoDays: Int,
        historyWindowDays: Int,
    ): List<Finding> = findings.map { finding ->
        val extra = LinkedHashMap<String, String>()

        historyByPath[finding.location.filePath]?.let { history ->
            extra[GitMetadataKeys.WINDOW_DAYS] = historyWindowDays.toString()
            extra[GitMetadataKeys.COMMITS] = history.commitCount.toString()
            extra[GitMetadataKeys.AUTHORS] = history.authorCount.toString()
            if (history.lastModifiedEpochMillis > 0) {
                extra[GitMetadataKeys.LAST_MODIFIED_DAYS_AGO] =
                    daysBetween(history.lastModifiedEpochMillis, nowEpochMillis).toString()
            }
        }

        if (finding.analyzerId == TodoMarkerAnalyzer.ID) {
            lineAgeEpochMillisByPath[finding.location.filePath]
                ?.get(finding.location.startLine)
                ?.takeIf { it > 0 }
                ?.let { touched ->
                    val ageDays = daysBetween(touched, nowEpochMillis)
                    extra[GitMetadataKeys.TODO_AGE_DAYS] = ageDays.toString()
                    if (ageDays >= longLivedTodoDays) {
                        extra[GitMetadataKeys.TODO_LONG_LIVED] = "true"
                    }
                }
        }

        if (extra.isEmpty()) {
            finding
        } else {
            val message = if (extra.containsKey(GitMetadataKeys.TODO_LONG_LIVED)) {
                finding.message +
                    " This marker has been in place for ${extra[GitMetadataKeys.TODO_AGE_DAYS]} days."
            } else {
                finding.message
            }
            finding.copy(message = message, metadata = finding.metadata + extra)
        }
    }

    private fun daysBetween(fromEpochMillis: Long, toEpochMillis: Long): Long =
        ((toEpochMillis - fromEpochMillis) / MILLIS_PER_DAY).coerceAtLeast(0)

    private const val MILLIS_PER_DAY: Long = 24L * 60 * 60 * 1000
}
