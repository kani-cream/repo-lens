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
            // Test sources import everything and reuse production package names, which
            // fabricates coupling the production code does not have.
            if (file.isTestSource()) continue
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

    private fun toFinding(component: List<String>, graph: PackageGraph): Finding {
        // A strongly connected component and a simple cycle are not the same thing: not
        // every member necessarily sits on one loop. The finding models the SCC (that is
        // the review unit) and shows one *real* cycle as the representative, so the path
        // and its evidence only ever contain edges that exist.
        val path = representativeCycle(component.toSet(), graph)
        val pathText = path.joinToString(" → ")

        // Anchor on the first edge of the representative cycle, so navigation lands on
        // an import that is actually part of it.
        val anchor = checkNotNull(graph.edge(path[0], path[1])) { "representative edge must exist" }
        val location = SourceLocation(anchor.filePath, anchor.line, anchor.line)

        val evidence = path.zipWithNext().map { (from, to) ->
            val edge = checkNotNull(graph.edge(from, to)) { "representative edge must exist" }
            "$from → $to (${edge.filePath}:${edge.line})"
        }

        return Finding(
            // Derived from the member packages, not from a location: the same group must
            // keep the same ID however the code moves, or ignoring it stops working.
            id = "$id:${component.joinToString(",")}",
            analyzerId = id,
            severity = Severity.WARNING,
            checkName = checkName,
            message = "These ${component.size} packages form one strongly connected dependency group: " +
                "${component.joinToString(", ")}. Representative cycle: $pathText. " +
                "Cycles couple the packages into one unit for change, testing, and reuse.",
            location = location,
            measuredValue = component.size.toDouble(),
            metadata = mapOf(
                METADATA_CYCLE_PATH to pathText,
                METADATA_MEMBERS to component.joinToString(", "),
                METADATA_EVIDENCE to evidence.joinToString("\n"),
            ),
        )
    }

    /**
     * One cycle that actually exists inside the component: the shortest walk (BFS over
     * existing edges, members only) from the alphabetically first member back to itself.
     * An SCC of size > 1 guarantees such a walk. Deterministic, so the path is stable
     * across runs.
     */
    private fun representativeCycle(members: Set<String>, graph: PackageGraph): List<String> {
        val start = members.min()
        val parent = mutableMapOf<String, String>()
        val queue = ArrayDeque<String>()

        graph.successors(start).filter { it in members }.sorted().forEach { next ->
            if (next == start) return listOf(start, start) // self-loop
            if (next !in parent) {
                parent[next] = start
                queue.addLast(next)
            }
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in graph.successors(current).filter { it in members }.sorted()) {
                if (next == start) {
                    val path = mutableListOf(start)
                    var walk = current
                    val reversed = mutableListOf<String>()
                    while (walk != start) {
                        reversed += walk
                        walk = parent.getValue(walk)
                    }
                    path += reversed.asReversed()
                    path += start
                    return path
                }
                if (next !in parent) {
                    parent[next] = current
                    queue.addLast(next)
                }
            }
        }
        error("strongly connected component must contain a cycle through $start")
    }

    companion object {
        const val ID: String = "RL-D001"

        /** Metadata key: the representative cycle as `a → b → c → a`. */
        const val METADATA_CYCLE_PATH: String = "cycle.path"

        /** Metadata key: every package in the strongly connected group, comma-separated. */
        const val METADATA_MEMBERS: String = "cycle.members"

        /** Metadata key: one `from → to (file:line)` line per edge of the cycle. */
        const val METADATA_EVIDENCE: String = "cycle.evidence"
    }
}
