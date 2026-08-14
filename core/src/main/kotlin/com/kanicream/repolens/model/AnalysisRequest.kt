package com.kanicream.repolens.model

/** Scopes a user can analyze. Nothing here depends on how a scope is resolved. */
enum class AnalysisScopeType(val displayName: String) {
    PROJECT("Project"),
    CURRENT_FILE("Current File"),
    SELECTED_FILES("Selected Files"),
    MODULE("Module"),
    LOCAL_CHANGES("Local Changes"),
}

/** Immutable description of one analysis run. */
data class AnalysisRequest(
    val scopeType: AnalysisScopeType,
    val settings: SettingsSnapshot,
)
