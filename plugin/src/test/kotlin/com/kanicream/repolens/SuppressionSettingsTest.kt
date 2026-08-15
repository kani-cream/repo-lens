package com.kanicream.repolens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.model.Finding
import com.kanicream.repolens.model.Severity
import com.kanicream.repolens.model.SourceLocation
import com.kanicream.repolens.settings.RepoLensSettings
import com.kanicream.repolens.suppression.SuppressionKind

class SuppressionSettingsTest : BasePlatformTestCase() {

    private fun finding(path: String): Finding {
        val location = SourceLocation(path, 1, 1)
        return Finding(
            id = Finding.stableId("RL-T001", location),
            analyzerId = "RL-T001",
            severity = Severity.INFO,
            checkName = "TODO / FIXME",
            message = "m",
            location = location,
        )
    }

    fun `test ignoring a finding suppresses it through the policy and can be undone`() {
        val settings = RepoLensSettings.getInstance(project)
        val target = finding("src/App.kt")

        settings.setFindingIgnored(target.id, true)
        assertEquals(SuppressionKind.IGNORED, settings.suppressionPolicy().suppressionOf(target))
        assertTrue(settings.isFindingIgnored(target.id))

        settings.setFindingIgnored(target.id, false)
        assertNull(settings.suppressionPolicy().suppressionOf(target))
    }

    fun `test suppress rule lines from settings reach the policy`() {
        val settings = RepoLensSettings.getInstance(project)
        settings.getState().suppressRuleLines = mutableListOf("RL-T001 | **/generated/**")

        val policy = settings.suppressionPolicy()
        assertEquals(SuppressionKind.RULE, policy.suppressionOf(finding("app/generated/File.kt")))
        assertNull(policy.suppressionOf(finding("app/src/File.kt")))
    }

    fun `test ignoring twice keeps a single entry`() {
        val settings = RepoLensSettings.getInstance(project)
        val target = finding("src/Twice.kt")

        settings.setFindingIgnored(target.id, true)
        settings.setFindingIgnored(target.id, true)

        assertEquals(1, settings.getState().ignoredFindingIds.count { it == target.id })
    }
}
