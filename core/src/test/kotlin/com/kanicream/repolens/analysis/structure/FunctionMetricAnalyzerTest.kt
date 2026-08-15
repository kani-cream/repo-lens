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

class FunctionMetricAnalyzerTest {

    private fun function(
        name: String,
        parameterCount: Int? = null,
        maxNestingDepth: Int? = null,
    ) = CodeDeclaration(
        kind = DeclarationKind.FUNCTION,
        displayName = name,
        startLine = 10,
        endLine = 40,
        bodyLineCount = 30,
        parameterCount = parameterCount,
        maxNestingDepth = maxNestingDepth,
    )

    private fun contextOf(
        vararg declarations: CodeDeclaration,
        settings: SettingsSnapshot = SettingsSnapshot(),
    ) = InMemoryAnalysisContext(
        listOf(InMemoryFile("src/App.kt", structure = CodeStructure(declarations.toList()))),
        settings,
    )

    @Test
    fun `parameter count over the threshold is reported with symbol and metrics`() = runTest {
        val context = contextOf(
            function("Api.wide()", parameterCount = 9),
            function("Api.narrow()", parameterCount = 7),
        )

        val findings = ParameterCountAnalyzer().analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals("RL-M002", finding.analyzerId)
        assertEquals("Too Many Parameters", finding.checkName)
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals("Api.wide()", finding.symbol?.displayName)
        assertEquals(9.0, finding.measuredValue)
        assertEquals(7.0, finding.threshold)
        assertTrue(finding.message.contains("9"))
    }

    @Test
    fun `nesting depth over the threshold is reported`() = runTest {
        val context = contextOf(
            function("deep()", maxNestingDepth = 6),
            function("flat()", maxNestingDepth = 5),
        )

        val findings = DeepNestingAnalyzer().analyze(context)

        assertEquals(1, findings.size)
        assertEquals("RL-M003", findings.single().analyzerId)
        assertEquals("Deep Nesting", findings.single().checkName)
        assertEquals(6.0, findings.single().measuredValue)
    }

    @Test
    fun `functions without the metric are skipped`() = runTest {
        val context = contextOf(
            function("noMetrics()"),
            settings = SettingsSnapshot(parameterCountThreshold = 0, nestingDepthThreshold = 0),
        )

        assertTrue(ParameterCountAnalyzer().analyze(context).isEmpty())
        assertTrue(DeepNestingAnalyzer().analyze(context).isEmpty())
    }

    @Test
    fun `types are never reported even with metrics present`() = runTest {
        val type = CodeDeclaration(
            kind = DeclarationKind.TYPE,
            displayName = "Odd",
            startLine = 1,
            endLine = 5,
            bodyLineCount = 5,
            parameterCount = 99,
            maxNestingDepth = 99,
        )

        assertTrue(ParameterCountAnalyzer().analyze(contextOf(type)).isEmpty())
        assertTrue(DeepNestingAnalyzer().analyze(contextOf(type)).isEmpty())
    }

    @Test
    fun `thresholds come from settings`() = runTest {
        val context = contextOf(
            function("f()", parameterCount = 3, maxNestingDepth = 2),
            settings = SettingsSnapshot(parameterCountThreshold = 2, nestingDepthThreshold = 1),
        )

        assertEquals(2.0, ParameterCountAnalyzer().analyze(context).single().threshold)
        assertEquals(1.0, DeepNestingAnalyzer().analyze(context).single().threshold)
    }
}
