package com.kanicream.repolens.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathExclusionsTest {

    private val defaults = PathExclusions(PathExclusions.DEFAULT_PATTERNS)

    @Test
    fun `defaults exclude virtual environments and dependency caches`() {
        listOf(
            ".venv/lib/python3.12/site-packages/pip/__init__.py",
            "backend/.venv/bin/activate",
            "venv/lib/foo.py",
            "frontend/node_modules/react/index.js",
            "apps/web/.next/dev/server/chunks/x.js",
            "src/__pycache__/module.cpython-312.pyc",
            "target/classes/App.class",
        ).forEach { path ->
            assertTrue(defaults.isExcluded(path), "expected $path to be excluded")
        }
    }

    @Test
    fun `defaults exclude top-level metadata directories`() {
        // Regression: `**' + '/x` must also match a top-level `x`.
        assertTrue(defaults.isExcluded(".git/config"))
        assertTrue(defaults.isExcluded(".idea/workspace.xml"))
        // A plugin-dev sandbox contains an entire IDE; scanning it drowns real findings.
        assertTrue(defaults.isExcluded(".intellijPlatform/sandbox/plugin/IU-2026.1.5/log/idea.log"))
        assertTrue(defaults.isExcluded("build/reports/index.html"))
    }

    @Test
    fun `defaults exclude generated lock files and minified bundles`() {
        assertTrue(defaults.isExcluded("package-lock.json"))
        assertTrue(defaults.isExcluded("apps/web/package-lock.json"))
        assertTrue(defaults.isExcluded("backend/go.sum"))
        assertTrue(defaults.isExcluded("public/js/app.min.js"))
        assertFalse(defaults.isExcluded("package.json"))
        assertFalse(defaults.isExcluded("backend/go.mod"))
    }

    @Test
    fun `defaults keep application sources`() {
        listOf(
            "src/main/kotlin/App.kt",
            "backend/internal/handler/handler.go",
            "frontend/src/components/Button.tsx",
            "scripts/deploy.sh",
            "docs/design.md",
        ).forEach { path ->
            assertFalse(defaults.isExcluded(path), "expected $path to be analyzed")
        }
    }

    @Test
    fun `does not exclude files whose name merely starts with an excluded segment`() {
        assertFalse(defaults.isExcluded("buildSrc/src/main/kotlin/Deps.kt"))
        assertFalse(defaults.isExcluded("src/distribution.kt"))
    }

    @Test
    fun `single star does not cross directory separators`() {
        val exclusions = PathExclusions(listOf("src/*.kt"))

        assertTrue(exclusions.isExcluded("src/App.kt"))
        assertFalse(exclusions.isExcluded("src/nested/App.kt"))
    }

    @Test
    fun `question mark matches exactly one character`() {
        val exclusions = PathExclusions(listOf("log?.txt"))

        assertTrue(exclusions.isExcluded("log1.txt"))
        assertFalse(exclusions.isExcluded("log.txt"))
        assertFalse(exclusions.isExcluded("log12.txt"))
    }

    @Test
    fun `blank patterns are ignored and nothing is excluded`() {
        val exclusions = PathExclusions(listOf("", "   "))

        assertFalse(exclusions.isExcluded("src/App.kt"))
        assertTrue(exclusions.invalidPatterns.isEmpty())
    }

    @Test
    fun `custom patterns are combined with each other`() {
        val exclusions = PathExclusions(listOf("**/generated/**", "*.lock"))

        assertTrue(exclusions.isExcluded("app/generated/Api.kt"))
        assertTrue(exclusions.isExcluded("package.lock"))
        assertFalse(exclusions.isExcluded("app/src/Api.kt"))
    }

    @Test
    fun `literal regex characters in patterns are not treated as regex`() {
        val exclusions = PathExclusions(listOf("**/a+b(1)/**"))

        assertTrue(exclusions.isExcluded("src/a+b(1)/File.kt"))
        assertFalse(exclusions.isExcluded("src/aab1/File.kt"))
    }

    @Test
    fun `default pattern list compiles without invalid entries`() {
        assertEquals(emptyList<String>(), defaults.invalidPatterns)
    }
}
