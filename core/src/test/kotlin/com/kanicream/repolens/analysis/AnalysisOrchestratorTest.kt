package com.kanicream.repolens.analysis

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisOrchestratorTest {

    private fun finding(
        analyzerId: String,
        path: String,
        line: Int,
        severity: Severity = Severity.INFO,
    ): Finding {
        val location = SourceLocation(path, line, line)
        return Finding(
            id = Finding.stableId(analyzerId, location),
            analyzerId = analyzerId,
            severity = severity,
            checkName = "Check $analyzerId",
            message = "message",
            location = location,
        )
    }

    private fun stubAnalyzer(
        analyzerId: String,
        supported: Boolean = true,
        produce: suspend () -> List<Finding>,
    ): RepoLensAnalyzer = object : RepoLensAnalyzer {
        override val id = analyzerId
        override val checkName = "Check $analyzerId"
        override fun supports(context: AnalysisContext) = supported
        override suspend fun analyze(context: AnalysisContext) = produce()
    }

    private fun context(settings: SettingsSnapshot = SettingsSnapshot()) =
        InMemoryAnalysisContext(emptyList(), settings)

    @Test
    fun `aggregates findings sorted by severity then path then line`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(
                    stubAnalyzer("A-1") {
                        listOf(
                            finding("A-1", "b.kt", 5, Severity.INFO),
                            finding("A-1", "a.kt", 9, Severity.WARNING),
                        )
                    },
                    stubAnalyzer("A-2") {
                        listOf(finding("A-2", "a.kt", 1, Severity.WARNING))
                    },
                ),
            ),
        )

        val result = orchestrator.analyze(context())

        assertEquals(
            listOf("a.kt:1" to Severity.WARNING, "a.kt:9" to Severity.WARNING, "b.kt:5" to Severity.INFO),
            result.findings.map { "${it.location.filePath}:${it.location.startLine}" to it.severity },
        )
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `deduplicates findings with the same stable id`() = runTest {
        val duplicate = { finding("A-1", "a.kt", 3) }
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(listOf(stubAnalyzer("A-1") { listOf(duplicate(), duplicate()) })),
        )

        val result = orchestrator.analyze(context())

        assertEquals(1, result.findings.size)
    }

    @Test
    fun `one failing analyzer does not abort the others`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(
                    stubAnalyzer("A-BROKEN") { throw IllegalStateException("boom") },
                    stubAnalyzer("A-OK") { listOf(finding("A-OK", "a.kt", 1)) },
                ),
            ),
        )

        val result = orchestrator.analyze(context())

        assertEquals(1, result.findings.size)
        assertEquals("A-BROKEN", result.failures.single().analyzerId)
        assertEquals("IllegalStateException", result.failures.single().exceptionType)
    }

    @Test
    fun `skips disabled analyzers and unsupported analyzers`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(
                    stubAnalyzer("A-DISABLED") { listOf(finding("A-DISABLED", "a.kt", 1)) },
                    stubAnalyzer("A-UNSUPPORTED", supported = false) {
                        listOf(finding("A-UNSUPPORTED", "a.kt", 2))
                    },
                    stubAnalyzer("A-ACTIVE") { listOf(finding("A-ACTIVE", "a.kt", 3)) },
                ),
            ),
        )

        val result = orchestrator.analyze(
            context(SettingsSnapshot(disabledAnalyzerIds = setOf("A-DISABLED"))),
        )

        assertEquals(listOf("A-ACTIVE"), result.findings.map { it.analyzerId })
    }

    @Test
    fun `cancellation propagates instead of being recorded as failure`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(stubAnalyzer("A-HANGING") { suspendCancellableCoroutine { } }),
            ),
        )

        var cancelled = false
        val job = launch {
            try {
                orchestrator.analyze(context())
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        testScheduler.runCurrent()
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertTrue(cancelled)
    }

    @Test
    fun `a scope that fails to resolve aborts the run instead of failing every analyzer`() = runTest {
        val brokenScope = object : AnalysisContext {
            override val request = context().request
            override suspend fun files() = throw IllegalStateException("base branch missing")
        }
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(listOf(stubAnalyzer("A-1") { emptyList() }, stubAnalyzer("A-2") { emptyList() })),
        )

        var thrown: Exception? = null
        try {
            orchestrator.analyze(brokenScope)
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertEquals("base branch missing", thrown?.message)
    }

    @Test
    fun `a skipped analyzer is reported with its reason not as a failure`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(
                    stubAnalyzer("A-INDEXED") { throw AnalyzerSkippedException("Waiting for indexing") },
                    stubAnalyzer("A-OK") { listOf(finding("A-OK", "a.kt", 1)) },
                ),
            ),
        )

        val result = orchestrator.analyze(context())

        assertEquals(1, result.findings.size)
        assertTrue(result.failures.isEmpty())
        assertEquals(listOf("A-INDEXED" to "Waiting for indexing"), result.skips.map { it.analyzerId to it.reason })
    }

    @Test
    fun `elapsed time is recorded per executed analyzer including failed ones`() = runTest {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(
                listOf(
                    stubAnalyzer("A-OK") { listOf(finding("A-OK", "a.kt", 1)) },
                    stubAnalyzer("A-BROKEN") { throw IllegalStateException("boom") },
                    stubAnalyzer("A-SKIPPED", supported = false) { emptyList() },
                ),
            ),
        )

        val result = orchestrator.analyze(context())

        assertEquals(setOf("A-OK", "A-BROKEN"), result.elapsedByAnalyzer.keys)
        assertTrue(result.elapsedByAnalyzer.values.all { it >= 0 })
    }

    @Test
    fun `registry rejects duplicate analyzer ids`() {
        val analyzer = stubAnalyzer("A-1") { emptyList() }
        val duplicate = stubAnalyzer("A-1") { emptyList() }
        var failed = false
        try {
            AnalyzerRegistry(listOf(analyzer, duplicate))
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
