package com.kanicream.repolens.analysis

import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalyzerFailure
import com.kanicream.repolens.model.Finding
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource
import kotlinx.coroutines.ensureActive

/**
 * Runs the active analyzers for a request and aggregates their findings.
 *
 * Responsibilities: analyzer selection (via the registry), cancellation propagation,
 * failure isolation, dedup by stable finding ID, and deterministic ordering.
 * One failing analyzer never aborts the others (graceful degradation); cancellation
 * always propagates.
 */
class AnalysisOrchestrator(private val registry: AnalyzerRegistry) {

    suspend fun analyze(context: AnalysisContext): AnalysisResult {
        val findingsById = LinkedHashMap<String, Finding>()
        val failures = mutableListOf<AnalyzerFailure>()
        val elapsed = LinkedHashMap<String, Long>()

        for (analyzer in registry.activeFor(context)) {
            coroutineContext.ensureActive()
            // Monotonic: wall-clock adjustments must not skew the diagnostics.
            val startedAt = TimeSource.Monotonic.markNow()
            try {
                for (finding in analyzer.analyze(context)) {
                    findingsById.putIfAbsent(finding.id, finding)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += AnalyzerFailure(analyzer.id, e.javaClass.simpleName)
            } finally {
                elapsed[analyzer.id] = startedAt.elapsedNow().inWholeMilliseconds
            }
        }

        val sorted = findingsById.values.sortedWith(
            compareByDescending<Finding> { it.severity.rank }
                .thenBy { it.location.filePath }
                .thenBy { it.location.startLine }
                .thenBy { it.analyzerId },
        )
        return AnalysisResult(sorted, failures, elapsed)
    }
}
