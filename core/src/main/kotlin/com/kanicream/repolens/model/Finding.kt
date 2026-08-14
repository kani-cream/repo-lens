package com.kanicream.repolens.model

/**
 * A single review candidate produced by an analyzer.
 *
 * Findings are evidence for review prioritization, not defect verdicts. They must stay
 * independent of any UI, clipboard, or navigation concern.
 */
data class Finding(
    /** Stable identity used for dedup; see [stableId]. */
    val id: String,
    val analyzerId: String,
    val severity: Severity,
    /** Display name of the check, e.g. `Large File`. */
    val checkName: String,
    /** Human-readable reason for reporting this finding. */
    val message: String,
    val location: SourceLocation,
    val symbol: SymbolInfo? = null,
    val measuredValue: Double? = null,
    val threshold: Double? = null,
    /** Analyzer-specific extras (e.g. TODO marker type). Values must never contain secrets. */
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Stable ID for dedup: same analyzer + file + range always yields the same ID,
         * regardless of analysis order or scope.
         */
        fun stableId(analyzerId: String, location: SourceLocation): String =
            "$analyzerId:${location.filePath}:${location.startLine}-${location.endLine}"
    }
}
