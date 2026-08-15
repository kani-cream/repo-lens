package com.kanicream.repolens.vcs

import com.kanicream.repolens.enrich.FileHistory

/**
 * Parses the output of the one batched history query per analysis:
 * `git log --since=… --format=%x01%an%x02%ct --name-only`.
 *
 * Each commit contributes one header line (SOH + author + STX + epoch seconds) followed
 * by the touched paths. One pass aggregates commit count, distinct authors, and the
 * newest timestamp per path — scaling with history size, not with file count.
 */
internal object GitLogParser {

    private const val HEADER_PREFIX = '\u0001' // SOH, from --format=%x01
    private const val FIELD_SEPARATOR = '\u0002' // STX, from %x02

    fun parse(lines: List<String>): Map<String, FileHistory> {
        data class Accumulator(
            var commits: Int = 0,
            val authors: MutableSet<String> = mutableSetOf(),
            var newestEpochMillis: Long = 0,
        )

        val perPath = LinkedHashMap<String, Accumulator>()
        var author: String? = null
        var epochMillis = 0L

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line[0] == HEADER_PREFIX) {
                val body = line.substring(1)
                val separator = body.indexOf(FIELD_SEPARATOR)
                if (separator < 0) continue
                author = body.substring(0, separator)
                epochMillis = body.substring(separator + 1).trim().toLongOrNull()?.times(1000) ?: 0
            } else if (author != null) {
                val acc = perPath.getOrPut(line) { Accumulator() }
                acc.commits++
                acc.authors += author
                if (epochMillis > acc.newestEpochMillis) acc.newestEpochMillis = epochMillis
            }
        }
        return perPath.mapValues { (_, acc) ->
            FileHistory(acc.commits, acc.authors.size, acc.newestEpochMillis)
        }
    }

    /**
     * Parses `git blame --line-porcelain` output into 1-based line → committer time
     * (epoch millis). Only `committer-time` is read; everything else is skipped.
     */
    fun parseBlame(lines: List<String>): Map<Int, Long> {
        val result = LinkedHashMap<Int, Long>()
        var currentLine = -1
        for (line in lines) {
            when {
                // Header: "<hash> <origLine> <finalLine> [numLines]"
                line.isNotEmpty() && !line.startsWith("\t") && HASH_HEADER.matches(line) -> {
                    currentLine = line.split(' ')[2].toIntOrNull() ?: -1
                }
                line.startsWith("committer-time ") && currentLine > 0 -> {
                    line.removePrefix("committer-time ").trim().toLongOrNull()?.let {
                        result[currentLine] = it * 1000
                    }
                }
            }
        }
        return result
    }

    private val HASH_HEADER = Regex("^[0-9a-f]{7,40} \\d+ \\d+( \\d+)?$")
}
