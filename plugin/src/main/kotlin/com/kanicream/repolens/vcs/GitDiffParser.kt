package com.kanicream.repolens.vcs

import com.kanicream.repolens.model.ChangeStatus
import com.kanicream.repolens.model.FileChangeInfo

/**
 * Parses `git diff --numstat` and `git diff --name-status` output into per-file change
 * info, keyed by the post-change path.
 *
 * Pure text processing so it is testable without a repository. Deleted files are
 * dropped — there is nothing left to analyze or navigate to. Binary files report zero
 * line counts (git prints `-`). Paths containing tabs or newlines are out of scope.
 */
internal object GitDiffParser {

    /** `120\t45\tsrc/App.kt`, `3\t1\tsrc/{old => new}/File.kt`, `-\t-\timage.png` */
    fun parseNumstat(lines: List<String>): Map<String, Pair<Int, Int>> {
        val result = LinkedHashMap<String, Pair<Int, Int>>()
        for (line in lines) {
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) continue
            val added = parts[0].toIntOrNull() ?: 0
            val deleted = parts[1].toIntOrNull() ?: 0
            val path = normalizeRenamePath(parts[2])
            result[path] = added to deleted
        }
        return result
    }

    /** `M\tsrc/App.kt`, `A\tsrc/New.kt`, `D\tsrc/Gone.kt`, `R100\told.kt\tnew.kt` */
    fun parseNameStatus(lines: List<String>): Map<String, ChangeStatus> {
        val result = LinkedHashMap<String, ChangeStatus>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 2) continue
            val statusCode = parts[0]
            when {
                statusCode.startsWith("R") || statusCode.startsWith("C") ->
                    parts.getOrNull(2)?.let { result[it] = ChangeStatus.RENAMED }
                statusCode == "A" -> result[parts[1]] = ChangeStatus.ADDED
                statusCode == "D" -> {} // nothing left to analyze
                else -> result[parts[1]] = ChangeStatus.MODIFIED
            }
        }
        return result
    }

    /** Joins both outputs; a path present in only one of them falls back sensibly. */
    fun combine(
        numstat: Map<String, Pair<Int, Int>>,
        statuses: Map<String, ChangeStatus>,
    ): Map<String, FileChangeInfo> {
        val result = LinkedHashMap<String, FileChangeInfo>()
        statuses.forEach { (path, status) ->
            val (added, deleted) = numstat[path] ?: (0 to 0)
            result[path] = FileChangeInfo(status, added, deleted)
        }
        return result
    }

    /** `src/{old => new}/File.kt` → `src/new/File.kt`; `old.kt => new.kt` → `new.kt`. */
    private fun normalizeRenamePath(rawPath: String): String {
        val braced = Regex("\\{([^{}]*) => ([^{}]*)}").replace(rawPath) { it.groupValues[2] }
        val collapsed = braced.replace("//", "/")
        if (" => " in collapsed && '{' !in rawPath) {
            return collapsed.substringAfter(" => ")
        }
        return collapsed
    }
}
