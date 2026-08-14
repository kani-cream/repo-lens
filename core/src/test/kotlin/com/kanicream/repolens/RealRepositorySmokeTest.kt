package com.kanicream.repolens

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzedFile
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.analysis.tier0.LargeFileAnalyzer
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.scope.PathExclusions
import com.kanicream.repolens.text.TextLines
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * Opt-in smoke test over a real checkout, supporting the real-repository check required
 * by docs/design.md 18.3. Skipped unless `-DrepoLens.smokeRoot=/path/to/repo` is given.
 * It reports finding volume and elapsed time, and asserts the invariants that must hold
 * on arbitrary real-world input.
 */
class RealRepositorySmokeTest {

    private class DiskFile(val file: File, override val relativePath: String) : AnalyzedFile {
        private val text: String? by lazy {
            // Crude NUL-byte binary check, standing in for IDE file type detection.
            runCatching { file.readText() }.getOrNull()?.takeUnless { it.contains('\u0000') }
        }
        override suspend fun lineCount(): Int? = text?.let(TextLines::physicalLineCount)
        override suspend fun lines(): List<String>? = text?.let(TextLines::split)
    }

    @Test
    fun `smoke over real repository`() {
        val root = File(System.getProperty("repoLens.smokeRoot") ?: "")
        Assumptions.assumeTrue(root.isDirectory, "no smoke root configured")

        // Uses the production exclusion rules so the smoke run reflects real behaviour.
        val settings = SettingsSnapshot()
        val exclusions = PathExclusions(settings.excludePatterns)
        val files = root.walkTopDown()
            .filter { it.isFile && it.length() < 5_000_000 }
            .map { DiskFile(it, it.relativeTo(root).path.replace(File.separatorChar, '/')) }
            .filterNot { exclusions.isExcluded(it.relativePath) }
            .toList()

        val context = object : AnalysisContext {
            override val request = AnalysisRequest(AnalysisScopeType.PROJECT, settings)
            override suspend fun files(): List<AnalyzedFile> = files
        }
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(listOf(LargeFileAnalyzer(), TodoMarkerAnalyzer())),
        )

        lateinit var result: com.kanicream.repolens.model.AnalysisResult
        val elapsed = measureTimeMillis { result = runBlocking { orchestrator.analyze(context) } }

        println("=== Repo Lens smoke: ${root.name}")
        println("files scanned : ${files.size}")
        println("elapsed       : ${elapsed}ms")
        println("findings      : ${result.findings.size} (failures: ${result.failures.size})")
        println("  large file  : ${result.findings.count { it.analyzerId == LargeFileAnalyzer.ID }}")
        println("  todo/fixme  : ${result.findings.count { it.analyzerId == TodoMarkerAnalyzer.ID }}")
        println("--- top 8 ---")
        result.findings.take(8).forEach {
            println("  [${it.severity.displayName}] ${it.checkName} ${it.location.filePath}:${it.location.lineRangeText} ${it.measuredValue ?: ""}")
        }

        assertTrue(result.failures.isEmpty(), "analyzers must not fail on real input")
        assertEquals(
            result.findings.map { it.id }.distinct().size,
            result.findings.size,
            "finding IDs must be unique after dedup",
        )
        result.findings.forEach { finding ->
            assertFalse(finding.location.filePath.startsWith("/"), "paths must stay repository-relative")
            assertTrue(finding.location.startLine >= 1, "lines must be 1-based")
            assertTrue(finding.message.isNotBlank(), "every finding must carry a reason")
        }
    }
}
