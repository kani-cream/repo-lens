package com.kanicream.repolens.model

/** How a file differs from the diff base. */
enum class ChangeStatus(val displayName: String) {
    ADDED("added"),
    MODIFIED("modified"),
    RENAMED("renamed"),
}

/**
 * Per-file diff metrics against the scope's base (a branch merge-base, typically).
 * Line counts are physical added/deleted lines as git reports them; binary files
 * carry zero counts.
 */
data class FileChangeInfo(
    val status: ChangeStatus,
    val addedLines: Int,
    val deletedLines: Int,
) {
    init {
        require(addedLines >= 0 && deletedLines >= 0) { "line counts must not be negative" }
    }

    val totalChangedLines: Int get() = addedLines + deletedLines
}
