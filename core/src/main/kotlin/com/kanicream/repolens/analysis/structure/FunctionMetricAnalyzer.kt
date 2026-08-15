package com.kanicream.repolens.analysis.structure

import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import com.kanicream.repolens.structure.CodeDeclaration
import com.kanicream.repolens.structure.DeclarationKind
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Flags functions whose metric exceeds a configured threshold.
 *
 * A declaration whose metric is `null` is skipped rather than failed: it means the
 * structure provider did not compute that metric, which is expected for providers that
 * only deliver line ranges.
 */
abstract class FunctionMetricAnalyzer : RepoLensAnalyzer {

    protected abstract fun threshold(context: AnalysisContext): Int

    protected abstract fun metric(declaration: CodeDeclaration): Int?

    /** Reason line for the finding, e.g. `... has 9 parameters, exceeding ... of 7.` */
    protected abstract fun message(value: Int, threshold: Int): String

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val threshold = threshold(context)
        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val functions = file.structure()?.ofKind(DeclarationKind.FUNCTION) ?: continue
            functions.forEach { declaration ->
                val value = metric(declaration) ?: return@forEach
                if (value > threshold) {
                    val location = SourceLocation(file.relativePath, declaration.startLine, declaration.endLine)
                    findings += Finding(
                        id = Finding.stableId(id, location),
                        analyzerId = id,
                        severity = Severity.WARNING,
                        checkName = checkName,
                        message = message(value, threshold),
                        location = location,
                        symbol = SymbolInfo(declaration.displayName),
                        measuredValue = value.toDouble(),
                        threshold = threshold.toDouble(),
                    )
                }
            }
        }
        return findings
    }
}

/** RL-M002: functions declaring more parameters than the threshold allows. */
class ParameterCountAnalyzer : FunctionMetricAnalyzer() {

    override val id: String = ID
    override val checkName: String = "Too Many Parameters"

    override fun threshold(context: AnalysisContext): Int = context.settings.parameterCountThreshold

    override fun metric(declaration: CodeDeclaration): Int? = declaration.parameterCount

    override fun message(value: Int, threshold: Int): String =
        "This method declares $value parameters, exceeding the configured threshold of $threshold."

    companion object {
        const val ID: String = "RL-M002"
    }
}

/** RL-M003: functions whose branching/looping constructs nest deeper than the threshold. */
class DeepNestingAnalyzer : FunctionMetricAnalyzer() {

    override val id: String = ID
    override val checkName: String = "Deep Nesting"

    override fun threshold(context: AnalysisContext): Int = context.settings.nestingDepthThreshold

    override fun metric(declaration: CodeDeclaration): Int? = declaration.maxNestingDepth

    override fun message(value: Int, threshold: Int): String =
        "This method nests control flow $value levels deep, exceeding the configured threshold of $threshold."

    companion object {
        const val ID: String = "RL-M003"
    }
}
