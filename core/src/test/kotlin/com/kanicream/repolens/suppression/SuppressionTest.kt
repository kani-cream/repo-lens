package com.kanicream.repolens.suppression

import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.model.SymbolInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuppressionTest {

    private fun finding(
        analyzerId: String,
        path: String,
        symbol: String? = null,
    ): Finding {
        val location = SourceLocation(path, 1, 1)
        return Finding(
            id = Finding.stableId(analyzerId, location),
            analyzerId = analyzerId,
            severity = Severity.WARNING,
            checkName = analyzerId,
            message = "m",
            location = location,
            symbol = symbol?.let(::SymbolInfo),
        )
    }

    // --- SuppressRule.parse ---

    @Test
    fun `parses full and partial rule lines`() {
        assertTrue(SuppressRule.parse("RL-M001 | **/generated/** | *.toString()") != null)
        assertTrue(SuppressRule.parse("RL-T001") != null)
        assertTrue(SuppressRule.parse("| **/test/**") != null)
        assertTrue(SuppressRule.parse("| | *.equals()") != null)
    }

    @Test
    fun `rejects blanks comments and unusable lines`() {
        assertNull(SuppressRule.parse(""))
        assertNull(SuppressRule.parse("   "))
        assertNull(SuppressRule.parse("# comment"))
        assertNull(SuppressRule.parse("| | |"))
        assertNull(SuppressRule.parse("a | b | c | d"))
    }

    @Test
    fun `check id restriction matches the analyzer id case-insensitively`() {
        val rule = SuppressRule.parse("rl-t001")!!

        assertTrue(rule.matches(finding("RL-T001", "a.kt")))
        assertFalse(rule.matches(finding("RL-F001", "a.kt")))
    }

    @Test
    fun `path restriction uses globs`() {
        val rule = SuppressRule.parse("| **/*_test.go")!!

        assertTrue(rule.matches(finding("RL-M001", "backend/internal/x_test.go")))
        assertFalse(rule.matches(finding("RL-M001", "backend/internal/x.go")))
    }

    @Test
    fun `symbol restriction never matches findings without a symbol`() {
        val rule = SuppressRule.parse("| | Test*")!!

        assertTrue(rule.matches(finding("RL-M001", "a.go", symbol = "TestFoo()")))
        assertFalse(rule.matches(finding("RL-M001", "a.go", symbol = "Foo()")))
        assertFalse(rule.matches(finding("RL-F001", "a.go", symbol = null)))
    }

    @Test
    fun `all restrictions must hold together`() {
        val rule = SuppressRule.parse("RL-M001 | **/*_test.go | Test*")!!

        assertTrue(rule.matches(finding("RL-M001", "x_test.go", "TestFoo()")))
        assertFalse(rule.matches(finding("RL-M001", "x.go", "TestFoo()")))
        assertFalse(rule.matches(finding("RL-M002", "x_test.go", "TestFoo()")))
    }

    // --- SuppressionPolicy ---

    @Test
    fun `ignored ids win and are reported as IGNORED`() {
        val target = finding("RL-T001", "a.kt")
        val policy = SuppressionPolicy(setOf(target.id), emptyList())

        assertEquals(SuppressionKind.IGNORED, policy.suppressionOf(target))
        assertNull(policy.suppressionOf(finding("RL-T001", "b.kt")))
    }

    @Test
    fun `rule matches are reported as RULE`() {
        val policy = SuppressionPolicy(emptySet(), SuppressRule.parseAll(listOf("RL-T001")))

        assertEquals(SuppressionKind.RULE, policy.suppressionOf(finding("RL-T001", "a.kt")))
    }

    @Test
    fun `partition preserves order within both halves`() {
        val keep1 = finding("RL-F001", "a.kt")
        val drop = finding("RL-T001", "b.kt")
        val keep2 = finding("RL-F001", "c.kt")
        val policy = SuppressionPolicy(emptySet(), SuppressRule.parseAll(listOf("RL-T001")))

        val (visible, suppressed) = policy.partition(listOf(keep1, drop, keep2))

        assertEquals(listOf(keep1, keep2), visible)
        assertEquals(listOf(drop), suppressed)
    }

    @Test
    fun `stable finding ids survive re-analysis of unchanged code`() {
        // The ignore feature depends on this: the same analyzer, file and range must
        // produce the same id on every run.
        assertEquals(finding("RL-T001", "a.kt").id, finding("RL-T001", "a.kt").id)
    }
}
