package com.kanicream.repolens.enrich

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitEnrichmentTest {

    private val now = 1_000_000_000_000L
    private fun daysAgo(days: Long): Long = now - days * 24 * 60 * 60 * 1000

    private fun finding(analyzerId: String, path: String, line: Int = 5): Finding {
        val location = SourceLocation(path, line, line)
        return Finding(
            id = Finding.stableId(analyzerId, location),
            analyzerId = analyzerId,
            severity = Severity.INFO,
            checkName = analyzerId,
            message = "original message.",
            location = location,
        )
    }

    private fun apply(
        findings: List<Finding>,
        history: Map<String, FileHistory> = emptyMap(),
        ages: Map<String, Map<Int, Long>> = emptyMap(),
        longLivedDays: Int = 90,
    ) = GitEnrichment.apply(findings, history, ages, now, longLivedDays, historyWindowDays = 90)

    @Test
    fun `history metadata is attached to any finding on a tracked file`() {
        val enriched = apply(
            listOf(finding("RL-F001", "src/App.kt")),
            history = mapOf("src/App.kt" to FileHistory(12, 3, daysAgo(5))),
        ).single()

        assertEquals("12", enriched.metadata[GitMetadataKeys.COMMITS])
        assertEquals("3", enriched.metadata[GitMetadataKeys.AUTHORS])
        assertEquals("5", enriched.metadata[GitMetadataKeys.LAST_MODIFIED_DAYS_AGO])
        assertEquals("90", enriched.metadata[GitMetadataKeys.WINDOW_DAYS])
    }

    @Test
    fun `todo findings gain marker age from their exact line`() {
        val enriched = apply(
            listOf(finding("RL-T001", "src/App.kt", line = 7)),
            ages = mapOf("src/App.kt" to mapOf(7 to daysAgo(30), 8 to daysAgo(300))),
        ).single()

        assertEquals("30", enriched.metadata[GitMetadataKeys.TODO_AGE_DAYS])
        assertNull(enriched.metadata[GitMetadataKeys.TODO_LONG_LIVED])
        assertEquals("original message.", enriched.message)
    }

    @Test
    fun `long-lived markers are labeled and explained in the message`() {
        val enriched = apply(
            listOf(finding("RL-T001", "src/App.kt", line = 7)),
            ages = mapOf("src/App.kt" to mapOf(7 to daysAgo(200))),
        ).single()

        assertEquals("200", enriched.metadata[GitMetadataKeys.TODO_AGE_DAYS])
        assertEquals("true", enriched.metadata[GitMetadataKeys.TODO_LONG_LIVED])
        assertTrue(enriched.message.endsWith("This marker has been in place for 200 days."))
    }

    @Test
    fun `the long-lived boundary is inclusive`() {
        val enriched = apply(
            listOf(finding("RL-T001", "a.kt", line = 1)),
            ages = mapOf("a.kt" to mapOf(1 to daysAgo(90))),
            longLivedDays = 90,
        ).single()

        assertEquals("true", enriched.metadata[GitMetadataKeys.TODO_LONG_LIVED])
    }

    @Test
    fun `non-todo findings never gain marker age`() {
        val enriched = apply(
            listOf(finding("RL-F001", "a.kt", line = 1)),
            ages = mapOf("a.kt" to mapOf(1 to daysAgo(500))),
        ).single()

        assertFalse(enriched.metadata.containsKey(GitMetadataKeys.TODO_AGE_DAYS))
    }

    @Test
    fun `findings pass through unchanged when git data is absent`() {
        val original = finding("RL-T001", "a.kt")

        val enriched = apply(listOf(original)).single()

        assertSame(original, enriched, "no data must mean no copy")
    }
}
