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
import com.kanicream.repolens.analysis.DefaultAnalyzers
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.platform.ScopeResolution
import com.kanicream.repolens.platform.ScopeResolver
import com.kanicream.repolens.platform.VfsAnalysisContext
import com.kanicream.repolens.settings.RepoLensSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    private val orchestrator = AnalysisOrchestrator(AnalyzerRegistry(DefaultAnalyzers.create()))

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

        when (val resolution = ScopeResolver.resolve(project, scopeType, selectedFiles)) {
            is ScopeResolution.Unavailable -> publisher.analysisFailed(resolution.reason)

            is ScopeResolution.Resolved -> {
                publisher.analysisStarted(scopeType)
                val request = AnalysisRequest(
                    scopeType = scopeType,
                    settings = RepoLensSettings.getInstance(project).snapshot(),
                )
                val context = VfsAnalysisContext(project, request, resolution.scope)
                currentJob = coroutineScope.launch {
                    try {
                        val result = withBackgroundProgress(project, progressTitle(scopeType)) {
                            orchestrator.analyze(context)
                        }
                        onEdt { publish { it.analysisFinished(scopeType, result) } }
                    } catch (e: CancellationException) {
                        onEdt { publish { it.analysisCancelled() } }
                        throw e
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

    private fun progressTitle(scopeType: AnalysisScopeType): String =
        "Repo Lens: analyzing ${scopeType.displayName.lowercase()}"

    private fun publish(action: (RepoLensAnalysisListener) -> Unit) {
        action(project.messageBus.syncPublisher(RepoLensAnalysisListener.TOPIC))
    }

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, project.disposed)
    }

    companion object {
        private val LOG = logger<RepoLensAnalysisService>()

        fun getInstance(project: Project): RepoLensAnalysisService = project.service()
    }
}
