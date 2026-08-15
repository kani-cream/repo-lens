package com.kanicream.repolens.analysis.structure

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.structure.PackageImport
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * RL-D001: cycles in the package dependency graph (docs/design.md §17, milestone v0.2).
 *
 * Package granularity: an edge P → Q exists when a file in package P imports something
 * from a *project* package Q. Imports that resolve to no known project package (JDK,
 * libraries) never create edges, so the graph stays project-sized. Each strongly
 * connected component with a cycle produces one finding whose ID is derived from the
 * member packages — stable across runs, re-orderings, and line shifts, which keeps
 * ignore working.
 *
 * Languages without dot-namespace packages contribute nothing; Go forbids import
 * cycles at the compiler level, so its absence here loses nothing.
 */
class CircularDependencyAnalyzer : RepoLensAnalyzer {

    override val id: String = ID
    override val checkName: String = "Circular Dependency"

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val graph = buildGraph(context)
        return findCycles(graph).map { cycle -> toFinding(cycle, graph) }
    }

    // --- graph construction ---

    /** Edge evidence: the import that creates the edge, for navigation and detail. */
    private data class Edge(val from: String, val to: String, val filePath: String, val line: Int)

    private class PackageGraph {
        val packages = sortedSetOf<String>()
        val edges = mutableMapOf<String, MutableMap<String, Edge>>()

        fun addEdge(edge: Edge) {
            edges.getOrPut(edge.from) { mutableMapOf() }.putIfAbsent(edge.to, edge)
        }

        fun successors(from: String): Set<String> = edges[from]?.keys ?: emptySet()

        fun edge(from: String, to: String): Edge? = edges[from]?.get(to)
    }

    private suspend fun buildGraph(context: AnalysisContext): PackageGraph {
        val graph = PackageGraph()
        val filesWithPackages = mutableListOf<Triple<String, String, List<PackageImport>>>()

        for (file in context.files()) {
            coroutineContext.ensureActive()
            val structure = file.structure() ?: continue
            val packageName = structure.packageName?.takeIf { it.isNotEmpty() } ?: continue
            graph.packages += packageName
            filesWithPackages += Triple(file.relativePath, packageName, structure.imports)
        }

        for ((path, fromPackage, imports) in filesWithPackages) {
            coroutineContext.ensureActive()
            imports.forEach { import ->
                val target = resolveToKnownPackage(import.target, graph.packages)
                if (target != null && target != fromPackage) {
                    graph.addEdge(Edge(fromPackage, target, path, import.line))
                }
            }
        }
        return graph
    }

    /**
     * Longest known package that the import points into: equal to the import, or a
     * dot-separated prefix of it (a class import points into its package).
     */
    private fun resolveToKnownPackage(importTarget: String, known: Set<String>): String? =
        known
            .filter { pkg -> importTarget == pkg || importTarget.startsWith("$pkg.") }
            .maxByOrNull { it.length }

    // --- cycle detection (iterative Tarjan SCC) ---

    private fun findCycles(graph: PackageGraph): List<List<String>> {
        val index = mutableMapOf<String, Int>()
        val lowLink = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        var counter = 0
        val components = mutableListOf<List<String>>()

        for (start in graph.packages) {
            if (start in index) continue
            // Iterative DFS: (node, successor iterator) frames.
            val work = ArrayDeque<Pair<String, Iterator<String>>>()
            fun open(node: String) {
                index[node] = counter
                lowLink[node] = counter
                counter++
                stack.addLast(node)
                onStack += node
                work.addLast(node to graph.successors(node).iterator())
            }
            open(start)
            while (work.isNotEmpty()) {
                val (node, successors) = work.last()
                if (successors.hasNext()) {
                    val next = successors.next()
                    when {
                        next !in index -> open(next)
                        next in onStack ->
                            lowLink[node] = minOf(lowLink.getValue(node), index.getValue(next))
                    }
                } else {
                    work.removeLast()
                    work.lastOrNull()?.let { (parent, _) ->
                        lowLink[parent] = minOf(lowLink.getValue(parent), lowLink.getValue(node))
                    }
                    if (lowLink.getValue(node) == index.getValue(node)) {
                        val component = mutableListOf<String>()
                        while (true) {
                            val member = stack.removeLast()
                            onStack -= member
                            component += member
                            if (member == node) break
                        }
                        if (component.size > 1) components += component.sorted()
                    }
                }
            }
        }
        return components.sortedBy { it.first() }
    }

    // --- finding construction ---

    private fun toFinding(cycle: List<String>, graph: PackageGraph): Finding {
        val path = cyclePath(cycle, graph)
        val pathText = path.joinToString(" → ")

        // Anchor on the import that closes the loop from the first package in the path,
        // so navigation lands on a line that is actually part of the cycle.
        val anchor = graph.edge(path[0], path[1]) ?: error("cycle edge must exist")
        val location = SourceLocation(anchor.filePath, anchor.line, anchor.line)

        val evidence = path.zipWithNext().mapNotNull { (from, to) ->
            graph.edge(from, to)?.let { "$from → $to (${it.filePath}:${it.line})" }
        }

        return Finding(
            // Derived from the member packages, not from a location: the same cycle must
            // keep the same ID however the code moves, or ignoring it stops working.
            id = "$id:${cycle.joinToString(",")}",
            analyzerId = id,
            severity = Severity.WARNING,
            checkName = checkName,
            message = "These ${cycle.size} packages depend on each other in a cycle: $pathText. " +
                "Cycles couple the packages into one unit for change, testing, and reuse.",
            location = location,
            measuredValue = cycle.size.toDouble(),
            metadata = mapOf(
                METADATA_CYCLE_PATH to pathText,
                METADATA_EVIDENCE to evidence.joinToString("\n"),
            ),
        )
    }

    /** A concrete walk around the cycle, starting from the alphabetically first member. */
    private fun cyclePath(cycle: List<String>, graph: PackageGraph): List<String> {
        val members = cycle.toSet()
        val path = mutableListOf(cycle.first())
        val visited = mutableSetOf(cycle.first())
        while (true) {
            val current = path.last()
            val next = graph.successors(current)
                .filter { it in members }
                .sorted()
                .firstOrNull { it == path.first() || it !in visited }
                ?: return path + path.first() // defensive; SCC guarantees a way onward
            if (next == path.first()) return path + next
            path += next
            visited += next
        }
    }

    companion object {
        const val ID: String = "RL-D001"

        /** Metadata key: the cycle as `a → b → c → a`. */
        const val METADATA_CYCLE_PATH: String = "cycle.path"

        /** Metadata key: one `from → to (file:line)` line per edge of the cycle. */
        const val METADATA_EVIDENCE: String = "cycle.evidence"
    }
}
