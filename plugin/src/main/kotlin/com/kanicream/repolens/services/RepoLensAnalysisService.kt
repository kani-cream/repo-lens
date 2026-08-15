package com.kanicream.repolens.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.analysis.ProjectAnalyzers
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.enrich.GitEnrichment
import com.kanicream.repolens.enrich.HotspotDetector
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.vcs.GitHistoryProvider
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.platform.ScopeResolution
import com.kanicream.repolens.platform.ScopeResolver
import com.kanicream.repolens.platform.VfsAnalysisContext
import com.kanicream.repolens.settings.RepoLensSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Drives analysis runs: snapshots the settings, resolves the scope through platform
 * adapters, runs the orchestrator on a background coroutine under a cancellable progress
 * indicator, and publishes the outcome on the EDT.
 */
@Service(Service.Level.PROJECT)
class RepoLensAnalysisService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    private val orchestrator = AnalysisOrchestrator(AnalyzerRegistry(ProjectAnalyzers.all(project)))

    @Volatile
    private var currentJob: Job? = null

    /**
     * Starts a run for [scopeType], cancelling any run still in flight. Must be called on
     * the EDT: resolving Current File and Selected Files reads live UI state.
     *
     * [selectedFiles] carries the explicit selection for [AnalysisScopeType.SELECTED_FILES]
     * and is ignored by the other scopes.
     */
    fun startAnalysis(scopeType: AnalysisScopeType, selectedFiles: List<VirtualFile> = emptyList()) {
        stop()
        val publisher = project.messageBus.syncPublisher(RepoLensAnalysisListener.TOPIC)

        val settingsSnapshot = RepoLensSettings.getInstance(project).snapshot()
        when (
            val resolution =
                ScopeResolver.resolve(project, scopeType, selectedFiles, settingsSnapshot.baseBranch)
        ) {
            is ScopeResolution.Unavailable -> publisher.analysisFailed(resolution.reason)

            is ScopeResolution.Resolved -> {
                publisher.analysisStarted(scopeType)
                val request = AnalysisRequest(scopeType = scopeType, settings = settingsSnapshot)
                val context = VfsAnalysisContext(project, request, resolution.scope)
                currentJob = coroutineScope.launch {
                    try {
                        val result = withBackgroundProgress(project, progressTitle(scopeType)) {
                            enrichWithGitEvidence(orchestrator.analyze(context), settingsSnapshot)
                        }
                        logDiagnostics(scopeType, result)
                        onEdt { publish { it.analysisFinished(scopeType, result) } }
                    } catch (e: CancellationException) {
                        onEdt { publish { it.analysisCancelled() } }
                        throw e
                    } catch (e: com.kanicream.repolens.vcs.ScopeUnavailableException) {
                        // A reasoned, user-fixable condition - not an error worth a stack trace.
                        onEdt { publish { it.analysisFailed(e.message ?: "Scope unavailable") } }
                    } catch (e: Throwable) {
                        LOG.warn("Repo Lens analysis failed", e)
                        onEdt { publish { it.analysisFailed("Analysis failed: ${e.javaClass.simpleName}") } }
                    }
                }
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
    }

    /**
     * Attaches Git history evidence: one bounded log query for the whole run, and blame
     * only for files carrying TODO findings (capped, so a marker-heavy repository cannot
     * turn enrichment into a blame storm). Absent Git support or failed queries leave the
     * findings untouched - the milestone's required degradation.
     */
    private suspend fun enrichWithGitEvidence(
        result: AnalysisResult,
        settings: SettingsSnapshot,
    ): AnalysisResult {
        if (result.findings.isEmpty()) return result
        val provider = GitHistoryProvider.first() ?: return result
        val history = provider.repositoryHistory(project, settings.gitHistoryDays) ?: return result

        val todoFiles = result.findings.asSequence()
            .filter { it.analyzerId == TodoMarkerAnalyzer.ID }
            .map { it.location.filePath }
            .distinct()
            .take(MAX_BLAMED_FILES)
            .toList()
        val lineAges = LinkedHashMap<String, Map<Int, Long>>()
        for (path in todoFiles) {
            currentCoroutineContext().ensureActive()
            provider.lineAges(project, path)?.let { lineAges[path] = it }
        }

        val enriched = GitEnrichment.apply(
            findings = result.findings,
            historyByPath = history,
            lineAgeEpochMillisByPath = lineAges,
            nowEpochMillis = System.currentTimeMillis(),
            longLivedTodoDays = settings.longLivedTodoDays,
            historyWindowDays = settings.gitHistoryDays,
        )
        // Hotspots combine the run's findings with the same history data; they are a
        // synthesis stage, not an analyzer over content.
        val hotspots = HotspotDetector.detect(
            findings = enriched,
            historyByPath = history,
            minCommits = settings.hotspotMinCommits,
            historyWindowDays = settings.gitHistoryDays,
        )
        return result.copy(findings = enriched + hotspots)
    }

    /** Analyzer IDs, counts and timings only - never file content (design 15.1). */
    private fun logDiagnostics(scopeType: AnalysisScopeType, result: com.kanicream.repolens.model.AnalysisResult) {
        val timings = result.elapsedByAnalyzer.entries.joinToString(" ") { "${it.key}=${it.value}ms" }
        LOG.info(
            "analysis scope=${scopeType.name} findings=${result.findings.size} " +
                "failures=${result.failures.size} $timings",
        )
    }

    private fun progressTitle(scopeType: AnalysisScopeType): String =
        "Repo Lens: analyzing ${scopeType.displayName.lowercase()}"

    private fun publish(action: (RepoLensAnalysisListener) -> Unit) {
        action(project.messageBus.syncPublisher(RepoLensAnalysisListener.TOPIC))
    }

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, project.disposed)
    }

    companion object {
        private const val MAX_BLAMED_FILES = 100

        private val LOG = logger<RepoLensAnalysisService>()

        fun getInstance(project: Project): RepoLensAnalysisService = project.service()
    }
}
