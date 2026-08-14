package com.kanicream.repolens.analysis

import com.kanicream.repolens.model.AnalysisRequest
import com.kanicream.repolens.model.SettingsSnapshot

/**
 * Everything an analyzer may see during one run.
 *
 * This is the only gateway between analyzers and the platform: analyzers must not reach
 * for UI, clipboard, navigation, or IDE services on their own. Platform adapters
 * implement this interface on top of the IntelliJ VFS; tests implement it in memory.
 */
interface AnalysisContext {
    val request: AnalysisRequest

    val settings: SettingsSnapshot
        get() = request.settings

    /** Files resolved for the requested scope. */
    suspend fun files(): List<AnalyzedFile>
}

/**
 * A single file exposed to analyzers.
 *
 * Content accessors are suspending so platform implementations can take short,
 * per-call read actions instead of holding a read lock across the whole analysis.
 * They return `null` when content is unavailable (binary or unreadable file);
 * analyzers are expected to skip such files silently.
 */
interface AnalyzedFile {
    /** Project-relative path with `/` separators (absolute if outside the project base). */
    val relativePath: String

    /** Physical line count, or `null` when unavailable. */
    suspend fun lineCount(): Int?

    /** File text as lines without terminators, or `null` when unavailable. */
    suspend fun lines(): List<String>?
}
