package com.kanicream.repolens.structure

/**
 * What kind of declaration a [CodeDeclaration] describes.
 *
 * Deliberately coarse: languages disagree about classes, structs, objects, functions and
 * methods, and the size checks do not care. Providers map their language onto these two
 * buckets rather than the core growing a language-shaped model (docs/design.md §4.5).
 */
enum class DeclarationKind {
    /** A class, interface, object, struct, or similar container. */
    TYPE,

    /** A method, function, constructor, or similar callable. */
    FUNCTION,
}

/**
 * One declaration found in a file.
 *
 * [startLine] / [endLine] describe the whole declaration and are what navigation uses.
 * [bodyLineCount] is the measured metric: physical lines of the body, comments and blank
 * lines included (docs/design.md OD-01).
 */
data class CodeDeclaration(
    val kind: DeclarationKind,
    /** Qualified enough to be recognizable, e.g. `PaymentService.processPayment()`. */
    val displayName: String,
    val startLine: Int,
    val endLine: Int,
    val bodyLineCount: Int,
) {
    init {
        require(startLine >= 1) { "startLine must be 1-based, got $startLine" }
        require(endLine >= startLine) { "endLine ($endLine) must not precede startLine ($startLine)" }
        require(bodyLineCount >= 0) { "bodyLineCount must not be negative, got $bodyLineCount" }
    }
}

/**
 * Structural view of a file, supplied by whichever language provider can parse it.
 *
 * A file with no provider has no structure at all, which is a normal state: the Tier 0
 * analyzers still run, and the structure analyzers simply produce nothing.
 */
data class CodeStructure(val declarations: List<CodeDeclaration>) {

    fun ofKind(kind: DeclarationKind): List<CodeDeclaration> = declarations.filter { it.kind == kind }

    companion object {
        val EMPTY: CodeStructure = CodeStructure(emptyList())
    }
}
