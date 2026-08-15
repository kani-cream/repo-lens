package com.kanicream.repolens.structure

import com.kanicream.repolens.RepoLensBundle

/**
 * Availability of one structure provider in the current IDE.
 *
 * Unavailable is a normal state, not an error (docs/design.md §4.6): it means the
 * language plugin the provider rides on is not installed or not enabled, and the
 * structure checks silently skip that language while Tier 0 still runs.
 */
data class ProviderCapability(
    val displayName: String,
    /** What the provider needs, phrased for the settings UI. */
    val requirement: String,
    val available: Boolean,
)

/**
 * The catalog of structure providers Repo Lens ships, matched against what actually
 * loaded. The catalog is static on purpose: an optional descriptor that failed to load
 * leaves no trace at runtime, so "what could exist" must be spelled out to explain
 * "what is missing".
 */
object ProviderCapabilities {

    private data class Entry(
        val nameKey: String,
        val requirementKey: String,
        val providerClassName: String,
    )

    private val ENTRIES = listOf(
        Entry(
            nameKey = "capability.uast",
            requirementKey = "capability.uast.requirement",
            providerClassName = "com.kanicream.repolens.structure.uast.UastCodeStructureProvider",
        ),
        Entry(
            nameKey = "capability.go",
            requirementKey = "capability.go.requirement",
            providerClassName = "com.kanicream.repolens.structure.go.GoCodeStructureProvider",
        ),
        Entry(
            nameKey = "capability.jsts",
            requirementKey = "capability.jsts.requirement",
            providerClassName = "com.kanicream.repolens.structure.jsts.JsTsCodeStructureProvider",
        ),
    )

    /** Current availability, computed from the extensions that actually loaded. */
    fun current(): List<ProviderCapability> {
        val loaded = CodeStructureProvider.EP_NAME.extensionList.map { it.javaClass.name }.toSet()
        return ENTRIES.map { entry ->
            ProviderCapability(
                displayName = RepoLensBundle.message(entry.nameKey),
                requirement = RepoLensBundle.message(entry.requirementKey),
                available = entry.providerClassName in loaded,
            )
        }
    }

    /** Class names the catalog knows; used by tests to detect drift. */
    fun catalogedClassNames(): Set<String> = ENTRIES.map { it.providerClassName }.toSet()
}
