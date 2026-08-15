package com.kanicream.repolens.format

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlainTextFormatterTest {

    private val method = Finding(
        id = "m",
        analyzerId = "RL-M001",
        severity = Severity.WARNING,
        checkName = "Large Method",
        message = "This method has 92 body lines, exceeding the configured threshold of 80.",
        location = SourceLocation("src/Sample.java", 5, 96),
        symbol = SymbolInfo("Sample.oversized()"),
        measuredValue = 92.0,
        threshold = 80.0,
    )

    private val todo = Finding(
        id = "t",
        analyzerId = "RL-T001",
        severity = Severity.INFO,
        checkName = "TODO / FIXME",
        message = "Line 7 contains a TODO marker.",
        location = SourceLocation("src/App.kt", 7, 7),
    )

    @Test
    fun `result renders one pipe-separated line per finding`() {
        val text = PlainTextFormatter.formatResult(
            listOf(CopyItem(method, null), CopyItem(todo, null)),
        )

        val lines = text.trimEnd().lines()
        assertEquals(2, lines.size)
        assertEquals(
            "Warning | Large Method | src/Sample.java:5-96 | Sample.oversized() | 92 / 80 | " +
                "This method has 92 body lines, exceeding the configured threshold of 80.",
            lines[0],
        )
        // Absent symbol and metrics simply drop their fields instead of leaving gaps.
        assertEquals(
            "Info | TODO / FIXME | src/App.kt:7 | Line 7 contains a TODO marker.",
            lines[1],
        )
    }

    @Test
    fun `with code appends numbered snippet lines`() {
        val snippet = CodeSnippet(startLine = 5, lines = listOf("int oversized() {", "  int total = 0;"), omittedLineCount = 0)
        val text = PlainTextFormatter.formatWithCode(listOf(CopyItem(method, snippet)))

        assertTrue(text.contains("   5 | int oversized() {"))
        assertTrue(text.contains("   6 |   int total = 0;"))
        assertFalse(text.contains("omitted"))
    }

    @Test
    fun `with code marks truncation`() {
        val snippet = CodeSnippet(startLine = 1, lines = listOf("a"), omittedLineCount = 84)
        val text = PlainTextFormatter.formatWithCode(listOf(CopyItem(method, snippet)))

        assertTrue(text.contains("... omitted 84 lines ..."))
    }

    @Test
    fun `findings are separated by a blank line in with-code output`() {
        val text = PlainTextFormatter.formatWithCode(
            listOf(CopyItem(method, null), CopyItem(todo, null)),
        )

        assertTrue(text.contains("threshold of 80.\n\nInfo | TODO"))
    }

    @Test
    fun `missing snippet leaves just the result line`() {
        val text = PlainTextFormatter.formatWithCode(listOf(CopyItem(todo, null)))

        assertEquals("Info | TODO / FIXME | src/App.kt:7 | Line 7 contains a TODO marker.\n", text)
    }
}
