package com.kanicream.repolens

import com.kanicream.repolens.vcs.GitLogParser
import junit.framework.TestCase

/** Pure text parsing; no repository involved. */
class GitLogParserTest : TestCase() {

    fun `test log output aggregates commits authors and newest timestamp per path`() {
        val lines = listOf(
            "\u0001alice\u00021000",
            "src/App.kt",
            "src/Util.kt",
            "",
            "\u0001bob\u00022000",
            "src/App.kt",
            "",
            "\u0001alice\u00023000",
            "src/App.kt",
        )

        val parsed = GitLogParser.parse(lines)

        val app = parsed.getValue("src/App.kt")
        assertEquals(3, app.commitCount)
        assertEquals(2, app.authorCount)
        assertEquals(3000L * 1000, app.lastModifiedEpochMillis)

        val util = parsed.getValue("src/Util.kt")
        assertEquals(1, util.commitCount)
        assertEquals(1, util.authorCount)
        assertEquals(1000L * 1000, util.lastModifiedEpochMillis)
    }

    fun `test malformed headers and paths before any header are skipped`() {
        val parsed = GitLogParser.parse(
            listOf(
                "orphan/path.kt",
                "\u0001broken-header-without-separator",
                "\u0001carol\u00025000",
                "real/path.kt",
            ),
        )

        assertEquals(setOf("real/path.kt"), parsed.keys)
    }

    fun `test blame porcelain yields committer time per final line`() {
        val lines = listOf(
            "a".repeat(40) + " 1 1 2",
            "author alice",
            "committer-time 1111",
            "\tline one text",
            "a".repeat(40) + " 2 2",
            "committer-time 2222",
            "\tline two text",
            "b".repeat(40) + " 9 3 1",
            "author bob",
            "committer-time 3333",
            "\tline three text",
        )

        val parsed = GitLogParser.parseBlame(lines)

        assertEquals(1111L * 1000, parsed[1])
        assertEquals(2222L * 1000, parsed[2])
        assertEquals(3333L * 1000, parsed[3])
    }
}
