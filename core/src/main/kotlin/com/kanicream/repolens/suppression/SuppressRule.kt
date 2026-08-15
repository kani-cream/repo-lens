package com.kanicream.repolens.suppression

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.scope.GlobMatcher

/**
 * A pattern-based suppression: findings matching every given restriction are hidden.
 *
 * Restrictions left `null` do not restrict. A rule with a symbol pattern never matches a
 * finding without a symbol — "suppress toString methods" should not swallow file-level
 * findings as a side effect.
 */
class SuppressRule private constructor(
    private val checkId: String?,
    private val pathMatcher: GlobMatcher?,
    private val symbolMatcher: GlobMatcher?,
    /** The line this rule was parsed from, echoed back by the settings UI. */
    val text: String,
) {

    fun matches(finding: Finding): Boolean {
        if (checkId != null && !checkId.equals(finding.analyzerId, ignoreCase = true)) return false
        if (pathMatcher != null && !pathMatcher.matches(finding.location.filePath)) return false
        if (symbolMatcher != null) {
            val symbol = finding.symbol?.displayName ?: return false
            if (!symbolMatcher.matches(symbol)) return false
        }
        return true
    }

    companion object {
        /**
         * Parses `checkId | path-glob | symbol-glob`. Segments may be omitted from the
         * right or left blank; at least one must be present. Returns `null` for blank
         * lines, `#` comments, and lines whose globs cannot be compiled — a broken rule
         * must not silently suppress everything.
         */
        fun parse(line: String): SuppressRule? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

            val segments = trimmed.split('|').map { it.trim() }
            if (segments.size > 3) return null

            val checkId = segments.getOrNull(0)?.takeIf { it.isNotEmpty() }
            val pathPattern = segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val symbolPattern = segments.getOrNull(2)?.takeIf { it.isNotEmpty() }
            if (checkId == null && pathPattern == null && symbolPattern == null) return null

            val pathMatcher = pathPattern?.let { GlobMatcher.compile(it) ?: return null }
            val symbolMatcher = symbolPattern?.let { GlobMatcher.compile(it) ?: return null }
            return SuppressRule(checkId, pathMatcher, symbolMatcher, trimmed)
        }

        fun parseAll(lines: List<String>): List<SuppressRule> = lines.mapNotNull(::parse)
    }
}
