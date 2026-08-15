package com.kanicream.repolens

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kanicream.repolens.structure.CodeStructureProvider
import com.kanicream.repolens.structure.ProviderCapabilities

class ProviderCapabilitiesTest : BasePlatformTestCase() {

    fun `test all providers loaded in this harness are reported available`() {
        // The test harness loads Java, Kotlin, Go and JavaScript, so every cataloged
        // provider should be present and available.
        val capabilities = ProviderCapabilities.current()
        assertSize(3, capabilities)
        capabilities.forEach { capability ->
            assertTrue("${capability.displayName} should be available", capability.available)
            assertTrue(capability.requirement.isNotBlank())
        }
    }

    fun `test the catalog covers every registered provider`() {
        // Drift guard: a provider registered on the extension point but missing from the
        // catalog would be invisible in the capability UI.
        val registered = CodeStructureProvider.EP_NAME.extensionList.map { it.javaClass.name }.toSet()
        val cataloged = ProviderCapabilities.catalogedClassNames()
        assertTrue(
            "registered=$registered cataloged=$cataloged",
            cataloged.containsAll(registered),
        )
    }
}
