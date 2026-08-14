package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.InMemoryAnalysisContext
import com.kanicream.repolens.analysis.InMemoryFile
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodoMarkerAnalyzerTest {

    private val analyzer = TodoMarkerAnalyzer()

    @Test
    fun `finds TODO and FIXME markers with line numbers`() = runTest {
        val text = """
            fun a() {}
            // TODO: rewrite this
            fun b() {}
            # FIXME broken on windows
        """.trimIndent()
        val context = InMemoryAnalysisContext(listOf(InMemoryFile("src/App.kt", text)))

        val findings = analyzer.analyze(context)

        assertEquals(2, findings.size)
        val todo = findings[0]
        assertEquals("RL-T001", todo.analyzerId)
        assertEquals(Severity.INFO, todo.severity)
        assertEquals(2, todo.location.startLine)
        assertEquals("TODO", todo.metadata[TodoMarkerAnalyzer.METADATA_MARKER])
        assertEquals("TODO: rewrite this", todo.metadata[TodoMarkerAnalyzer.METADATA_TEXT])

        val fixme = findings[1]
        assertEquals(4, fixme.location.startLine)
        assertEquals("FIXME", fixme.metadata[TodoMarkerAnalyzer.METADATA_MARKER])
        assertEquals("FIXME broken on windows", fixme.metadata[TodoMarkerAnalyzer.METADATA_TEXT])
    }

    @Test
    fun `matches markers case-insensitively but reports canonical marker`() = runTest {
        val context = InMemoryAnalysisContext(listOf(InMemoryFile("a.txt", "// todo later")))

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        assertEquals("TODO", findings.single().metadata[TodoMarkerAnalyzer.METADATA_MARKER])
    }

    @Test
    fun `requires whole-word match`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("a.txt", "val methodology = 1\nval TODOS = 2")),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }

    @Test
    fun `uses custom markers from settings`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("a.txt", "// HACK temporary\n// TODO ignored-by-config")),
            SettingsSnapshot(todoMarkers = listOf("HACK")),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        assertEquals(1, findings.single().location.startLine)
        assertEquals("HACK", findings.single().metadata[TodoMarkerAnalyzer.METADATA_MARKER])
    }

    @Test
    fun `does not support context without markers`() {
        val context = InMemoryAnalysisContext(emptyList(), SettingsSnapshot(todoMarkers = listOf(" ")))
        assertFalse(analyzer.supports(context))
    }

    @Test
    fun `reports one finding per line even with multiple markers`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("a.txt", "// TODO and FIXME on one line")),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        assertEquals("TODO", findings.single().metadata[TodoMarkerAnalyzer.METADATA_MARKER])
    }
}
