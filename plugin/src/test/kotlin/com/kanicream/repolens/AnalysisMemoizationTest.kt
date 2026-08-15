package com.kanicream.repolens

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.platform.ResolvedScope
import com.kanicream.repolens.platform.VfsAnalysisContext
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/**
 * One analysis run must resolve the scope once and parse each file's structure once,
 * however many analyzers ask — re-walking the project or re-parsing per analyzer would
 * multiply the most expensive steps (pre-v0.2 hardening).
 */
class AnalysisMemoizationTest : BasePlatformTestCase() {

    private class CountingProvider : CodeStructureProvider {
        val calls = AtomicInteger()
        override fun supports(project: Project, file: VirtualFile): Boolean =
            file.name.endsWith(".txt")

        override fun structure(project: Project, file: VirtualFile): CodeStructure? {
            calls.incrementAndGet()
            return CodeStructure.EMPTY
        }
    }

    private fun newContext(): VfsAnalysisContext = VfsAnalysisContext(
        project,
        AnalysisRequest(AnalysisScopeType.PROJECT, SettingsSnapshot()),
        ResolvedScope.WholeProject,
    )

    private fun <T> onPooledThread(action: suspend () -> T): T {
        val future = com.intellij.openapi.application.ApplicationManager.getApplication()
            .executeOnPooledThread<T> { runBlocking { action() } }
        return PlatformTestUtil.waitForFuture(future, 60_000)
    }

    fun `test scope files are listed once per analysis run`() {
        myFixture.addFileToProject("src/a.txt", "content\n")
        val context = newContext()

        val first = onPooledThread { context.files() }
        val second = onPooledThread { context.files() }

        assertSame("files() must be memoized for the run", first, second)
    }

    fun `test structure is parsed once per file however many analyzers ask`() {
        val provider = CountingProvider()
        CodeStructureProvider.EP_NAME.point.registerExtension(provider, testRootDisposable)
        myFixture.addFileToProject("src/a.txt", "content\n")

        val context = newContext()
        onPooledThread {
            val file = context.files().single { it.relativePath.endsWith("a.txt") }
            repeat(4) { file.structure() }
            file.structure()
        }

        assertEquals(1, provider.calls.get())
    }

    fun `test a fresh analysis run parses again`() {
        val provider = CountingProvider()
        CodeStructureProvider.EP_NAME.point.registerExtension(provider, testRootDisposable)
        myFixture.addFileToProject("src/a.txt", "content\n")

        onPooledThread { newContext().files().single { it.relativePath.endsWith("a.txt") }.structure() }
        onPooledThread { newContext().files().single { it.relativePath.endsWith("a.txt") }.structure() }

        assertEquals("memoization must not outlive a run", 2, provider.calls.get())
    }
}
