package com.kanicream.repolens.enrich

import com.kanicream.repolens.analysis.structure.CircularDependencyAnalyzer
import com.kanicream.repolens.analysis.structure.DeepNestingAnalyzer
import com.kanicream.repolens.analysis.structure.LargeClassAnalyzer
import com.kanicream.repolens.analysis.structure.LargeMethodAnalyzer
import com.kanicream.repolens.analysis.structure.ParameterCountAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation

/**
 * RL-H001: files that are both structurally noteworthy and frequently changed.
 *
 * The initial definition from docs/milestones/v0.3.md: change frequency high AND at
 * least one structural finding present. The score is deliberately naive — commits
 * multiplied by structural findings — because it must stay explainable: every component
 * is named in the message and the metadata, and the Detail pane can show exactly why a
 * file scored what it scored. A black-box risk model is an explicit non-goal.
 *
 * Runs after analysis and enrichment, combining their outputs; it is not an analyzer
 * over file content.
 */
object HotspotDetector {

    const val ID: String = "RL-H001"
    const val CHECK_NAME: String = "Hotspot"

    const val METADATA_COMMITS: String = "hotspot.commits"
    const val METADATA_AUTHORS: String = "hotspot.authors"
    const val METADATA_STRUCTURAL: String = "hotspot.structuralFindings"
    const val METADATA_CHECKS: String = "hotspot.checks"
    const val METADATA_SCORE: String = "hotspot.score"

    /** Checks that count as structural evidence for a hotspot. */
    private val STRUCTURAL_ANALYZER_IDS = setOf(
        LargeClassAnalyzer.ID,
        LargeMethodAnalyzer.ID,
        ParameterCountAnalyzer.ID,
        DeepNestingAnalyzer.ID,
        CircularDependencyAnalyzer.ID,
    )

    fun detect(
        findings: List<Finding>,
        historyByPath: Map<String, FileHistory>,
        minCommits: Int,
        historyWindowDays: Int,
    ): List<Finding> {
        val structuralByPath = findings
            .filter { it.analyzerId in STRUCTURAL_ANALYZER_IDS }
            .groupBy { it.location.filePath }

        return structuralByPath.mapNotNull { (path, structural) ->
            val history = historyByPath[path] ?: return@mapNotNull null
            if (history.commitCount < minCommits) return@mapNotNull null

            val score = history.commitCount * structural.size
            val checks = structural.map { it.checkName }.distinct().sorted()
            val location = SourceLocation(path, 1, 1)
            Finding(
                id = "$ID:$path",
                analyzerId = ID,
                severity = Severity.WARNING,
                checkName = CHECK_NAME,
                message = "This file changed ${history.commitCount} time(s) by " +
                    "${history.authorCount} author(s) in the last $historyWindowDays days " +
                    "and carries ${structural.size} structural finding(s) " +
                    "(${checks.joinToString(", ")}). " +
                    "Score ${history.commitCount} × ${structural.size} = $score. " +
                    "Frequently changed code with structural findings is where review " +
                    "attention pays off most.",
                location = location,
                measuredValue = score.toDouble(),
                metadata = mapOf(
                    METADATA_COMMITS to history.commitCount.toString(),
                    METADATA_AUTHORS to history.authorCount.toString(),
                    METADATA_STRUCTURAL to structural.size.toString(),
                    METADATA_CHECKS to checks.joinToString(", "),
                    METADATA_SCORE to score.toString(),
                ),
            )
        }.sortedByDescending { it.measuredValue }
    }
}
