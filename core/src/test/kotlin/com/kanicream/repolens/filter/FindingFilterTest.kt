package com.kanicream.repolens.filter

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindingFilterTest {

    private fun finding(
        checkName: String,
        path: String,
        severity: Severity,
        message: String = "reason",
        symbol: String? = null,
    ): Finding {
        val location = SourceLocation(path, 1, 1)
        return Finding(
            id = "$checkName:$path",
            analyzerId = checkName,
            severity = severity,
            checkName = checkName,
            message = message,
            location = location,
            symbol = symbol?.let(::SymbolInfo),
        )
    }

    private val largeFile = finding("Large File", "src/payment/Service.kt", Severity.WARNING)
    private val todo = finding("TODO / FIXME", "src/App.kt", Severity.INFO, message = "Line 3 contains a TODO marker.")
    private val method = finding(
        "Large Method",
        "src/Ui.kt",
        Severity.WARNING,
        symbol = "Ui.render()",
    )
    private val all = listOf(largeFile, todo, method)

    @Test
    fun `default filter keeps everything`() {
        val filter = FindingFilter()

        assertFalse(filter.isActive)
        assertEquals(all, filter.apply(all))
    }

    @Test
    fun `severity filter keeps only matching severities`() {
        val filter = FindingFilter(severities = setOf(Severity.WARNING))

        assertTrue(filter.isActive)
        assertEquals(listOf(largeFile, method), filter.apply(all))
    }

    @Test
    fun `check filter keeps only matching checks`() {
        val filter = FindingFilter(checkNames = setOf("TODO / FIXME"))

        assertEquals(listOf(todo), filter.apply(all))
    }

    @Test
    fun `search matches file path case-insensitively`() {
        assertEquals(listOf(largeFile), FindingFilter(searchText = "PAYMENT").apply(all))
    }

    @Test
    fun `search matches check name, message and symbol`() {
        assertEquals(listOf(todo), FindingFilter(searchText = "marker").apply(all))
        assertEquals(listOf(method), FindingFilter(searchText = "render").apply(all))
        assertEquals(listOf(largeFile), FindingFilter(searchText = "Large File").apply(all))
    }

    @Test
    fun `search ignores surrounding whitespace`() {
        assertEquals(listOf(largeFile), FindingFilter(searchText = "  payment  ").apply(all))
    }

    @Test
    fun `blank search is not an active filter`() {
        assertFalse(FindingFilter(searchText = "   ").isActive)
        assertEquals(all, FindingFilter(searchText = "   ").apply(all))
    }

    @Test
    fun `filters combine with and semantics`() {
        val filter = FindingFilter(
            searchText = "src",
            severities = setOf(Severity.WARNING),
            checkNames = setOf("Large Method"),
        )

        assertEquals(listOf(method), filter.apply(all))
    }

    @Test
    fun `non-matching combination yields nothing`() {
        val filter = FindingFilter(
            severities = setOf(Severity.INFO),
            checkNames = setOf("Large File"),
        )

        assertTrue(filter.apply(all).isEmpty())
    }
}
