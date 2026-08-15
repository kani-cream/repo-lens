package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.text.TextLines
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * RL-G001: files whose change against the diff base exceeds the configured size.
 *
 * Tier 0 and language-independent: the metric comes from the VCS, not from parsing.
 * Files without change info (non-diff scopes, or no VCS) simply produce nothing, so the
 * analyzer is harmless outside the Branch Diff scope.
 */
class LargeDiffAnalyzer : RepoLensAnalyzer {

    override val id: String = ID
    override val checkName: String = "Large Diff"

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val threshold = context.settings.largeDiffChangedLineThreshold
        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val change = file.changeInfo() ?: continue
            if (change.totalChangedLines <= threshold) continue

            val endLine = file.lineCount()?.coerceAtLeast(1) ?: 1
            val location = SourceLocation(file.relativePath, 1, endLine)
            findings += Finding(
                id = Finding.stableId(id, location),
                analyzerId = id,
                severity = Severity.WARNING,
                checkName = checkName,
                message = "This ${change.status.displayName} file changes " +
                    "${change.totalChangedLines} lines against the base " +
                    "(+${change.addedLines} −${change.deletedLines}), " +
                    "exceeding the configured threshold of $threshold.",
                location = location,
                measuredValue = change.totalChangedLines.toDouble(),
                threshold = threshold.toDouble(),
                metadata = mapOf(
                    METADATA_STATUS to change.status.displayName,
                    METADATA_ADDED to change.addedLines.toString(),
                    METADATA_DELETED to change.deletedLines.toString(),
                ),
            )
        }
        return findings
    }

    companion object {
        const val ID: String = "RL-G001"

        const val METADATA_STATUS: String = "diff.status"
        const val METADATA_ADDED: String = "diff.added"
        const val METADATA_DELETED: String = "diff.deleted"
    }
}
