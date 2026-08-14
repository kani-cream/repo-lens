package com.kanicream.repolens.format

import com.kanicream.repolens.model.CopySettings
import com.kanicream.repolens.model.SourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodeSnippetBuilderTest {

    private val lines = (1..100).map { "line $it" }

    @Test
    fun `includes context lines around the finding range`() {
        val snippet = CodeSnippetBuilder.build(
            lines,
            SourceLocation("a.kt", 10, 12),
            CopySettings(contextLines = 2, maxCodeLines = 100),
        )

        assertEquals(8, snippet.startLine)
        assertEquals(listOf("line 8", "line 9", "line 10", "line 11", "line 12", "line 13", "line 14"), snippet.lines)
        assertFalse(snippet.isTruncated)
    }

    @Test
    fun `clamps context at file boundaries`() {
        val snippet = CodeSnippetBuilder.build(
            lines,
            SourceLocation("a.kt", 1, 2),
            CopySettings(contextLines = 5, maxCodeLines = 100),
        )

        assertEquals(1, snippet.startLine)
        assertEquals("line 1", snippet.lines.first())
        assertEquals("line 7", snippet.lines.last())
    }

    @Test
    fun `truncates at maxCodeLines and reports omitted count`() {
        val snippet = CodeSnippetBuilder.build(
            lines,
            SourceLocation("a.kt", 1, 100),
            CopySettings(contextLines = 0, maxCodeLines = 16),
        )

        assertTrue(snippet.isTruncated)
        assertEquals(16, snippet.lines.size)
        assertEquals(84, snippet.omittedLineCount)
        assertEquals("line 16", snippet.lines.last())
    }

    @Test
    fun `range beyond file end yields empty snippet`() {
        val snippet = CodeSnippetBuilder.build(
            listOf("only line"),
            SourceLocation("a.kt", 5, 6),
            CopySettings(contextLines = 0, maxCodeLines = 10),
        )

        assertTrue(snippet.lines.isEmpty())
        assertFalse(snippet.isTruncated)
    }
}
