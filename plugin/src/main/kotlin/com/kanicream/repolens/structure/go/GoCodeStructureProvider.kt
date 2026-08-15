package com.kanicream.repolens.structure.go

import com.goide.GoFileType
import com.goide.psi.GoFile
import com.goide.psi.GoForStatement
import com.goide.psi.GoFunctionLit
import com.goide.psi.GoFunctionOrMethodDeclaration
import com.goide.psi.GoIfStatement
import com.goide.psi.GoMethodDeclaration
import com.goide.psi.GoSelectStatement
import com.goide.psi.GoSwitchStatement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.kanicream.repolens.structure.CodeDeclaration
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind

/**
 * Extracts Go declarations for the structure checks.
 *
 * Loaded only when the Go plugin is present (see repo-lens-go.xml). Go has no class
 * bodies — methods live outside their receiver type — so this provider emits FUNCTION
 * declarations only and no TYPE entries: a Large Class finding would be meaningless
 * (docs/design.md §4.4: don't force Go into Java's shape).
 */
internal class GoCodeStructureProvider : CodeStructureProvider {

    override fun supports(project: Project, file: VirtualFile): Boolean =
        file.fileType == GoFileType.INSTANCE

    override fun structure(project: Project, file: VirtualFile): CodeStructure? {
        val goFile = PsiManager.getInstance(project).findFile(file) as? GoFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

        val declarations = (goFile.functions + goFile.methods)
            .mapNotNull { declaration(it, document) }
        return CodeStructure(declarations)
    }

    private fun declaration(
        function: GoFunctionOrMethodDeclaration,
        document: Document,
    ): CodeDeclaration? {
        val name = displayName(function) ?: return null
        val range = function.textRange ?: return null
        val startLine = lineOf(range.startOffset, document) ?: return null
        val endLine = lineOf(range.endOffset, document) ?: return null

        val bodyRange = function.block?.textRange
        val bodyStart = bodyRange?.let { lineOf(it.startOffset, document) } ?: startLine
        val bodyEnd = bodyRange?.let { lineOf(it.endOffset, document) } ?: endLine

        return CodeDeclaration(
            kind = DeclarationKind.FUNCTION,
            displayName = name,
            startLine = startLine,
            endLine = maxOf(startLine, endLine),
            bodyLineCount = maxOf(0, bodyEnd - bodyStart + 1),
            parameterCount = parameterCount(function),
            maxNestingDepth = GoNestingDepth.of(function.block),
        )
    }

    /** `Server.Start()` for receiver methods, `main()` for free functions. */
    private fun displayName(function: GoFunctionOrMethodDeclaration): String? {
        val name = function.name?.takeIf { it.isNotEmpty() } ?: return null
        val receiverType = (function as? GoMethodDeclaration)
            ?.receiverType?.text
            ?.removePrefix("*")
        return if (receiverType.isNullOrEmpty()) "$name()" else "$receiverType.$name()"
    }

    /**
     * Declared parameters, counting names: `a, b int` is two. An unnamed parameter
     * (`func f(int)`) has no definitions but is still one parameter. Receivers are not
     * parameters; a variadic parameter counts once.
     */
    private fun parameterCount(function: GoFunctionOrMethodDeclaration): Int? {
        val parameters = function.signature?.parameters ?: return 0
        return parameters.parameterDeclarationList.sumOf { declaration ->
            maxOf(1, declaration.paramDefinitionList.size)
        }
    }

    private fun lineOf(offset: Int, document: Document): Int? {
        if (offset < 0 || offset > document.textLength) return null
        return document.getLineNumber(offset) + 1
    }
}

/**
 * Deepest nesting of Go control flow: if, all `for` forms, switch (expression and
 * type), select, and function literals — the Go counterpart of OD-02's construct list.
 */
internal object GoNestingDepth {

    fun of(root: PsiElement?): Int {
        if (root == null) return 0
        var max = 0
        fun walk(element: PsiElement, depth: Int) {
            val next = if (isNestingConstruct(element)) depth + 1 else depth
            if (next > max) max = next
            var child = element.firstChild
            while (child != null) {
                walk(child, next)
                child = child.nextSibling
            }
        }
        walk(root, 0)
        return max
    }

    private fun isNestingConstruct(element: PsiElement): Boolean =
        element is GoIfStatement ||
            element is GoForStatement ||
            element is GoSwitchStatement ||
            element is GoSelectStatement ||
            element is GoFunctionLit
}
