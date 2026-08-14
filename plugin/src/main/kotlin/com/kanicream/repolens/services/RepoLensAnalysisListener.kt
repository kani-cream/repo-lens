package com.kanicream.repolens.services

import com.intellij.util.messages.Topic
import com.kanicream.repolens.model.AnalysisResult
import com.kanicream.repolens.model.AnalysisScopeType

/**
 * Analysis lifecycle events, published on the EDT.
 *
 * Going through the message bus keeps the service independent of the tool window: an
 * action can start a run before any UI exists, and a closed tool window simply stops
 * receiving events instead of being called after disposal.
 */
interface RepoLensAnalysisListener {

    fun analysisStarted(scopeType: AnalysisScopeType) {}

    fun analysisFinished(scopeType: AnalysisScopeType, result: AnalysisResult) {}

    fun analysisCancelled() {}

    /** [reason] is a user-facing message; it never contains source code. */
    fun analysisFailed(reason: String) {}

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<RepoLensAnalysisListener> =
            Topic.create("Repo Lens analysis", RepoLensAnalysisListener::class.java)
    }
}
