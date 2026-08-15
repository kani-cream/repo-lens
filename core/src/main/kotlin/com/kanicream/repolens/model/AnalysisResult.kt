package com.kanicream.repolens.model

/**
 * Failure of a single analyzer. Only the exception type is retained: analysis errors
 * must never carry source code content (see the logging policy in docs/design.md §15.1).
 */
data class AnalyzerFailure(
    val analyzerId: String,
    val exceptionType: String,
)

/** Aggregated outcome of one analysis run. */
data class AnalysisResult(
    val findings: List<Finding>,
    val failures: List<AnalyzerFailure> = emptyList(),
    /** Wall-clock per executed analyzer, for the diagnostics log (design 15.1). */
    val elapsedByAnalyzer: Map<String, Long> = emptyMap(),
) {
    fun countBySeverity(severity: Severity): Int = findings.count { it.severity == severity }
}
