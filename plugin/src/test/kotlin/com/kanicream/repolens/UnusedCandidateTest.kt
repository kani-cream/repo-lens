package com.kanicream.repolens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.Confidence
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.platform.ResolvedScope
import com.kanicream.repolens.platform.VfsAnalysisContext
import com.kanicream.repolens.structure.uast.UnusedCandidateAnalyzer
import kotlinx.coroutines.runBlocking

class UnusedCandidateTest : BasePlatformTestCase() {

    private fun analyze(): AnalysisResult {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(listOf(UnusedCandidateAnalyzer(project))),
        )
        val request = AnalysisRequest(AnalysisScopeType.PROJECT, SettingsSnapshot())
        val future = ApplicationManager.getApplication().executeOnPooledThread<AnalysisResult> {
            runBlocking {
                orchestrator.analyze(VfsAnalysisContext(project, request, ResolvedScope.WholeProject))
            }
        }
        return PlatformTestUtil.waitForFuture(future, 60_000)
    }

    fun `test unreferenced public declarations are candidates with low confidence`() {
        myFixture.addFileToProject(
            "orphan/OrphanService.java",
            """
            package orphan;

            public class OrphanService {
                public void neverCalled() {
                }
            }
            """.trimIndent(),
        )

        val findings = analyze().findings

        val symbols = findings.map { it.symbol?.displayName }
        assertContainsElements(symbols, "OrphanService", "OrphanService.neverCalled()")
        findings.forEach { finding ->
            assertEquals(Confidence.LOW, finding.confidence)
            assertTrue(finding.message, "candidate, not a verdict" in finding.message)
        }
    }

    fun `test referenced declarations are not candidates`() {
        myFixture.addFileToProject(
            "used/Engine.java",
            """
            package used;

            public class Engine {
                public void run() {
                }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "used/Driver.java",
            """
            package used;

            public class Driver {
                public void drive() {
                    new Engine().run();
                }
            }
            """.trimIndent(),
        )

        val symbols = analyze().findings.map { it.symbol?.displayName }

        // Engine and run() are referenced; Driver and drive() are not.
        assertDoesntContain(symbols, "Engine", "Engine.run()")
        assertContainsElements(symbols, "Driver", "Driver.drive()")
    }

    fun `test entry points overrides and non-public members are skipped`() {
        myFixture.addFileToProject(
            "entry/Entry.java",
            """
            package entry;

            public class Entry implements Runnable {
                public static void main(String[] args) {
                }

                @Override
                public void run() {
                }

                void packagePrivate() {
                }

                @Deprecated
                public void annotated() {
                }
            }
            """.trimIndent(),
        )

        val symbols = analyze().findings.map { it.symbol?.displayName }

        assertDoesntContain(
            symbols,
            "Entry.main()",
            "Entry.run()",
            "Entry.packagePrivate()",
            "Entry.annotated()",
        )
        // The class itself is unreferenced and public, so it stays a candidate.
        assertContainsElements(symbols, "Entry")
    }

    fun `test kotlin public functions are candidates too`() {
        myFixture.addFileToProject(
            "orphan/KotlinOrphan.kt",
            """
            package orphan

            class KotlinOrphan {
                fun neverCalled(): Int {
                    return 1
                }
            }
            """.trimIndent(),
        )

        val symbols = analyze().findings.map { it.symbol?.displayName }

        // Kotlin light methods carry synthesized nullability annotations, which must not
        // disqualify them the way real (framework) annotations do.
        assertContainsElements(symbols, "KotlinOrphan", "KotlinOrphan.neverCalled()")
    }

    fun `test dumb mode skips with a visible reason instead of failing`() {
        myFixture.addFileToProject("A.java", "public class A {}\n")

        val result = DumbModeTestUtils.computeInDumbModeSynchronously(project) { analyze() }

        assertEmpty(result.findings)
        assertEmpty(result.failures)
        val skip = result.skips.single()
        assertEquals(UnusedCandidateAnalyzer.ID, skip.analyzerId)
        assertTrue(skip.reason, "indexing" in skip.reason)
    }
}
