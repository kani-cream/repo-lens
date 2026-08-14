package com.kanicream.repolens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.analysis.tier0.LargeFileAnalyzer
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.format.AiCopyItem
import com.kanicream.repolens.format.AiCopyRequest
import com.kanicream.repolens.format.CodeSnippetBuilder
import com.kanicream.repolens.format.MarkdownAiFormatter
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.CopySettings
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.navigation.FindingNavigator
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.platform.ResolvedScope
import com.kanicream.repolens.platform.VfsAnalysisContext
import com.kanicream.repolens.text.TextLines
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/**
 * Fixture test for the v0.1 workflow: scope resolution over real (light fixture) files,
 * then navigation and Copy-for-AI formatting from the produced findings.
 */
class RepoLensWorkflowTest : BasePlatformTestCase() {

    private fun analyze(
        scope: ResolvedScope,
        settings: SettingsSnapshot = SettingsSnapshot(),
        scopeType: AnalysisScopeType = AnalysisScopeType.PROJECT,
    ): AnalysisResult {
        val orchestrator = AnalysisOrchestrator(
            AnalyzerRegistry(listOf(LargeFileAnalyzer(), TodoMarkerAnalyzer())),
        )
        val request = AnalysisRequest(scopeType, settings)
        // The suspending readAction contract forbids the EDT (where light tests run),
        // so the analysis executes on a pooled thread like it does in production.
        val future = ApplicationManager.getApplication().executeOnPooledThread<AnalysisResult> {
            runBlocking {
                orchestrator.analyze(VfsAnalysisContext(project, request, scope))
            }
        }
        return future.get(60, TimeUnit.SECONDS)
    }

    private fun analyzeProject(settings: SettingsSnapshot): AnalysisResult =
        analyze(ResolvedScope.WholeProject, settings)

    private fun fileNames(result: AnalysisResult): List<String> =
        result.findings.map { it.location.filePath.substringAfterLast('/') }.distinct()

    fun `test project analysis finds large file and todo markers`() {
        myFixture.addFileToProject("src/Big.txt", (1..30).joinToString("\n") { "line $it" })
        myFixture.addFileToProject("src/App.kt", "fun main() {}\n// TODO check this\n// FIXME broken\n")

        val result = analyzeProject(SettingsSnapshot(largeFileLineThreshold = 20))

        assertEmpty(result.failures)

        val largeFile = result.findings.filter { it.analyzerId == LargeFileAnalyzer.ID }
        assertSize(1, largeFile)
        assertEquals(Severity.WARNING, largeFile.single().severity)
        assertTrue(largeFile.single().location.filePath.endsWith("Big.txt"))
        assertEquals(30.0, largeFile.single().measuredValue)

        val todos = result.findings.filter { it.analyzerId == TodoMarkerAnalyzer.ID }
        assertSize(2, todos)
        assertEquals(listOf(2, 3), todos.map { it.location.startLine })
        assertEquals(
            listOf("TODO", "FIXME"),
            todos.map { it.metadata[TodoMarkerAnalyzer.METADATA_MARKER] },
        )
    }

    fun `test excluded directories are not analyzed`() {
        myFixture.addFileToProject(".venv/lib/python3.12/site-packages/pip/cli.py", "# TODO vendored\n")
        myFixture.addFileToProject("node_modules/react/index.js", "// TODO vendored\n")
        myFixture.addFileToProject("src/App.kt", "// TODO mine\n")

        val findings = analyzeProject(SettingsSnapshot()).findings

        // The light fixture's temp file system does not always yield project-relative
        // paths, so assert on file names rather than on the full path.
        assertEquals(
            "only project sources should be analyzed",
            listOf("App.kt"),
            findings.map { it.location.filePath.substringAfterLast('/') },
        )
    }

    fun `test custom exclude pattern overrides the defaults`() {
        myFixture.addFileToProject("generated/Api.kt", "// TODO generated\n")
        myFixture.addFileToProject("src/App.kt", "// TODO mine\n")

        val findings = analyzeProject(
            SettingsSnapshot(excludePatterns = listOf("**/generated/**")),
        ).findings

        assertEquals(listOf("App.kt"), findings.map { it.location.filePath.substringAfterLast('/') })
    }

    fun `test current file scope analyzes only the given file`() {
        val target = myFixture.addFileToProject("src/Target.kt", "// TODO mine\n").virtualFile
        myFixture.addFileToProject("src/Other.kt", "// TODO other\n")

        val result = analyze(
            ResolvedScope.ExplicitFiles(listOf(target)),
            scopeType = AnalysisScopeType.CURRENT_FILE,
        )

        assertEquals(listOf("Target.kt"), fileNames(result))
    }

    fun `test selected files scope expands directories`() {
        val directory = myFixture.addFileToProject("src/feature/A.kt", "// TODO a\n").virtualFile.parent
        myFixture.addFileToProject("src/feature/nested/B.kt", "// TODO b\n")
        myFixture.addFileToProject("src/Outside.kt", "// TODO outside\n")

        val result = analyze(
            ResolvedScope.ExplicitFiles(listOf(directory)),
            scopeType = AnalysisScopeType.SELECTED_FILES,
        )

        assertEquals(listOf("A.kt", "B.kt"), fileNames(result).sorted())
    }

    fun `test explicitly selected file is analyzed even when excluded by default`() {
        val excluded: VirtualFile =
            myFixture.addFileToProject(".venv/lib/tool.py", "# TODO vendored\n").virtualFile

        val result = analyze(
            ResolvedScope.ExplicitFiles(listOf(excluded)),
            scopeType = AnalysisScopeType.SELECTED_FILES,
        )

        assertEquals(listOf("tool.py"), fileNames(result))
    }

    fun `test module scope covers the module owning the anchor file`() {
        val anchor = myFixture.addFileToProject("src/Anchor.kt", "// TODO anchor\n").virtualFile
        myFixture.addFileToProject("src/Sibling.kt", "// TODO sibling\n")

        val result = analyze(
            ResolvedScope.ContainingModule(anchor),
            scopeType = AnalysisScopeType.MODULE,
        )

        assertEquals(listOf("Anchor.kt", "Sibling.kt"), fileNames(result).sorted())
    }

    fun `test navigation opens the finding file in an editor`() {
        myFixture.addFileToProject("src/Todo.kt", "// line 1\n// TODO navigate here\n")

        val result = analyzeProject(SettingsSnapshot())
        val todo = result.findings.single { it.analyzerId == TodoMarkerAnalyzer.ID }

        assertTrue(FindingNavigator.getInstance(project).navigate(todo))
        val openFiles = FileEditorManager.getInstance(project).openFiles
        assertTrue(openFiles.any { it.name == "Todo.kt" })
    }

    fun `test copy for ai markdown is built from analyzed findings`() {
        val file = myFixture.addFileToProject(
            "src/Service.kt",
            (1..25).joinToString("\n") { if (it == 10) "// TODO extract" else "line $it" },
        )

        val result = analyzeProject(SettingsSnapshot())
        val todo = result.findings.single { it.analyzerId == TodoMarkerAnalyzer.ID }

        val fileLines = TextLines.split(file.text)
        val snippet = CodeSnippetBuilder.build(
            fileLines,
            todo.location,
            CopySettings(contextLines = 1, maxCodeLines = 10),
        )
        val markdown = MarkdownAiFormatter.format(
            AiCopyRequest(project.name, "Project", listOf(AiCopyItem(todo, snippet))),
        )

        assertTrue(markdown.startsWith("## Repo Lens Finding"))
        assertTrue(markdown.contains("- Issue: TODO / FIXME"))
        assertTrue(markdown.contains("// TODO extract"))
        assertTrue(markdown.contains("line 9"))
        assertTrue(markdown.contains("line 11"))
        assertTrue(markdown.trimEnd().endsWith(MarkdownAiFormatter.REVIEW_PROMPT))
    }
}
