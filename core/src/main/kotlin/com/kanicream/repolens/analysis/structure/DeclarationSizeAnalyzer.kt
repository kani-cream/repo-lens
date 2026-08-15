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
 * Flags declarations whose body exceeds a configured line count.
 *
 * Shared by the type and function checks: both measure physical body lines against a
 * threshold, and differ only in which declarations they look at and what they are
 * called. Files without a structure provider yield nothing, which is the expected
 * outcome for languages Repo Lens cannot parse yet.
 */
abstract class DeclarationSizeAnalyzer(
    private val kind: DeclarationKind,
    private val subject: String,
) : RepoLensAnalyzer {

    /** Line budget for this run; read from the settings snapshot. */
    protected abstract fun threshold(context: AnalysisContext): Int

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        val threshold = threshold(context)
        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val declarations = file.structure()?.ofKind(kind) ?: continue
            declarations.forEach { declaration ->
                if (declaration.bodyLineCount > threshold) {
                    findings += toFinding(file.relativePath, declaration, threshold)
                }
            }
        }
        return findings
    }

    private fun toFinding(path: String, declaration: CodeDeclaration, threshold: Int): Finding {
        val location = SourceLocation(path, declaration.startLine, declaration.endLine)
        return Finding(
            id = Finding.stableId(id, location),
            analyzerId = id,
            severity = Severity.WARNING,
            checkName = checkName,
            message = "This $subject has ${declaration.bodyLineCount} body lines, " +
                "exceeding the configured threshold of $threshold.",
            location = location,
            symbol = SymbolInfo(declaration.displayName),
            measuredValue = declaration.bodyLineCount.toDouble(),
            threshold = threshold.toDouble(),
        )
    }
}

/**
 * RL-C001: types (classes, interfaces, objects, structs) with an oversized body.
 * The check ID keeps its historical name; the display name is language-neutral.
 */
class LargeClassAnalyzer : DeclarationSizeAnalyzer(DeclarationKind.TYPE, "type") {

    override val id: String = ID
    override val checkName: String = "Large Type"

    override fun threshold(context: AnalysisContext): Int = context.settings.largeClassLineThreshold

    companion object {
        const val ID: String = "RL-C001"
    }
}

/** RL-M001: functions and methods with an oversized body. */
class LargeMethodAnalyzer : DeclarationSizeAnalyzer(DeclarationKind.FUNCTION, "function or method") {

    override val id: String = ID
    override val checkName: String = "Large Function / Method"

    override fun threshold(context: AnalysisContext): Int = context.settings.largeMethodLineThreshold

    companion object {
        const val ID: String = "RL-M001"
    }
}
