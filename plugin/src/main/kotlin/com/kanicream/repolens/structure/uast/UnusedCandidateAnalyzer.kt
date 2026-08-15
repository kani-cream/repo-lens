package com.kanicream.repolens.structure.uast

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.searches.ReferencesSearch
import com.kanicream.repolens.RepoLensBundle
import com.kanicream.repolens.analysis.AnalysisContext
import com.kanicream.repolens.analysis.AnalyzerSkippedException
import com.kanicream.repolens.analysis.RepoLensAnalyzer
import com.kanicream.repolens.model.Confidence
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import com.kanicream.repolens.platform.ProjectPaths
import com.kanicream.repolens.platform.VfsText
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * RL-U001: public declarations that nothing in the project references.
 *
 * Private members are the IDE's own inspections' territory; the review-worthy case is a
 * *public* API surface with no callers. The name is deliberately "candidate": reference
 * search cannot see reflection, dependency injection, serialization, or external
 * consumers, and every finding says so.
 *
 * The first index-dependent analyzer: while the IDE is still indexing it skips with a
 * visible reason instead of producing a silently incomplete result.
 *
 * Registered through the UAST optional descriptor, so IDEs without the Java plugin
 * simply do not have this check.
 */
internal class UnusedCandidateAnalyzer(private val project: Project) : RepoLensAnalyzer {

    override val id: String = ID
    override val checkName: String = "Unused Candidate"

    override fun supports(context: AnalysisContext): Boolean = true

    override suspend fun analyze(context: AnalysisContext): List<Finding> {
        if (DumbService.getInstance(project).isDumb) {
            throw AnalyzerSkippedException(RepoLensBundle.message("skip.unused.candidate.indexing"))
        }

        // Two phases with separate short read actions (issue #17): collecting the
        // candidate declarations is cheap, while each reference search can be slow - one
        // long read action per file would make a declaration-heavy file block write
        // actions for the duration of every search in it.
        val findings = mutableListOf<Finding>()
        for (file in context.files()) {
            coroutineContext.ensureActive()
            val candidates = readAction { collectCandidates(file.relativePath) }
            for (candidate in candidates) {
                coroutineContext.ensureActive()
                readAction { searchAndReport(candidate) }?.let(findings::add)
            }
        }
        return findings
    }

    /** Everything needed to search one declaration in its own read action later. */
    private class Candidate(
        val pointer: SmartPsiElementPointer<PsiModifierListOwner>,
        val searchName: String,
        val displayName: String,
        val subject: String,
        val location: SourceLocation,
    )

    /** Called under a read action, in smart mode. */
    private fun collectCandidates(relativePath: String): List<Candidate> {
        val virtualFile = ProjectPaths.resolve(project, relativePath) ?: return emptyList()
        // Test code's public members are called by the framework, not by references.
        if (TestSourcesFilter.isTestSources(virtualFile, project)) return emptyList()
        if (VfsText.load(virtualFile) == null) return emptyList()
        val uFile = PsiManager.getInstance(project).findFile(virtualFile)?.toUElement() as? UFile
            ?: return emptyList()
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: return emptyList()

        val candidates = mutableListOf<Candidate>()
        uFile.accept(
            object : AbstractUastVisitor() {
                override fun visitClass(node: UClass): Boolean {
                    // A companion object is reached through its containing class.
                    if (node.javaPsi.name == "Companion") return false
                    candidate(node.javaPsi, node.javaPsi.name, "type", relativePath, node.sourcePsi, document)
                        ?.let(candidates::add)
                    return false
                }

                override fun visitMethod(node: UMethod): Boolean {
                    if (isEntryPointOrOverride(node)) return false
                    val owner = node.getContainingUClass()?.javaPsi?.name
                    val display = if (owner == null) "${node.name}()" else "$owner.${node.name}()"
                    candidate(node.javaPsi, display, "function or method", relativePath, node.sourcePsi, document)
                        ?.let(candidates::add)
                    return false
                }
            },
        )
        return candidates
    }

    /**
     * Members a reference search would wrongly flag: entry points the platform calls
     * (main), overrides reached through the supertype, constructors (reached via the
     * type), and anything annotated — annotations are how frameworks take over calling.
     */
    private fun isEntryPointOrOverride(method: UMethod): Boolean {
        val psi = method.javaPsi
        if (method.isConstructor) return true
        if (psi.name == "main") return true
        // Accessors and other members a language synthesizes (Kotlin property getters,
        // enum valueOf/values, data-class members) round-trip to something other than a
        // method. References resolve to the property, never to the light accessor, so a
        // search on the accessor is guaranteed to find nothing and would always flag it.
        val sourceAsUElement: UElement? = method.sourcePsi?.toUElement()
        if (sourceAsUElement !is UMethod) return true
        // Properties with explicit accessors surface as UMethods whose source is the
        // accessor, slipping past the round-trip check. References still resolve to the
        // property, so searching the accessor is guaranteed empty. No compile dependency
        // on the Kotlin plugin exists here, hence the class-name check.
        val sourceClassName = method.sourcePsi?.javaClass?.name.orEmpty()
        if ("KtProperty" in sourceClassName || "KtParameter" in sourceClassName) return true
        // Kotlin light methods carry synthesized @NotNull/@Nullable; those say nothing
        // about who calls the method and must not exempt every Kotlin function.
        val meaningfulAnnotations = psi.annotations.filterNot {
            it.qualifiedName?.startsWith("org.jetbrains.annotations.") == true
        }
        if (meaningfulAnnotations.isNotEmpty()) return true
        return psi.findSuperMethods().isNotEmpty()
    }

    private fun candidate(
        psi: PsiModifierListOwner,
        name: String?,
        subject: String,
        relativePath: String,
        sourcePsi: PsiElement?,
        document: Document,
    ): Candidate? {
        if (name.isNullOrEmpty() || sourcePsi == null) return null
        if (!psi.hasModifierProperty(PsiModifier.PUBLIC)) return null

        val range = sourcePsi.textRange ?: return null
        if (range.startOffset > document.textLength) return null
        val startLine = document.getLineNumber(range.startOffset) + 1
        val endLine = document.getLineNumber(range.endOffset.coerceAtMost(document.textLength)) + 1

        return Candidate(
            pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psi),
            searchName = if (psi is PsiMethod) psi.name else name,
            displayName = name,
            subject = subject,
            location = SourceLocation(relativePath, startLine, maxOf(startLine, endLine)),
        )
    }

    /** Called under its own read action; returns a finding when nothing references the candidate. */
    private fun searchAndReport(candidate: Candidate): Finding? {
        val psi = candidate.pointer.element ?: return null // edited away since collection
        val scope = GlobalSearchScope.projectScope(project)
        when (PsiSearchHelper.getInstance(project).isCheapEnoughToSearch(candidate.searchName, scope, null)) {
            SearchCostResult.TOO_MANY_OCCURRENCES -> return null // common name; searching would lie or crawl
            SearchCostResult.ZERO_OCCURRENCES -> {} // definitely no textual mention outside the declaration
            SearchCostResult.FEW_OCCURRENCES ->
                if (ReferencesSearch.search(psi, scope).findFirst() != null) return null
        }

        return Finding(
            id = Finding.stableId(id, candidate.location),
            analyzerId = id,
            severity = Severity.INFO,
            checkName = checkName,
            message = "No project reference to this public ${candidate.subject} was found. $LIMITATION",
            location = candidate.location,
            symbol = SymbolInfo(candidate.displayName),
            confidence = Confidence.LOW,
        )
    }

    companion object {
        const val ID: String = "RL-U001"

        /** Stated on every finding: the blind spots make this a candidate, not a verdict. */
        const val LIMITATION: String =
            "Reference search cannot see reflection, dependency injection, serialization, " +
                "or callers outside this project; treat this as a candidate, not a verdict."
    }
}
