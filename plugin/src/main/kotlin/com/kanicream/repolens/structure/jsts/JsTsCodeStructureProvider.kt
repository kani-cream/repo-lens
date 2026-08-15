package com.kanicream.repolens.structure.jsts

import com.intellij.lang.Language
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSIfStatement
import com.intellij.lang.javascript.psi.JSLoopStatement
import com.intellij.lang.javascript.psi.JSSwitchStatement
import com.intellij.lang.javascript.psi.JSTryStatement
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.kanicream.repolens.structure.CodeDeclaration
import com.kanicream.repolens.structure.CodeStructure
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.DeclarationKind

/**
 * Extracts JavaScript and TypeScript declarations for the structure checks.
 *
 * Loaded only when the JavaScript plugin is present (see repo-lens-javascript.xml).
 * One implementation covers the whole family — JS, TS, JSX, TSX — because every dialect
 * derives from the JavaScript base language and shares the JS PSI.
 *
 * Anonymous functions (arrows and function expressions without a name of their own) are
 * not extracted as declarations; like Go's function literals they count toward the
 * enclosing function's nesting instead.
 */
internal class JsTsCodeStructureProvider : CodeStructureProvider {

    override fun supports(project: Project, file: VirtualFile): Boolean =
        isJavaScriptFamily((file.fileType as? LanguageFileType)?.language)

    override fun structure(project: Project, file: VirtualFile): CodeStructure? {
        val jsFile = PsiManager.getInstance(project).findFile(file) as? JSFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

        val declarations = mutableListOf<CodeDeclaration>()
        PsiTreeUtil.findChildrenOfType(jsFile, JSClass::class.java).forEach { jsClass ->
            typeDeclaration(jsClass, document)?.let(declarations::add)
        }
        PsiTreeUtil.findChildrenOfType(jsFile, JSFunction::class.java).forEach { function ->
            functionDeclaration(function, document)?.let(declarations::add)
        }
        return CodeStructure(declarations)
    }

    private fun typeDeclaration(jsClass: JSClass, document: Document): CodeDeclaration? {
        val name = jsClass.name?.takeIf { it.isNotEmpty() } ?: return null
        return declaration(jsClass, name, DeclarationKind.TYPE, jsClass.textRange, document)
    }

    private fun functionDeclaration(function: JSFunction, document: Document): CodeDeclaration? {
        val name = function.name?.takeIf { it.isNotEmpty() } ?: return null
        val owner = PsiTreeUtil.getParentOfType(function, JSClass::class.java)?.name
        val displayName = if (owner.isNullOrEmpty()) "$name()" else "$owner.$name()"
        val bodyRange = function.block?.textRange ?: function.textRange
        return declaration(function, displayName, DeclarationKind.FUNCTION, bodyRange, document)
            ?.copy(
                parameterCount = function.parameters.size,
                maxNestingDepth = JsNestingDepth.of(function.block ?: function),
            )
    }

    private fun declaration(
        element: PsiElement,
        name: String,
        kind: DeclarationKind,
        bodyRange: com.intellij.openapi.util.TextRange?,
        document: Document,
    ): CodeDeclaration? {
        val range = element.textRange ?: return null
        val startLine = lineOf(range.startOffset, document) ?: return null
        val endLine = lineOf(range.endOffset, document) ?: return null
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

    private fun isJavaScriptFamily(language: Language?): Boolean {
        var current = language
        while (current != null) {
            if (current.id == "JavaScript") return true
            current = current.baseLanguage
        }
        return false
    }
}

/**
 * Deepest nesting of JS/TS control flow: if, every loop form, switch, try, and nested
 * function expressions — callbacks and arrows, the construct that actually produces
 * deep nesting in this family (docs/milestones/v0.4.md test focus: nested callback).
 */
internal object JsNestingDepth {

    fun of(root: PsiElement?): Int {
        if (root == null) return 0
        var max = 0
        fun walk(element: PsiElement, depth: Int) {
            val next = if (isNestingConstruct(element) && element !== root) depth + 1 else depth
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
        element is JSIfStatement ||
            element is JSLoopStatement ||
            element is JSSwitchStatement ||
            element is JSTryStatement ||
            element is JSFunctionExpression
}
