package com.kanicream.repolens.analysis

/**
 * Holds the analyzers known to this installation and decides which of them run for a
 * given context. Language providers will contribute additional analyzers here without
 * requiring core changes.
 */
class AnalyzerRegistry(analyzers: List<RepoLensAnalyzer>) {

    private val analyzers: List<RepoLensAnalyzer> = analyzers.toList()

    init {
        val duplicated = analyzers.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "Duplicate analyzer IDs: $duplicated" }
    }

    fun all(): List<RepoLensAnalyzer> = analyzers

    /** Analyzers that are enabled in settings and support [context]. */
    fun activeFor(context: AnalysisContext): List<RepoLensAnalyzer> =
        analyzers.filter { it.id !in context.settings.disabledAnalyzerIds && it.supports(context) }
}
