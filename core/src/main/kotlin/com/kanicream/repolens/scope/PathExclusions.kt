package com.kanicream.repolens.scope

/**
 * Decides which project-relative paths are kept out of an analysis.
 *
 * Scope resolution applies this before analyzers see a file, so analyzers never need to
 * know about build output, dependency caches, or virtual environments. Patterns that
 * cannot be compiled are ignored rather than failing the whole run; [invalidPatterns]
 * reports them so the UI can explain why a rule had no effect.
 */
class PathExclusions(patterns: List<String>) {

    private val matchers: List<GlobMatcher>
    val invalidPatterns: List<String>

    init {
        val compiled = mutableListOf<GlobMatcher>()
        val invalid = mutableListOf<String>()
        patterns.forEach { pattern ->
            if (pattern.isBlank()) return@forEach
            val matcher = GlobMatcher.compile(pattern)
            if (matcher == null) invalid += pattern.trim() else compiled += matcher
        }
        matchers = compiled
        invalidPatterns = invalid
    }

    fun isExcluded(relativePath: String): Boolean = matchers.any { it.matches(relativePath) }

    companion object {
        /**
         * Directories that are almost never worth reviewing: VCS metadata, build output,
         * dependency caches, and language virtual environments. Users can edit the list
         * in settings, so this only has to be a sensible starting point.
         */
        val DEFAULT_PATTERNS: List<String> = listOf(
            "**/.git/**",
            "**/.idea/**",
            "**/.gradle/**",
            "**/.intellijPlatform/**",
            "**/build/**",
            "**/out/**",
            "**/target/**",
            "**/dist/**",
            "**/node_modules/**",
            "**/vendor/**",
            "**/.venv/**",
            "**/venv/**",
            "**/__pycache__/**",
            "**/.next/**",
            "**/.nuxt/**",
            "**/.tox/**",
            "**/coverage/**",
            // Machine-generated files that are never review targets.
            "**/package-lock.json",
            "**/yarn.lock",
            "**/pnpm-lock.yaml",
            "**/Cargo.lock",
            "**/go.sum",
            "**/*.min.js",
            "**/*.min.css",
        )
    }
}
