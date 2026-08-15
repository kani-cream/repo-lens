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
import com.kanicream.repolens.RepoLensBundle
import com.kanicream.repolens.analysis.ProjectAnalyzers
import com.kanicream.repolens.structure.ProviderCapabilities

/** Project settings UI: per-check toggles, thresholds, exclusions, and copy limits. */
class RepoLensConfigurable(private val project: Project) : BoundConfigurable("Repo Lens") {

    private val state: RepoLensSettings.State = RepoLensSettings.getInstance(project).getState()

    override fun createPanel(): DialogPanel = panel {
        group(RepoLensBundle.message("settings.group.capabilities")) {
            row {
                comment(RepoLensBundle.message("settings.capabilities.intro"))
            }
            ProviderCapabilities.current().forEach { capability ->
                row {
                    val mark = if (capability.available) "✓" else "—"
                    label("$mark  ${capability.displayName}")
                    if (!capability.available) {
                        comment(RepoLensBundle.message("settings.capabilities.unavailable", capability.requirement))
                    }
                }
            }
        }
        group(RepoLensBundle.message("settings.group.checks")) {
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
            row(RepoLensBundle.message("settings.todo.markers")) {
                textField()
                    .columns(30)
                    .bindText(
                        getter = { state.todoMarkers.joinToString(", ") },
                        setter = { text -> state.todoMarkers = parseMarkers(text) },
                    )
                    .comment(RepoLensBundle.message("settings.todo.markers.comment"))
            }
        }
        group(RepoLensBundle.message("settings.group.thresholds")) {
            row(RepoLensBundle.message("settings.threshold.large.file")) {
                intTextField(range = 1..1_000_000).bindIntText(state::largeFileLineThreshold)
            }
            row(RepoLensBundle.message("settings.threshold.large.type")) {
                intTextField(range = 1..1_000_000).bindIntText(state::largeClassLineThreshold)
            }
            row(RepoLensBundle.message("settings.threshold.large.function")) {
                intTextField(range = 1..1_000_000).bindIntText(state::largeMethodLineThreshold)
            }
            row(RepoLensBundle.message("settings.threshold.parameters")) {
                intTextField(range = 1..1_000).bindIntText(state::parameterCountThreshold)
            }
            row(RepoLensBundle.message("settings.threshold.nesting")) {
                intTextField(range = 1..100).bindIntText(state::nestingDepthThreshold)
            }
            row(RepoLensBundle.message("settings.threshold.large.diff")) {
                intTextField(range = 1..1_000_000).bindIntText(state::largeDiffChangedLineThreshold)
            }
        }
        group(RepoLensBundle.message("settings.group.git")) {
            row(RepoLensBundle.message("settings.git.base.branch")) {
                textField()
                    .columns(24)
                    .bindText(state::baseBranch)
                    .comment(RepoLensBundle.message("settings.git.base.branch.comment"))
            }
            row(RepoLensBundle.message("settings.git.history.days")) {
                intTextField(range = 1..3650).bindIntText(state::gitHistoryDays)
                    .comment(RepoLensBundle.message("settings.git.history.days.comment"))
            }
            row(RepoLensBundle.message("settings.git.long.lived.days")) {
                intTextField(range = 1..3650).bindIntText(state::longLivedTodoDays)
            }
            row(RepoLensBundle.message("settings.git.hotspot.commits")) {
                intTextField(range = 1..10_000).bindIntText(state::hotspotMinCommits)
                    .comment(RepoLensBundle.message("settings.git.hotspot.commits.comment"))
            }
        }
        group(RepoLensBundle.message("settings.group.exclusions")) {
            row {
                textArea()
                    .rows(8)
                    .align(AlignX.FILL)
                    .bindText(
                        getter = { state.excludePatterns.joinToString("\n") },
                        setter = { text -> state.excludePatterns = parsePatterns(text) },
                    )
                    .comment(RepoLensBundle.message("settings.exclusions.comment"))
            }.resizableRow()
        }
        group(RepoLensBundle.message("settings.group.suppression")) {
            row {
                textArea()
                    .rows(5)
                    .align(AlignX.FILL)
                    .bindText(
                        getter = { state.suppressRuleLines.joinToString("\n") },
                        setter = { text -> state.suppressRuleLines = parsePatterns(text) },
                    )
                    .comment(RepoLensBundle.message("settings.suppression.comment"))
            }.resizableRow()
            row {
                comment(RepoLensBundle.message("settings.suppression.ignored.count", state.ignoredFindingIds.size))
                button(RepoLensBundle.message("settings.suppression.clear")) {
                    state.ignoredFindingIds.clear()
                }
            }
        }
        group(RepoLensBundle.message("settings.group.copy")) {
            row(RepoLensBundle.message("settings.copy.context.lines")) {
                intTextField(range = 0..100).bindIntText(state::copyContextLines)
            }
            row(RepoLensBundle.message("settings.copy.max.lines")) {
                intTextField(range = 1..1_000).bindIntText(state::copyMaxCodeLines)
            }
        }
    }

    private fun parseMarkers(text: String): MutableList<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

    private fun parsePatterns(text: String): MutableList<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
}
