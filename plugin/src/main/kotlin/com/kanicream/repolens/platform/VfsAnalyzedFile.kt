package com.kanicream.repolens.platform

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.kanicream.repolens.analysis.AnalyzedFile
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.text.TextLines

/**
 * [AnalyzedFile] backed by a [VirtualFile]. Each content access takes its own short
 * read action, so analyzers never hold a read lock between files.
 *
 * The structural view is memoized for the run: four analyzers ask for it per file, and
 * re-parsing for each would multiply the most expensive step. Text is deliberately not
 * cached — one instance exists per project file for the whole run, so retaining text
 * would hold every analyzed file in memory, while the platform's own VFS caching keeps
 * repeated reads cheap. Analyzers run sequentially, so plain fields are safe memos.
 */
internal class VfsAnalyzedFile(
    private val project: Project,
    private val file: VirtualFile,
    override val relativePath: String,
) : AnalyzedFile {

    private var structureComputed = false
    private var cachedStructure: CodeStructure? = null

    override suspend fun lineCount(): Int? = text()?.let(TextLines::physicalLineCount)

    override suspend fun lines(): List<String>? = text()?.let(TextLines::split)

    override suspend fun structure(): CodeStructure? {
        if (!structureComputed) {
            cachedStructure = readAction { CodeStructureProvider.structureOf(project, file) }
            structureComputed = true
        }
        return cachedStructure
    }

    private suspend fun text(): String? = readAction { VfsText.load(file) }
}
