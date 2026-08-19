# Installation

## Requirements

- **IntelliJ IDEA 2026.1 or newer.** 2026.1 and 2026.2 are verified with every release
  (Plugin Verifier + manual checks). Other IntelliJ-based IDEs of the same platform
  line should load Repo Lens Core, but are not routinely verified.
- Java support is bundled in IntelliJ IDEA; nothing extra is needed for Java/Kotlin
  structure analysis.

## Installing from the JetBrains Marketplace

The normal path. Repo Lens is published as
[Repo Lens](https://plugins.jetbrains.com/plugin/33576-repo-lens) (plugin ID
`com.kanicream.repolens`).

1. Settings → Plugins → **Marketplace** tab
2. Search for **Repo Lens** and press **Install**
3. **Restart the IDE** (Repo Lens does not support dynamic loading — the IDE will tell
   you a restart is needed)

## Installing from a ZIP

For a locally built version, or an older release taken from
[GitHub Releases](https://github.com/kani-cream/repo-lens/releases).

1. Obtain `plugin-<version>.zip` (build it with `./gradlew :plugin:buildPlugin`; it
   lands in `plugin/build/distributions/`)
2. Settings → Plugins → ⚙ (gear icon) → **Install Plugin from Disk…**
3. Select the zip, apply, and **restart the IDE**

## What the optional plugins unlock

Repo Lens loads and works without any of these; each one only adds capability. The
current state is always visible under Settings → Tools → Repo Lens → Language
Capabilities.

| Plugin | Unlocks |
|---|---|
| Java (bundled) | Java/Kotlin structure checks, Unused Candidate, Circular Dependency |
| Go (Marketplace, needs an Ultimate license at runtime) | Go structure checks |
| JavaScript (bundled with Ultimate) | JS / TS / JSX / TSX structure checks |
| Git (bundled) | Branch Diff scope, Large Diff, git evidence, TODO age, Hotspot |

A missing or disabled plugin is a normal state: the corresponding checks simply do not
run, and the capability list says why.

## Updating

Marketplace installs update themselves: the IDE offers the new version under Settings →
Plugins, and applying it asks for a restart. For a zip install, install the newer zip
the same way — the IDE replaces the previous version and asks for a restart.

## Uninstalling

Settings → Plugins → Repo Lens → Uninstall. Project-level settings live in
`.idea/repoLens.xml` inside each project and can be deleted freely.
