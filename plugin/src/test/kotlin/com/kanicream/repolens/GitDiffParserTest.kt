package com.kanicream.repolens

import com.kanicream.repolens.model.ChangeStatus
import com.kanicream.repolens.vcs.GitDiffParser
import junit.framework.TestCase

/** Pure text parsing; no repository or fixture involved. */
class GitDiffParserTest : TestCase() {

    fun `test numstat parses counts binary markers and rename paths`() {
        val parsed = GitDiffParser.parseNumstat(
            listOf(
                "120\t45\tsrc/App.kt",
                "-\t-\tassets/logo.png",
                "3\t1\tsrc/{old => new}/File.kt",
                "7\t0\told.kt => new.kt",
                "garbage line",
            ),
        )

        assertEquals(120 to 45, parsed["src/App.kt"])
        assertEquals(0 to 0, parsed["assets/logo.png"])
        assertEquals(3 to 1, parsed["src/new/File.kt"])
        assertEquals(7 to 0, parsed["new.kt"])
        assertEquals(4, parsed.size)
    }

    fun `test name-status maps codes and drops deletions`() {
        val parsed = GitDiffParser.parseNameStatus(
            listOf(
                "M\tsrc/App.kt",
                "A\tsrc/New.kt",
                "D\tsrc/Gone.kt",
                "R100\tsrc/Old.kt\tsrc/Renamed.kt",
                "T\tsrc/TypeChanged.kt",
            ),
        )

        assertEquals(ChangeStatus.MODIFIED, parsed["src/App.kt"])
        assertEquals(ChangeStatus.ADDED, parsed["src/New.kt"])
        assertEquals(ChangeStatus.RENAMED, parsed["src/Renamed.kt"])
        assertEquals(ChangeStatus.MODIFIED, parsed["src/TypeChanged.kt"])
        assertFalse("deletions must be dropped", parsed.containsKey("src/Gone.kt"))
    }

    fun `test combine joins by post-change path`() {
        val combined = GitDiffParser.combine(
            numstat = mapOf("a.kt" to (10 to 2), "renamed.kt" to (5 to 5)),
            statuses = mapOf(
                "a.kt" to ChangeStatus.MODIFIED,
                "renamed.kt" to ChangeStatus.RENAMED,
                "counted-missing.kt" to ChangeStatus.ADDED,
            ),
        )

        assertEquals(12, combined["a.kt"]?.totalChangedLines)
        assertEquals(ChangeStatus.RENAMED, combined["renamed.kt"]?.status)
        // Status without numstat (e.g. pure mode change) keeps zero counts.
        assertEquals(0, combined["counted-missing.kt"]?.totalChangedLines)
    }
}
