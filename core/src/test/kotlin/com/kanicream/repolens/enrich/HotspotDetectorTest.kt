package com.kanicream.repolens.enrich

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotspotDetectorTest {

    private fun finding(analyzerId: String, checkName: String, path: String): Finding {
        val location = SourceLocation(path, 10, 20)
        return Finding(
            id = Finding.stableId(analyzerId, location),
            analyzerId = analyzerId,
            severity = Severity.WARNING,
            checkName = checkName,
            message = "m",
            location = location,
        )
    }

    private fun detect(
        findings: List<Finding>,
        history: Map<String, FileHistory>,
        minCommits: Int = 3,
    ) = HotspotDetector.detect(findings, history, minCommits, historyWindowDays = 90)

    @Test
    fun `frequently changed file with structural findings becomes a hotspot with explainable score`() {
        val findings = listOf(
            finding("RL-M001", "Large Function / Method", "src/Service.kt"),
            finding("RL-M003", "Deep Nesting", "src/Service.kt"),
        )
        val history = mapOf("src/Service.kt" to FileHistory(6, 2, 0))

        val hotspots = detect(findings, history)

        val hotspot = hotspots.single()
        assertEquals("RL-H001", hotspot.analyzerId)
        assertEquals("RL-H001:src/Service.kt", hotspot.id)
        assertEquals(12.0, hotspot.measuredValue)
        assertEquals("6", hotspot.metadata[HotspotDetector.METADATA_COMMITS])
        assertEquals("2", hotspot.metadata[HotspotDetector.METADATA_AUTHORS])
        assertEquals("2", hotspot.metadata[HotspotDetector.METADATA_STRUCTURAL])
        assertEquals(
            "Deep Nesting, Large Function / Method",
            hotspot.metadata[HotspotDetector.METADATA_CHECKS],
        )
        assertTrue(hotspot.message.contains("6 × 2 = 12"))
    }

    @Test
    fun `tier 0 findings alone never make a hotspot`() {
        val findings = listOf(
            finding("RL-F001", "Large File", "src/Big.txt"),
            finding("RL-T001", "TODO / FIXME", "src/Big.txt"),
        )
        val history = mapOf("src/Big.txt" to FileHistory(50, 5, 0))

        assertTrue(detect(findings, history).isEmpty())
    }

    @Test
    fun `files below the commit threshold are not hotspots`() {
        val findings = listOf(finding("RL-M001", "Large Function / Method", "a.kt"))
        val history = mapOf("a.kt" to FileHistory(2, 1, 0))

        assertTrue(detect(findings, history, minCommits = 3).isEmpty())
        assertEquals(1, detect(findings, history, minCommits = 2).size)
    }

    @Test
    fun `files without history are not hotspots`() {
        val findings = listOf(finding("RL-C001", "Large Type", "a.kt"))

        assertTrue(detect(findings, emptyMap()).isEmpty())
    }

    @Test
    fun `hotspots are ordered by score descending`() {
        val findings = listOf(
            finding("RL-M001", "Large Function / Method", "low.kt"),
            finding("RL-M001", "Large Function / Method", "high.kt"),
            finding("RL-M003", "Deep Nesting", "high.kt"),
        )
        val history = mapOf(
            "low.kt" to FileHistory(3, 1, 0),
            "high.kt" to FileHistory(10, 3, 0),
        )

        val hotspots = detect(findings, history)

        assertEquals(listOf("high.kt", "low.kt"), hotspots.map { it.location.filePath })
        assertEquals(listOf(20.0, 3.0), hotspots.map { it.measuredValue })
    }
}
