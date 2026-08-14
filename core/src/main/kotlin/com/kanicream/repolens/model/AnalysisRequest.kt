package com.kanicream.repolens.model

/**
 * Scopes a user can analyze. Local Changes, the remaining v0.1 scope, is added once the
 * VCS adapter exists; nothing here depends on how a scope is resolved.
 */
enum class AnalysisScopeType(val displayName: String) {
    PROJECT("Project"),
    CURRENT_FILE("Current File"),
    SELECTED_FILES("Selected Files"),
    MODULE("Module"),
}

/** Immutable description of one analysis run. */
data class AnalysisRequest(
    val scopeType: AnalysisScopeType,
    val settings: SettingsSnapshot,
)
