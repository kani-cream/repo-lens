package com.kanicream.repolens.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.kanicream.repolens.analysis.AnalysisOrchestrator
import com.kanicream.repolens.analysis.AnalyzerRegistry
import com.kanicream.repolens.analysis.tier0.LargeFileAnalyzer
import com.kanicream.repolens.analysis.tier0.TodoMarkerAnalyzer
import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.platform.ProjectAnalysisContext
import com.kanicream.repolens.settings.RepoLensSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Application service that drives analysis runs: it snapshots the settings, resolves the
 * scope through platform adapters, runs the orchestrator on a background coroutine with
 * a cancellable progress indicator, and reports back on the EDT.
 */
@Service(Service.Level.PROJECT)
class RepoLensAnalysisService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    private val orchestrator = AnalysisOrchestrator(
        AnalyzerRegistry(listOf(LargeFileAnalyzer(), TodoMarkerAnalyzer())),
    )

    @Volatile
    private var currentJob: Job? = null

    /** Starts a Project-scope analysis, cancelling any run still in flight. */
    fun startProjectAnalysis(listener: AnalysisListener) {
        stop()
        val request = AnalysisRequest(
            scopeType = AnalysisScopeType.PROJECT,
            settings = RepoLensSettings.getInstance(project).snapshot(),
        )
        currentJob = coroutineScope.launch {
            try {
                val result = withBackgroundProgress(project, "Repo Lens: analyzing project") {
                    orchestrator.analyze(ProjectAnalysisContext(project, request))
                }
                onEdt { listener.onFinished(result) }
            } catch (e: CancellationException) {
                onEdt { listener.onCancelled() }
                throw e
            } catch (e: Throwable) {
                LOG.warn("Repo Lens analysis failed", e)
                onEdt { listener.onFailed(e) }
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
    }

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action, project.disposed)
    }

    companion object {
        private val LOG = logger<RepoLensAnalysisService>()

        fun getInstance(project: Project): RepoLensAnalysisService = project.service()
    }
}
