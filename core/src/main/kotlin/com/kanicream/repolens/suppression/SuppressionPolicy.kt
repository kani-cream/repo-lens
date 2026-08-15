package com.kanicream.repolens.suppression

import com.kanicream.repolens.model.Finding

/** Why a finding is hidden; shown in the detail pane. */
enum class SuppressionKind {
    /** The user ignored this exact finding (by its stable ID). */
    IGNORED,

    /** A suppress rule matched. */
    RULE,
}

/**
 * Decides which findings are hidden. Applied after analysis, never inside it: analyzers
 * keep reporting everything, so toggling "show ignored" or editing a rule needs no
 * re-analysis and suppressed findings stay available for the toggle.
 */
class SuppressionPolicy(
    private val ignoredFindingIds: Set<String>,
    private val rules: List<SuppressRule>,
) {

    fun suppressionOf(finding: Finding): SuppressionKind? = when {
        finding.id in ignoredFindingIds -> SuppressionKind.IGNORED
        rules.any { it.matches(finding) } -> SuppressionKind.RULE
        else -> null
    }

    fun isSuppressed(finding: Finding): Boolean = suppressionOf(finding) != null

    /** Splits [findings] into (visible, suppressed), preserving order. */
    fun partition(findings: List<Finding>): Pair<List<Finding>, List<Finding>> {
        val visible = mutableListOf<Finding>()
        val suppressed = mutableListOf<Finding>()
        findings.forEach { finding ->
            if (isSuppressed(finding)) suppressed += finding else visible += finding
        }
        return visible to suppressed
    }

    companion object {
        val NONE: SuppressionPolicy = SuppressionPolicy(emptySet(), emptyList())
    }
}
