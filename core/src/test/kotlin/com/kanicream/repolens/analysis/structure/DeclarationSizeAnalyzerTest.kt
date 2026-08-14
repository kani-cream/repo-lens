package com.kanicream.repolens.analysis.structure

import com.kanicream.repolens.analysis.InMemoryAnalysisContext
import com.kanicream.repolens.analysis.InMemoryFile
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.structure.CodeDeclaration
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.DeclarationKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeclarationSizeAnalyzerTest {

    private fun declaration(
        kind: DeclarationKind,
        name: String,
        startLine: Int,
        endLine: Int,
        bodyLines: Int,
    ) = CodeDeclaration(kind, name, startLine, endLine, bodyLines)

    private fun contextOf(
        vararg declarations: CodeDeclaration,
        settings: SettingsSnapshot = SettingsSnapshot(),
    ) = InMemoryAnalysisContext(
        listOf(InMemoryFile("src/App.kt", structure = CodeStructure(declarations.toList()))),
        settings,
    )

    @Test
    fun `large class reports types over the threshold with symbol and metrics`() = runTest {
        val context = contextOf(
            declaration(DeclarationKind.TYPE, "PaymentService", 10, 620, 600),
            declaration(DeclarationKind.TYPE, "Small", 700, 710, 10),
        )

        val findings = LargeClassAnalyzer().analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals("RL-C001", finding.analyzerId)
        assertEquals("Large Class", finding.checkName)
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals("PaymentService", finding.symbol?.displayName)
        assertEquals(10, finding.location.startLine)
        assertEquals(620, finding.location.endLine)
        assertEquals(600.0, finding.measuredValue)
        assertEquals(500.0, finding.threshold)
        assertTrue(finding.message.contains("600"))
    }

    @Test
    fun `large method reports only functions`() = runTest {
        val context = contextOf(
            declaration(DeclarationKind.TYPE, "Big", 1, 900, 890),
            declaration(DeclarationKind.FUNCTION, "Big.process()", 100, 300, 190),
        )

        val findings = LargeMethodAnalyzer().analyze(context)

        assertEquals(1, findings.size)
        assertEquals("RL-M001", findings.single().analyzerId)
        assertEquals("Big.process()", findings.single().symbol?.displayName)
        assertEquals(190.0, findings.single().measuredValue)
    }

    @Test
    fun `declaration exactly at the threshold is not reported`() = runTest {
        val context = contextOf(declaration(DeclarationKind.FUNCTION, "f()", 1, 90, 80))

        assertTrue(LargeMethodAnalyzer().analyze(context).isEmpty())
    }

    @Test
    fun `thresholds come from settings`() = runTest {
        val context = contextOf(
            declaration(DeclarationKind.FUNCTION, "f()", 1, 20, 11),
            settings = SettingsSnapshot(largeMethodLineThreshold = 10),
        )

        val findings = LargeMethodAnalyzer().analyze(context)

        assertEquals(11.0, findings.single().measuredValue)
        assertEquals(10.0, findings.single().threshold)
    }

    @Test
    fun `files without a structure provider yield nothing`() = runTest {
        val context = InMemoryAnalysisContext(listOf(InMemoryFile("main.go", text = "package main")))

        assertTrue(LargeClassAnalyzer().analyze(context).isEmpty())
        assertTrue(LargeMethodAnalyzer().analyze(context).isEmpty())
    }

    @Test
    fun `findings from the same declaration range are stable across runs`() = runTest {
        val context = contextOf(declaration(DeclarationKind.TYPE, "Big", 1, 900, 890))

        val first = LargeClassAnalyzer().analyze(context).single()
        val second = LargeClassAnalyzer().analyze(context).single()

        assertEquals(first.id, second.id)
    }
}
