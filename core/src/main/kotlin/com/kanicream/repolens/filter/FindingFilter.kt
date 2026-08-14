package com.kanicream.repolens.filter

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity

/**
 * Narrows a result set without re-running the analysis.
 *
 * Empty [severities] or [checkNames] mean "no restriction" rather than "match nothing",
 * so the default instance keeps everything. Filtering is pure and lives outside the UI
 * so it can be tested without an IDE.
 */
data class FindingFilter(
    /** Case-insensitive substring matched against path, check, symbol, and message. */
    val searchText: String = "",
    val severities: Set<Severity> = emptySet(),
    /** Check display names, as shown in the findings table. */
    val checkNames: Set<String> = emptySet(),
) {

    val isActive: Boolean
        get() = searchText.isNotBlank() || severities.isNotEmpty() || checkNames.isNotEmpty()

    fun apply(findings: List<Finding>): List<Finding> {
        if (!isActive) return findings
        val needle = searchText.trim()
        return findings.filter { finding ->
            matchesSeverity(finding) && matchesCheck(finding) && matchesText(finding, needle)
        }
    }

    private fun matchesSeverity(finding: Finding): Boolean =
        severities.isEmpty() || finding.severity in severities

    private fun matchesCheck(finding: Finding): Boolean =
        checkNames.isEmpty() || finding.checkName in checkNames

    private fun matchesText(finding: Finding, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return finding.location.filePath.contains(needle, ignoreCase = true) ||
            finding.checkName.contains(needle, ignoreCase = true) ||
            finding.message.contains(needle, ignoreCase = true) ||
            finding.symbol?.displayName?.contains(needle, ignoreCase = true) == true
    }
}
