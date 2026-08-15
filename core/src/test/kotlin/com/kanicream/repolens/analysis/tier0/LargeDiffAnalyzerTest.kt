package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.InMemoryAnalysisContext
import com.kanicream.repolens.analysis.InMemoryFile
import com.kanicream.repolens.model.ChangeStatus
import com.kanicream.repolens.model.FileChangeInfo
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LargeDiffAnalyzerTest {

    private val analyzer = LargeDiffAnalyzer()

    @Test
    fun `reports files whose changed lines exceed the threshold`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                InMemoryFile(
                    "src/Big.kt",
                    text = "a\nb\nc",
                    changeInfo = FileChangeInfo(ChangeStatus.MODIFIED, addedLines = 250, deletedLines = 60),
                ),
                InMemoryFile(
                    "src/Small.kt",
                    text = "x",
                    changeInfo = FileChangeInfo(ChangeStatus.MODIFIED, addedLines = 10, deletedLines = 5),
                ),
            ),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals("RL-G001", finding.analyzerId)
        assertEquals("Large Diff", finding.checkName)
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(310.0, finding.measuredValue)
        assertEquals(300.0, finding.threshold)
        assertEquals("modified", finding.metadata[LargeDiffAnalyzer.METADATA_STATUS])
        assertEquals("250", finding.metadata[LargeDiffAnalyzer.METADATA_ADDED])
        assertEquals("60", finding.metadata[LargeDiffAnalyzer.METADATA_DELETED])
        assertTrue(finding.message.contains("+250"))
        assertTrue(finding.message.contains("−60"))
    }

    @Test
    fun `files without change info are silent`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(InMemoryFile("src/App.kt", text = (1..2000).joinToString("\n") { "l" })),
            SettingsSnapshot(largeDiffChangedLineThreshold = 1),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }

    @Test
    fun `threshold comes from settings and boundary is exclusive`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                InMemoryFile(
                    "a.kt",
                    text = "x",
                    changeInfo = FileChangeInfo(ChangeStatus.ADDED, 30, 0),
                ),
                InMemoryFile(
                    "b.kt",
                    text = "x",
                    changeInfo = FileChangeInfo(ChangeStatus.ADDED, 31, 0),
                ),
            ),
            SettingsSnapshot(largeDiffChangedLineThreshold = 30),
        )

        val findings = analyzer.analyze(context)

        assertEquals(listOf("b.kt"), findings.map { it.location.filePath })
        assertEquals("added", findings.single().metadata[LargeDiffAnalyzer.METADATA_STATUS])
    }
}
