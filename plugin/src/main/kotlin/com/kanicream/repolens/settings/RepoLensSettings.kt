package com.kanicream.repolens.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.kanicream.repolens.model.CopySettings
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.scope.PathExclusions
import com.kanicream.repolens.suppression.SuppressRule
import com.kanicream.repolens.suppression.SuppressionPolicy

/**
 * Project-level persistent settings. Analysis code never reads this component directly;
 * it works on an immutable [SettingsSnapshot] taken when a run starts.
 */
@Service(Service.Level.PROJECT)
@State(name = "RepoLensSettings", storages = [Storage("repoLens.xml")])
class RepoLensSettings : PersistentStateComponent<RepoLensSettings.State> {

    class State {
        var largeFileLineThreshold: Int = SettingsSnapshot.DEFAULT_LARGE_FILE_LINE_THRESHOLD
        var largeClassLineThreshold: Int = SettingsSnapshot.DEFAULT_LARGE_CLASS_LINE_THRESHOLD
        var largeMethodLineThreshold: Int = SettingsSnapshot.DEFAULT_LARGE_METHOD_LINE_THRESHOLD
        var parameterCountThreshold: Int = SettingsSnapshot.DEFAULT_PARAMETER_COUNT_THRESHOLD
        var nestingDepthThreshold: Int = SettingsSnapshot.DEFAULT_NESTING_DEPTH_THRESHOLD
        var todoMarkers: MutableList<String> = SettingsSnapshot.DEFAULT_TODO_MARKERS.toMutableList()
        var disabledAnalyzerIds: MutableList<String> = mutableListOf()
        var excludePatterns: MutableList<String> = PathExclusions.DEFAULT_PATTERNS.toMutableList()
        var ignoredFindingIds: MutableList<String> = mutableListOf()
        var suppressRuleLines: MutableList<String> = mutableListOf()
        var copyContextLines: Int = CopySettings.DEFAULT_CONTEXT_LINES
        var copyMaxCodeLines: Int = CopySettings.DEFAULT_MAX_CODE_LINES
    }

    private val current = State()

    override fun getState(): State = current

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, current)
    }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        largeFileLineThreshold = current.largeFileLineThreshold,
        largeClassLineThreshold = current.largeClassLineThreshold,
        largeMethodLineThreshold = current.largeMethodLineThreshold,
        parameterCountThreshold = current.parameterCountThreshold,
        nestingDepthThreshold = current.nestingDepthThreshold,
        todoMarkers = current.todoMarkers.toList(),
        disabledAnalyzerIds = current.disabledAnalyzerIds.toSet(),
        excludePatterns = current.excludePatterns.toList(),
        copy = CopySettings(
            contextLines = current.copyContextLines,
            maxCodeLines = current.copyMaxCodeLines,
        ),
    )

    /** The suppression view policy; not part of [SettingsSnapshot] because analysis ignores it. */
    fun suppressionPolicy(): SuppressionPolicy = SuppressionPolicy(
        ignoredFindingIds = current.ignoredFindingIds.toSet(),
        rules = SuppressRule.parseAll(current.suppressRuleLines),
    )

    fun setFindingIgnored(findingId: String, ignored: Boolean) {
        if (ignored) {
            if (findingId !in current.ignoredFindingIds) current.ignoredFindingIds.add(findingId)
        } else {
            current.ignoredFindingIds.remove(findingId)
        }
    }

    fun isFindingIgnored(findingId: String): Boolean = findingId in current.ignoredFindingIds

    companion object {
        fun getInstance(project: Project): RepoLensSettings = project.service()
    }
}
