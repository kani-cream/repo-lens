package com.kanicream.repolens.model

/**
 * Location of a finding inside a project file.
 *
 * [filePath] is project-relative with `/` separators whenever the file lives under the
 * project base directory; otherwise it may be an absolute path. Lines are 1-based and
 * the range is inclusive. Offsets are optional character offsets into the file text.
 */
data class SourceLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
) {
    init {
        require(startLine >= 1) { "startLine must be 1-based, got $startLine" }
        require(endLine >= startLine) { "endLine ($endLine) must not precede startLine ($startLine)" }
    }

    /** `84` for a single line, `182-327` for a range. */
    val lineRangeText: String
        get() = if (startLine == endLine) startLine.toString() else "$startLine-$endLine"
}
