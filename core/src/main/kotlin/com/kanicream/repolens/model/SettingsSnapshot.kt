package com.kanicream.repolens.model

import com.kanicream.repolens.scope.PathExclusions

/** Limits applied when extracting code snippets for clipboard export. */
data class CopySettings(
    /** Extra lines included before and after the finding range. */
    val contextLines: Int = DEFAULT_CONTEXT_LINES,
    /** Hard cap on snippet lines; the overflow is truncated and reported. */
    val maxCodeLines: Int = DEFAULT_MAX_CODE_LINES,
) {
    companion object {
        const val DEFAULT_CONTEXT_LINES: Int = 3
        const val DEFAULT_MAX_CODE_LINES: Int = 60
    }
}

/**
 * Immutable snapshot of the user settings taken when an analysis starts, so that a
 * settings change mid-run cannot produce inconsistent findings.
 */
data class SettingsSnapshot(
    val largeFileLineThreshold: Int = DEFAULT_LARGE_FILE_LINE_THRESHOLD,
    val largeClassLineThreshold: Int = DEFAULT_LARGE_CLASS_LINE_THRESHOLD,
    val largeMethodLineThreshold: Int = DEFAULT_LARGE_METHOD_LINE_THRESHOLD,
    val parameterCountThreshold: Int = DEFAULT_PARAMETER_COUNT_THRESHOLD,
    val nestingDepthThreshold: Int = DEFAULT_NESTING_DEPTH_THRESHOLD,
    val largeDiffChangedLineThreshold: Int = DEFAULT_LARGE_DIFF_CHANGED_LINE_THRESHOLD,
    /** Base branch for the Branch Diff scope; blank auto-detects main/master. */
    val baseBranch: String = "",
    val todoMarkers: List<String> = DEFAULT_TODO_MARKERS,
    val disabledAnalyzerIds: Set<String> = emptySet(),
    /** Glob patterns for paths kept out of analysis; see [PathExclusions]. */
    val excludePatterns: List<String> = PathExclusions.DEFAULT_PATTERNS,
    val copy: CopySettings = CopySettings(),
) {
    companion object {
        const val DEFAULT_LARGE_FILE_LINE_THRESHOLD: Int = 800
        const val DEFAULT_LARGE_CLASS_LINE_THRESHOLD: Int = 500
        const val DEFAULT_LARGE_METHOD_LINE_THRESHOLD: Int = 80
        const val DEFAULT_PARAMETER_COUNT_THRESHOLD: Int = 7
        const val DEFAULT_NESTING_DEPTH_THRESHOLD: Int = 5
        const val DEFAULT_LARGE_DIFF_CHANGED_LINE_THRESHOLD: Int = 300
        val DEFAULT_TODO_MARKERS: List<String> = listOf("TODO", "FIXME")
    }
}
