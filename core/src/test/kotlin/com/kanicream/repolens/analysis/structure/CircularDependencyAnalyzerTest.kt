package com.kanicream.repolens.analysis.structure

import com.kanicream.repolens.analysis.InMemoryAnalysisContext
import com.kanicream.repolens.analysis.InMemoryFile
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.PackageImport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CircularDependencyAnalyzerTest {

    private val analyzer = CircularDependencyAnalyzer()

    private fun file(
        path: String,
        packageName: String,
        vararg imports: Pair<String, Int>,
    ) = InMemoryFile(
        path,
        structure = CodeStructure(
            declarations = emptyList(),
            packageName = packageName,
            imports = imports.map { PackageImport(it.first, it.second) },
        ),
    )

    @Test
    fun `detects a three package cycle with path and evidence`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                file("src/a/A.java", "app.a", "app.b.B" to 3),
                file("src/b/B.java", "app.b", "app.c.C" to 4),
                file("src/c/C.java", "app.c", "app.a.A" to 5),
            ),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals("RL-D001", finding.analyzerId)
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals(3.0, finding.measuredValue)
        assertEquals(
            "app.a → app.b → app.c → app.a",
            finding.metadata[CircularDependencyAnalyzer.METADATA_CYCLE_PATH],
        )
        // Anchored on the import that leaves the first package of the path.
        assertEquals("src/a/A.java", finding.location.filePath)
        assertEquals(3, finding.location.startLine)
        val evidence = finding.metadata[CircularDependencyAnalyzer.METADATA_EVIDENCE].orEmpty()
        assertTrue("app.b → app.c (src/b/B.java:4)" in evidence.lines(), evidence)
    }

    @Test
    fun `scc members not on one simple cycle never fabricate edges`() = runTest {
        // A -> B, B -> C, C -> B, B -> D, D -> A : one SCC {A,B,C,D}, but no single
        // simple cycle visits all four. The old greedy walk emitted A -> B -> C -> A,
        // whose closing edge does not exist.
        val context = InMemoryAnalysisContext(
            listOf(
                file("A.java", "app.a", "app.b.B" to 1),
                file("B.java", "app.b", "app.c.C" to 1, "app.d.D" to 2),
                file("C.java", "app.c", "app.b.B" to 1),
                file("D.java", "app.d", "app.a.A" to 1),
            ),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        val finding = findings.single()
        // The SCC is the finding unit: all four members, stable ID over all of them.
        assertEquals(4.0, finding.measuredValue)
        assertEquals("RL-D001:app.a,app.b,app.c,app.d", finding.id)
        assertEquals(
            "app.a, app.b, app.c, app.d",
            finding.metadata[CircularDependencyAnalyzer.METADATA_MEMBERS],
        )
        // The representative is a real cycle: the shortest loop through app.a.
        assertEquals(
            "app.a → app.b → app.d → app.a",
            finding.metadata[CircularDependencyAnalyzer.METADATA_CYCLE_PATH],
        )
        // Every path edge exists, so evidence covers each step exactly once.
        val evidence = finding.metadata[CircularDependencyAnalyzer.METADATA_EVIDENCE].orEmpty().lines()
        assertEquals(3, evidence.size)
        assertTrue(evidence.any { it.startsWith("app.a → app.b") }, evidence.toString())
        assertTrue(evidence.any { it.startsWith("app.b → app.d") }, evidence.toString())
        assertTrue(evidence.any { it.startsWith("app.d → app.a") }, evidence.toString())
    }

    @Test
    fun `acyclic graphs produce nothing`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                file("A.java", "app.a", "app.b.B" to 1),
                file("B.java", "app.b", "app.c.C" to 1),
                file("C.java", "app.c"),
            ),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }

    @Test
    fun `imports outside the project never create edges`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                file("A.java", "app.a", "java.util.List" to 1, "com.vendor.sdk.Client" to 2, "app.b.B" to 3),
                file("B.java", "app.b", "java.util.Map" to 1, "app.a.A" to 2),
            ),
        )

        val findings = analyzer.analyze(context)

        // Only the two project packages participate; the JDK/vendor imports are noise.
        assertEquals(1, findings.size)
        assertEquals(2.0, findings.single().measuredValue)
    }

    @Test
    fun `two independent cycles yield two findings`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                file("A.java", "app.a", "app.b.B" to 1),
                file("B.java", "app.b", "app.a.A" to 1),
                file("X.java", "lib.x", "lib.y.Y" to 1),
                file("Y.java", "lib.y", "lib.x.X" to 1),
            ),
        )

        val findings = analyzer.analyze(context)

        assertEquals(2, findings.size)
        assertEquals(
            listOf("RL-D001:app.a,app.b", "RL-D001:lib.x,lib.y"),
            findings.map { it.id }.sorted(),
        )
    }

    @Test
    fun `finding id is stable across file order and line changes`() = runTest {
        val first = analyzer.analyze(
            InMemoryAnalysisContext(
                listOf(
                    file("A.java", "app.a", "app.b.B" to 3),
                    file("B.java", "app.b", "app.a.A" to 4),
                ),
            ),
        ).single()
        val second = analyzer.analyze(
            InMemoryAnalysisContext(
                listOf(
                    file("B.java", "app.b", "app.a.A" to 99),
                    file("A.java", "app.a", "app.b.B" to 42),
                ),
            ),
        ).single()

        assertEquals(first.id, second.id)
    }

    @Test
    fun `imports into a nested known package resolve to the deepest match`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                file("A.java", "app", "app.sub.deep.Thing" to 1),
                file("D.java", "app.sub.deep", "app.Top" to 1),
                file("S.java", "app.sub"),
            ),
        )

        val findings = analyzer.analyze(context)

        assertEquals(1, findings.size)
        assertEquals(
            "app → app.sub.deep → app",
            findings.single().metadata[CircularDependencyAnalyzer.METADATA_CYCLE_PATH],
        )
    }

    @Test
    fun `files without structure or package are skipped`() = runTest {
        val context = InMemoryAnalysisContext(
            listOf(
                InMemoryFile("notes.txt", text = "no structure"),
                file("A.java", "app.a"),
            ),
        )

        assertTrue(analyzer.analyze(context).isEmpty())
    }
}
