package com.kanicream.repolens.structure.uast

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.kanicream.repolens.structure.CodeDeclaration
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Extracts declarations through UAST, which covers every language that ships a UAST
 * implementation — Java and Kotlin today, without either being referenced here.
 *
 * Loaded only when the Java plugin is present, since UAST ships inside it. Everything
 * language-specific stops at this class: the core sees plain line numbers and names.
 */
internal class UastCodeStructureProvider : CodeStructureProvider {

    override fun supports(project: Project, file: VirtualFile): Boolean {
        val language = (file.fileType as? LanguageFileType)?.language ?: return false
        return UastLanguagePlugin.getInstances().any { it.language == language }
    }

    override fun structure(project: Project, file: VirtualFile): CodeStructure? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val uFile = psiFile.toUElement() as? UFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

        val declarations = mutableListOf<CodeDeclaration>()
        uFile.accept(
            object : AbstractUastVisitor() {
                // Returning false keeps the visitor descending, so nested types and
                // methods are reported too.
                override fun visitClass(node: UClass): Boolean {
                    declarations += declaration(
                        sourcePsi = node.sourcePsi,
                        name = node.javaPsi.name,
                        kind = DeclarationKind.TYPE,
                        bodyRange = node.sourcePsi?.textRange,
                        document = document,
                    ) ?: return false
                    return false
                }

                override fun visitMethod(node: UMethod): Boolean {
                    if (isSynthesized(node)) return false
                    declarations += declaration(
                        sourcePsi = node.sourcePsi,
                        name = methodName(node),
                        kind = DeclarationKind.FUNCTION,
                        bodyRange = node.uastBody?.sourcePsi?.textRange ?: node.sourcePsi?.textRange,
                        document = document,
                    )?.copy(
                        parameterCount = node.uastParameters.size,
                        maxNestingDepth = UastNestingDepth.of(node.uastBody),
                    ) ?: return false
                    return false
                }
            },
        )
        return CodeStructure(declarations)
    }

    /**
     * True for members the language synthesizes rather than the author writing them,
     * such as a Kotlin class's implicit constructor. They share their source element
     * with the enclosing type, so reporting them would attribute the whole type's range
     * to a "method" that does not exist in the file.
     */
    private fun isSynthesized(node: UMethod): Boolean {
        val sourcePsi = node.sourcePsi ?: return true
        val ownerPsi = node.getContainingUClass()?.sourcePsi ?: return false
        return sourcePsi == ownerPsi
    }

    private fun methodName(node: UMethod): String {
        val owner = node.getContainingUClass()?.javaPsi?.name
        return if (owner == null) "${node.name}()" else "$owner.${node.name}()"
    }

    /**
     * Maps a declaration onto 1-based lines. The reported range covers the whole
     * declaration so navigation lands on the signature, while the metric counts the
     * physical lines of [bodyRange] — comments and blank lines included (OD-01). Types
     * have no separate body range in UAST, so for them both spans coincide.
     */
    private fun declaration(
        sourcePsi: PsiElement?,
        name: String?,
        kind: DeclarationKind,
        bodyRange: TextRange?,
        document: Document,
    ): CodeDeclaration? {
        if (sourcePsi == null || name.isNullOrEmpty()) return null
        val declarationRange = sourcePsi.textRange ?: return null
        val startLine = lineOf(declarationRange.startOffset, document) ?: return null
        val endLine = lineOf(declarationRange.endOffset, document) ?: return null

        val bodyStart = bodyRange?.let { lineOf(it.startOffset, document) } ?: startLine
        val bodyEnd = bodyRange?.let { lineOf(it.endOffset, document) } ?: endLine

        return CodeDeclaration(
            kind = kind,
            displayName = name,
            startLine = startLine,
            endLine = maxOf(startLine, endLine),
            bodyLineCount = maxOf(0, bodyEnd - bodyStart + 1),
        )
    }

    private fun lineOf(offset: Int, document: Document): Int? {
        if (offset < 0 || offset > document.textLength) return null
        return document.getLineNumber(offset) + 1
    }
}
