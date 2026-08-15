package com.kanicream.repolens.analysis

import com.kanicream.repolens.model.Finding

/**
 * Analyzer SPI.
 *
 * An analyzer's sole responsibility is turning an [AnalysisContext] into [Finding]s.
 * Implementations must not touch UI components, the clipboard, editor navigation, or
 * notifications, and must stay cancellable by calling suspending functions (or
 * otherwise cooperating with coroutine cancellation) between units of work.
 *
 * Future language providers plug in by contributing additional implementations through
 * the [AnalyzerRegistry]; the core must never need to change for that.
 */
/**
 * Thrown by an analyzer that cannot run right now for an environmental reason the user
 * should see - most prominently "the index is still being built". A skip is a normal
 * state, reported next to the results, never an error.
 */
class AnalyzerSkippedException(val reason: String) : Exception(reason)

interface RepoLensAnalyzer {
    /** Stable check ID, e.g. `RL-F001`. Used for dedup, settings, and logging. */
    val id: String

    /** Display name of the check, e.g. `Large File`. */
    val checkName: String

    /**
     * Whether this analyzer can run against [context] at all (language support,
     * required capabilities, ...). Enable/disable via settings is handled by the
     * registry, not here.
     */
    fun supports(context: AnalysisContext): Boolean

    /** Produces findings. Must be side-effect free apart from reading through [context]. */
    suspend fun analyze(context: AnalysisContext): List<Finding>
}
