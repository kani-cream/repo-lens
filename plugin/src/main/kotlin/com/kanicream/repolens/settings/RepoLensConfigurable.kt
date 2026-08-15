package com.kanicream.repolens.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.kanicream.repolens.analysis.DefaultAnalyzers

/** Project settings UI: per-check toggles, thresholds, exclusions, and copy limits. */
class RepoLensConfigurable(project: Project) : BoundConfigurable("Repo Lens") {

    private val state: RepoLensSettings.State = RepoLensSettings.getInstance(project).getState()

    override fun createPanel(): DialogPanel = panel {
        group("Checks") {
            DefaultAnalyzers.create().forEach { analyzer ->
                row {
                    checkBox("${analyzer.checkName} (${analyzer.id})")
                        .bindSelected(
                            getter = { analyzer.id !in state.disabledAnalyzerIds },
                            setter = { enabled ->
                                if (enabled) {
                                    state.disabledAnalyzerIds.remove(analyzer.id)
                                } else if (analyzer.id !in state.disabledAnalyzerIds) {
                                    state.disabledAnalyzerIds.add(analyzer.id)
                                }
                            },
                        )
                }
            }
            row("TODO markers:") {
                textField()
                    .columns(30)
                    .bindText(
                        getter = { state.todoMarkers.joinToString(", ") },
                        setter = { text -> state.todoMarkers = parseMarkers(text) },
                    )
                    .comment("Comma separated. Markers are matched as whole words, case-insensitively.")
            }
        }
        group("Thresholds") {
            row("Large file threshold (lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeFileLineThreshold)
            }
            row("Large class threshold (body lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeClassLineThreshold)
            }
            row("Large method threshold (body lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeMethodLineThreshold)
            }
            row("Parameter count threshold:") {
                intTextField(range = 1..1_000).bindIntText(state::parameterCountThreshold)
            }
            row("Nesting depth threshold:") {
                intTextField(range = 1..100).bindIntText(state::nestingDepthThreshold)
            }
        }
        group("Exclusions") {
            row {
                textArea()
                    .rows(8)
                    .align(AlignX.FILL)
                    .bindText(
                        getter = { state.excludePatterns.joinToString("\n") },
                        setter = { text -> state.excludePatterns = parsePatterns(text) },
                    )
                    .comment(
                        "One glob per line, matched against project-relative paths. " +
                            "Use a double star to cross directories, for example **/.venv/**. " +
                            "Patterns that fail to compile are ignored.",
                    )
            }.resizableRow()
        }
        group("Copy") {
            row("Context lines:") {
                intTextField(range = 0..100).bindIntText(state::copyContextLines)
            }
            row("Max code lines:") {
                intTextField(range = 1..1_000).bindIntText(state::copyMaxCodeLines)
            }
        }
    }

    private fun parseMarkers(text: String): MutableList<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

    private fun parsePatterns(text: String): MutableList<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
}
