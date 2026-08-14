package com.kanicream.repolens.analysis.tier0

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * RL-F001: flags files whose physical line count exceeds the configured threshold.
 *
 * Tier 0 analyzer: works on any text file IntelliJ can read, no language PSI involved.
 * Physical lines include blank lines and comments (docs/design.md §7.1).
 */
class LargeFileAnalyzer : RepoLensAnalyzer {

    override val id: String = ID
    override val checkName: String = "Large File"

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val threshold = context.settings.largeFileLineThreshold
        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val lineCount = file.lineCount() ?: continue
            if (lineCount <= threshold) continue

            val location = SourceLocation(file.relativePath, startLine = 1, endLine = lineCount)
            findings += Finding(
                id = Finding.stableId(id, location),
                analyzerId = id,
                severity = Severity.WARNING,
                checkName = checkName,
                message = "File has $lineCount physical lines, exceeding the configured threshold of $threshold.",
                location = location,
                measuredValue = lineCount.toDouble(),
                threshold = threshold.toDouble(),
            )
        }
        return findings
    }

    companion object {
        const val ID: String = "RL-F001"
    }
}
