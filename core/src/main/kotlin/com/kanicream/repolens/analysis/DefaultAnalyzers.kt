package com.kanicream.repolens.analysis

import com.kanicream.repolens.analysis.structure.DeepNestingAnalyzer
import com.kanicream.repolens.analysis.structure.LargeClassAnalyzer
import com.kanicream.repolens.analysis.structure.LargeMethodAnalyzer
import com.kanicream.repolens.analysis.structure.ParameterCountAnalyzer
import com.kanicream.repolens.analysis.tier0.LargeFileAnalyzer
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer

/**
 * The built-in analyzer set, in the order they are listed to the user. The analysis
 * service builds its registry from this, and the settings UI derives its per-analyzer
 * checkboxes from it, so the two can never drift apart.
 */
object DefaultAnalyzers {
    fun create(): List<RepoLensAnalyzer> = listOf(
        LargeFileAnalyzer(),
        TodoMarkerAnalyzer(),
        LargeClassAnalyzer(),
        LargeMethodAnalyzer(),
        ParameterCountAnalyzer(),
        DeepNestingAnalyzer(),
    )
}
