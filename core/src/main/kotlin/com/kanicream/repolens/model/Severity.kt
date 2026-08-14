package com.kanicream.repolens.model

/**
 * Review priority of a [Finding]. A finding is review evidence, not a verdict,
 * so severities stay coarse.
 */
enum class Severity(val rank: Int) {
    INFO(0),
    WARNING(1);

    /** Human-readable form used in tables and Markdown output (e.g. `Warning`). */
    val displayName: String = name.lowercase().replaceFirstChar { it.uppercase() }
}
