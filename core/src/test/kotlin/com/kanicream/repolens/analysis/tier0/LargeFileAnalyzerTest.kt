package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.InMemoryAnalysisContext
import com.kanicream.repolens.analysis.InMemoryFile
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LargeFileAnalyzerTest {

    private val analyzer = LargeFileAnalyzer()

    private fun textOfLines(count: Int): String = (1..count).joinToString("\n") { "line $it" }

    @Test
    fun `reports file exceeding threshold`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("src/Big.kt", textOfLines(801))),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals("RL-F001", finding.analyzerId)
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals("Large File", finding.checkName)
        assertEquals("src/Big.kt", finding.location.filePath)
        assertEquals(1, finding.location.startLine)
        assertEquals(801, finding.location.endLine)
        assertEquals(801.0, finding.measuredValue)
        assertEquals(800.0, finding.threshold)
        assertTrue(finding.message.contains("801"))
        assertTrue(finding.message.contains("800"))
    }

    @Test
    fun `does not report file at threshold`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("src/AtLimit.kt", textOfLines(800))),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }

    @Test
    fun `respects custom threshold from settings`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("src/Small.txt", textOfLines(11))),
            SettingsSnapshot(largeFileLineThreshold = 10),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        assertEquals(11.0, findings.single().measuredValue)
        assertEquals(10.0, findings.single().threshold)
    }

    @Test
    fun `skips unreadable files and empty files`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                InMemoryFile("bin/image.png", text = null),
                InMemoryFile("src/Empty.kt", text = ""),
            ),
            SettingsSnapshot(largeFileLineThreshold = 0),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }

    @Test
    fun `counts CRLF lines like LF lines`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("src/Windows.txt", "a\r\nb\r\nc")),
            SettingsSnapshot(largeFileLineThreshold = 2),
        )

        assertEquals(3.0, analyzer.analyze(context).single().measuredValue)
    }
}
