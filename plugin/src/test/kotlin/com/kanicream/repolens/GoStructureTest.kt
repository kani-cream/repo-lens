package com.kanicream.repolens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.analysis.DefaultAnalyzers
import com.kanicream.repolens.analysis.structure.DeepNestingAnalyzer
import com.kanicream.repolens.analysis.structure.ParameterCountAnalyzer
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.platform.ResolvedScope
import com.kanicream.repolens.platform.VfsAnalysisContext
import kotlinx.coroutines.runBlocking
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind

/**
 * Verifies Go structure extraction against real Go PSI — the checkpoint from
 * docs/milestones/v0.4.md: a provider added without touching core, analyzers, or UI.
 */
class GoStructureTest : BasePlatformTestCase() {

    private fun structureOf(text: String): CodeStructure? {
        val file = myFixture.addFileToProject("src/sample.go", text).virtualFile
        return ReadAction.compute<CodeStructure?, RuntimeException> {
            CodeStructureProvider.structureOf(project, file)
        }
    }

    fun `test free functions and receiver methods are extracted with names`() {
        val structure = structureOf(
            """
            package sample

            type Server struct{}

            func main() {
                println("hello")
            }

            func (s *Server) Start(port int) error {
                return nil
            }
            """.trimIndent(),
        )

        assertNotNull("Go files should have structure when the Go plugin is present", structure)
        val functions = structure!!.ofKind(DeclarationKind.FUNCTION)
        assertEquals(listOf("main()", "Server.Start()"), functions.map { it.displayName })
        // Go has no class bodies, so no TYPE declarations are emitted.
        assertEmpty(structure.ofKind(DeclarationKind.TYPE))
    }

    fun `test parameter counting handles grouped unnamed and variadic parameters`() {
        val structure = structureOf(
            """
            package sample

            func grouped(a, b int, c string) {}

            func unnamed(int, string) {}

            func variadic(prefix string, values ...int) {}

            func none() {}
            """.trimIndent(),
        )

        val byName = structure!!.ofKind(DeclarationKind.FUNCTION).associateBy { it.displayName }
        assertEquals(3, byName["grouped()"]?.parameterCount)
        assertEquals(2, byName["unnamed()"]?.parameterCount)
        assertEquals(2, byName["variadic()"]?.parameterCount)
        assertEquals(0, byName["none()"]?.parameterCount)
    }

    fun `test nesting depth counts go control flow`() {
        val structure = structureOf(
            """
            package sample

            func deep(groups [][]int) {
                if groups != nil {                  // 1
                    for _, group := range groups {  // 2
                        switch len(group) {         // 3
                        case 0:
                        default:
                            for _, v := range group { // 4
                                if v > 0 {            // 5
                                    func() {          // 6
                                        println(v)
                                    }()
                                }
                            }
                        }
                    }
                }
            }

            func flat() {
                println("no branches")
            }
            """.trimIndent(),
        )

        val byName = structure!!.ofKind(DeclarationKind.FUNCTION).associateBy { it.displayName }
        assertEquals(6, byName["deep()"]?.maxNestingDepth)
        assertEquals(0, byName["flat()"]?.maxNestingDepth)
    }

    fun `test the unchanged analyzers produce findings from go structure`() {
        myFixture.addFileToProject(
            "src/pipeline.go",
            """
            package sample

            func wide(a, b, c int) {
                if a > 0 {
                    if b > 0 {
                        println(c)
                    }
                }
            }
            """.trimIndent(),
        )

        val request = AnalysisRequest(
            AnalysisScopeType.PROJECT,
            SettingsSnapshot(parameterCountThreshold = 2, nestingDepthThreshold = 1),
        )
        val orchestrator = AnalysisOrchestrator(AnalyzerRegistry(DefaultAnalyzers.create()))
        val future = ApplicationManager.getApplication().executeOnPooledThread<com.kanicream.repolens.model.AnalysisResult> {
            runBlocking {
                orchestrator.analyze(VfsAnalysisContext(project, request, ResolvedScope.WholeProject))
            }
        }
        val result = PlatformTestUtil.waitForFuture(future, 60_000)

        assertEmpty(result.failures)
        val params = result.findings.single { it.analyzerId == ParameterCountAnalyzer.ID }
        assertEquals("wide()", params.symbol?.displayName)
        assertEquals(3.0, params.measuredValue)
        val nesting = result.findings.single { it.analyzerId == DeepNestingAnalyzer.ID }
        assertEquals("wide()", nesting.symbol?.displayName)
        assertEquals(2.0, nesting.measuredValue)
    }

    fun `test body line count covers the function block`() {
        val structure = structureOf(
            """
            package sample

            func sized() int {
                a := 1
                b := 2
                return a + b
            }
            """.trimIndent(),
        )

        val sized = structure!!.ofKind(DeclarationKind.FUNCTION).single()
        assertEquals(3, sized.startLine)
        assertEquals(7, sized.endLine)
        // Block spans the braces: lines 3..7 -> 5 physical lines.
        assertEquals(5, sized.bodyLineCount)
    }
}
