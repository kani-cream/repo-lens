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
import com.kanicream.repolens.analysis.ProjectAnalyzers
import com.kanicream.repolens.structure.ProviderCapabilities

/** Project settings UI: per-check toggles, thresholds, exclusions, and copy limits. */
class RepoLensConfigurable(private val project: Project) : BoundConfigurable("Repo Lens") {

    private val state: RepoLensSettings.State = RepoLensSettings.getInstance(project).getState()

    override fun createPanel(): DialogPanel = panel {
        group("Language Capabilities") {
            row {
                comment(
                    "Universal checks (Large File, TODO / FIXME) run for every text " +
                        "language. Structure checks need a language provider:",
                )
            }
            ProviderCapabilities.current().forEach { capability ->
                row {
                    val mark = if (capability.available) "✓" else "—"
                    label("$mark  ${capability.displayName}")
                    if (!capability.available) {
                        comment("Unavailable: ${capability.requirement}")
                    }
                }
            }
        }
        group("Checks") {
            ProjectAnalyzers.all(project).forEach { analyzer ->
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
            row("Large type threshold (body lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeClassLineThreshold)
            }
            row("Large function / method threshold (body lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeMethodLineThreshold)
            }
            row("Parameter count threshold:") {
                intTextField(range = 1..1_000).bindIntText(state::parameterCountThreshold)
            }
            row("Nesting depth threshold:") {
                intTextField(range = 1..100).bindIntText(state::nestingDepthThreshold)
            }
            row("Large diff threshold (changed lines):") {
                intTextField(range = 1..1_000_000).bindIntText(state::largeDiffChangedLineThreshold)
            }
        }
        group("Branch Diff") {
            row("Base branch:") {
                textField()
                    .columns(24)
                    .bindText(state::baseBranch)
                    .comment("Blank auto-detects origin/main, origin/master, main, master.")
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
        group("Suppression") {
            row {
                textArea()
                    .rows(5)
                    .align(AlignX.FILL)
                    .bindText(
                        getter = { state.suppressRuleLines.joinToString("\n") },
                        setter = { text -> state.suppressRuleLines = parsePatterns(text) },
                    )
                    .comment(
                        "One rule per line: check-id | path-glob | symbol-glob. " +
                            "Empty segments do not restrict; lines starting with # are comments. " +
                            "Example: RL-M001 | **/*_test.go suppresses Large Function / Method in Go tests.",
                    )
            }.resizableRow()
            row {
                comment("Individually ignored findings: ${state.ignoredFindingIds.size}")
                button("Clear Ignored Findings") {
                    state.ignoredFindingIds.clear()
                }
            }
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
