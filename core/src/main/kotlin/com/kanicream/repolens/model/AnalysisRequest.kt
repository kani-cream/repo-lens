package com.kanicream.repolens.model

/**
 * Analysis scopes. v0.1's first vertical slice implements [PROJECT] only; the remaining
 * v0.1 scopes (Current File, Selected Files, Module, Local Changes) extend this enum.
 */
enum class AnalysisScopeType(val displayName: String) {
    PROJECT("Project"),
}

/** Immutable description of one analysis run. */
data class AnalysisRequest(
    val scopeType: AnalysisScopeType,
    val settings: SettingsSnapshot,
)
