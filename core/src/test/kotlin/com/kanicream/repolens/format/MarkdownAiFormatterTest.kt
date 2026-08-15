package com.kanicream.repolens.format

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownAiFormatterTest {

    private fun largeFileFinding(): Finding {
        val location = SourceLocation("src/payment/PaymentService.kt", 1, 900)
        return Finding(
            id = Finding.stableId("RL-F001", location),
            analyzerId = "RL-F001",
            severity = Severity.WARNING,
            checkName = "Large File",
            message = "File has 900 physical lines, exceeding the configured threshold of 800.",
            location = location,
            measuredValue = 900.0,
            threshold = 800.0,
        )
    }

    private fun todoFinding(): Finding {
        val location = SourceLocation("src/App.kt", 84, 84)
        return Finding(
            id = Finding.stableId("RL-T001", location),
            analyzerId = "RL-T001",
            severity = Severity.INFO,
            checkName = "TODO / FIXME",
            message = "Line 84 contains a TODO marker.",
            location = location,
        )
    }

    @Test
    fun `single finding renders required fields and trailing prompt`() {
        val snippet = CodeSnippet(startLine = 1, lines = listOf("class PaymentService {", "}"), omittedLineCount = 0)
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest("payment-api", "Project", listOf(CopyItem(largeFileFinding(), snippet))),
        )

        assertTrue(markdown.startsWith("## Repo Lens Finding"))
        assertTrue(markdown.contains("- Issue: Large File"))
        assertTrue(markdown.contains("- Severity: Warning"))
        assertTrue(markdown.contains("- File: `src/payment/PaymentService.kt`"))
        assertTrue(markdown.contains("- Location: `1-900`"))
        assertTrue(markdown.contains("- Value: 900"))
        assertTrue(markdown.contains("- Threshold: 800"))
        assertTrue(markdown.contains("### Reason"))
        assertTrue(markdown.contains("exceeding the configured threshold"))
        assertTrue(markdown.contains("### Code"))
        assertTrue(markdown.contains("```kotlin"))
        assertTrue(markdown.contains("class PaymentService {"))
        assertTrue(markdown.trimEnd().endsWith(MarkdownAiFormatter.REVIEW_PROMPT))
    }

    @Test
    fun `symbol line is omitted when finding has no symbol`() {
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest("p", "Project", listOf(CopyItem(largeFileFinding(), snippet = null))),
        )

        assertFalse(markdown.contains("- Symbol:"))
        assertFalse(markdown.contains("### Code"))
    }

    @Test
    fun `symbol is rendered when present`() {
        val finding = todoFinding().copy(symbol = SymbolInfo("App.main()"))
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest("p", "Project", listOf(CopyItem(finding, snippet = null))),
        )

        assertTrue(markdown.contains("- Symbol: `App.main()`"))
        assertTrue(markdown.contains("- Location: `84`"))
    }

    @Test
    fun `multiple findings render review context header and numbered sections`() {
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest(
                projectName = "payment-api",
                scopeName = "Project",
                items = listOf(
                    CopyItem(largeFileFinding(), snippet = null),
                    CopyItem(todoFinding(), snippet = null),
                ),
            ),
        )

        assertTrue(markdown.startsWith("# Repo Lens Review Context"))
        assertTrue(markdown.contains("Project: payment-api"))
        assertTrue(markdown.contains("Scope: Project"))
        assertTrue(markdown.contains("Findings: 2"))
        assertTrue(markdown.contains("## 1. Large File"))
        assertTrue(markdown.contains("## 2. TODO / FIXME"))
        assertEquals(1, Regex(Regex.escape(MarkdownAiFormatter.REVIEW_PROMPT)).findAll(markdown).count())
    }

    @Test
    fun `truncated snippet renders omitted line marker inside the fence`() {
        val snippet = CodeSnippet(startLine = 1, lines = listOf("a", "b"), omittedLineCount = 84)
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest("p", "Project", listOf(CopyItem(largeFileFinding(), snippet))),
        )

        assertTrue(markdown.contains("... omitted 84 lines ..."))
    }

    @Test
    fun `snippet containing backtick fence uses a longer fence`() {
        val snippet = CodeSnippet(
            startLine = 1,
            lines = listOf("```markdown", "text", "```"),
            omittedLineCount = 0,
        )
        val finding = largeFileFinding().copy(
            location = SourceLocation("docs/README.md", 1, 3),
        )
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest("p", "Project", listOf(CopyItem(finding, snippet))),
        )

        assertTrue(markdown.contains("````markdown"))
    }

    @Test
    fun `non-integral metric keeps decimal representation`() {
        assertEquals("2.5", MetricFormat.format(2.5))
        assertEquals("800", MetricFormat.format(800.0))
    }
}
