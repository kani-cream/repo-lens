package com.kanicream.repolens

import com.kanicream.repolens.model.AnalysisScopeType
import com.kanicream.repolens.model.Severity
import java.util.Properties
import junit.framework.TestCase

/** The two bundles must stay in step, or one locale silently shows raw keys. */
class RepoLensBundleTest : TestCase() {

    private fun load(name: String): Properties {
        val props = Properties()
        javaClass.classLoader.getResourceAsStream(name)!!.reader(Charsets.UTF_8).use(props::load)
        return props
    }

    fun `test english and japanese bundles declare the same keys`() {
        val english = load("messages/RepoLensBundle.properties").keys.map { it.toString() }.toSet()
        val japanese = load("messages/RepoLensBundle_ja.properties").keys.map { it.toString() }.toSet()

        assertEquals("missing in ja: ${english - japanese}", emptySet<String>(), english - japanese)
        assertEquals("missing in en: ${japanese - english}", emptySet<String>(), japanese - english)
    }

    fun `test every scope and severity has a localized name`() {
        val keys = load("messages/RepoLensBundle.properties").keys.map { it.toString() }.toSet()

        AnalysisScopeType.entries.forEach { scope ->
            assertTrue("scope.${scope.name}", "scope.${scope.name}" in keys)
        }
        Severity.entries.forEach { severity ->
            assertTrue("severity.${severity.name}", "severity.${severity.name}" in keys)
        }
    }

    fun `test bundle resolves and formats parameters`() {
        assertEquals("Total 5", RepoLensBundle.message("toolwindow.status.total", 5))
        assertTrue(RepoLensBundle.message("toolwindow.analyze").isNotBlank())
    }
}
