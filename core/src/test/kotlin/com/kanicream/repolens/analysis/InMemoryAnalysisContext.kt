package com.kanicream.repolens.analysis

import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.SettingsSnapshot
import com.kanicream.repolens.text.TextLines

/** In-memory [AnalyzedFile]; `text == null` simulates unreadable/binary content. */
class InMemoryFile(
    override val relativePath: String,
    private val text: String?,
) : AnalyzedFile {
    override suspend fun lineCount(): Int? = text?.let(TextLines::physicalLineCount)
    override suspend fun lines(): List<String>? = text?.let(TextLines::split)
}

class InMemoryAnalysisContext(
    private val fileList: List<InMemoryFile>,
    settings: SettingsSnapshot = SettingsSnapshot(),
) : AnalysisContext {
    override val request = AnalysisRequest(AnalysisScopeType.PROJECT, settings)
    override suspend fun files(): List<AnalyzedFile> = fileList
}
