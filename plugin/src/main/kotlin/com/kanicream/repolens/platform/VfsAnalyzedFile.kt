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
 * Content is deliberately not cached: one instance exists per project file for the whole
 * run, so caching would retain the text of every analyzed file until the run finishes.
 * The platform's own VFS caching keeps repeated reads cheap.
 */
internal class VfsAnalyzedFile(
    private val project: Project,
    private val file: VirtualFile,
    override val relativePath: String,
) : AnalyzedFile {

    override suspend fun lineCount(): Int? = text()?.let(TextLines::physicalLineCount)

    override suspend fun lines(): List<String>? = text()?.let(TextLines::split)

    override suspend fun structure(): CodeStructure? =
        readAction { CodeStructureProvider.structureOf(project, file) }

    private suspend fun text(): String? = readAction { VfsText.load(file) }
}
