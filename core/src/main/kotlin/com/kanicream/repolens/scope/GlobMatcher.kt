package com.kanicream.repolens.scope

/**
 * Minimal glob matcher for project-relative paths with `/` separators.
 *
 * Supported syntax:
 * - a double star matches any characters, including separators
 * - a double star followed by a separator also matches zero leading segments, so a
 *   pattern anchored that way matches top-level paths too
 * - a single star matches any characters except a separator
 * - `?` matches a single character except a separator
 *
 * Everything else is literal, and the pattern must match the whole path.
 */
class GlobMatcher private constructor(private val regex: Regex) {

    fun matches(path: String): Boolean = regex.matches(path)

    companion object {
        private const val SEPARATOR = '/'
        private const val STAR = '*'

        /** Compiles [pattern], or returns `null` when it is blank or cannot be compiled. */
        fun compile(pattern: String): GlobMatcher? {
            val trimmed = pattern.trim()
            if (trimmed.isEmpty()) return null
            return runCatching { GlobMatcher(Regex(toRegex(trimmed))) }.getOrNull()
        }

        private fun toRegex(pattern: String): String {
            val regex = StringBuilder("^")
            var index = 0
            while (index < pattern.length) {
                index += when {
                    startsWithGlobstarSegment(pattern, index) -> {
                        regex.append("(?:.*").append(SEPARATOR).append(")?")
                        3
                    }
                    startsWithGlobstar(pattern, index) -> {
                        regex.append(".*")
                        2
                    }
                    pattern[index] == STAR -> {
                        regex.append("[^").append(SEPARATOR).append("]*")
                        1
                    }
                    pattern[index] == '?' -> {
                        regex.append("[^").append(SEPARATOR).append("]")
                        1
                    }
                    else -> {
                        regex.append(Regex.escape(pattern[index].toString()))
                        1
                    }
                }
            }
            return regex.append("$").toString()
        }

        private fun startsWithGlobstar(pattern: String, index: Int): Boolean =
            pattern[index] == STAR && pattern.getOrNull(index + 1) == STAR

        private fun startsWithGlobstarSegment(pattern: String, index: Int): Boolean =
            startsWithGlobstar(pattern, index) && pattern.getOrNull(index + 2) == SEPARATOR
    }
}
