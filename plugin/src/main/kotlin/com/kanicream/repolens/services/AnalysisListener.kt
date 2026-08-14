package com.kanicream.repolens.services

import com.kanicream.repolens.model.AnalysisResult

/** UI-facing callbacks of [RepoLensAnalysisService]. All methods are invoked on the EDT. */
interface AnalysisListener {
    fun onFinished(result: AnalysisResult)
    fun onCancelled() {}
    fun onFailed(error: Throwable) {}
}
