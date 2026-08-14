package com.kanicream.repolens.model

import com.kanicream.repolens.text.TextLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {

    @Test
    fun `stable id is deterministic for same analyzer file and range`() {
        val a = Finding.stableId("RL-F001", SourceLocation("src/A.kt", 1, 900))
        val b = Finding.stableId("RL-F001", SourceLocation("src/A.kt", 1, 900))
        val other = Finding.stableId("RL-F001", SourceLocation("src/A.kt", 1, 901))

        assertEquals(a, b)
        assertTrue(a != other)
    }

    @Test
    fun `source location validates line numbers`() {
        var rejected = false
        try {
            SourceLocation("a.kt", 0, 1)
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)

        rejected = false
        try {
            SourceLocation("a.kt", 5, 4)
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `line range text formats single line and range`() {
        assertEquals("84", SourceLocation("a.kt", 84, 84).lineRangeText)
        assertEquals("182-327", SourceLocation("a.kt", 182, 327).lineRangeText)
    }

    @Test
    fun `text lines counts physical lines including trailing newline`() {
        assertEquals(0, TextLines.physicalLineCount(""))
        assertEquals(1, TextLines.physicalLineCount("abc"))
        assertEquals(2, TextLines.physicalLineCount("abc\n"))
        assertEquals(3, TextLines.physicalLineCount("a\r\nb\rc"))
    }

    @Test
    fun `analysis result counts by severity`() {
        val warning = Finding(
            id = "w",
            analyzerId = "A",
            severity = Severity.WARNING,
            checkName = "c",
            message = "m",
            location = SourceLocation("a.kt", 1, 1),
        )
        val info = warning.copy(id = "i", severity = Severity.INFO)
        val result = AnalysisResult(listOf(warning, info, info.copy(id = "i2")))

        assertEquals(1, result.countBySeverity(Severity.WARNING))
        assertEquals(2, result.countBySeverity(Severity.INFO))
    }
}
