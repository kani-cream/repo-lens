# Repo Lens

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/33576-repo-lens?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/33576-repo-lens)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33576-repo-lens?label=downloads)](https://plugins.jetbrains.com/plugin/33576-repo-lens)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

A code review navigator for IntelliJ-based IDEs. Repo Lens collects review candidates —
large files and functions, deep nesting, dependency cycles, unused public API, oversized
diffs, long-lived TODOs, hotspots — into one tool window, jumps from any finding straight
to the code, and copies findings as structured Markdown you can paste into any AI chat or
review thread.

**Repo Lens calls no AI API, stores no keys, and never sends your source anywhere.**
Analysis, history queries, and formatting all happen locally; the clipboard is written
only when you press Copy.

## Installation

Requires IntelliJ IDEA 2026.1 or newer (2026.1 and 2026.2 are verified).

1. Settings → Plugins → **Marketplace** → search for **Repo Lens**
2. Install, then restart the IDE (Repo Lens does not support dynamic loading)

Marketplace page: **[plugins.jetbrains.com/plugin/33576-repo-lens](https://plugins.jetbrains.com/plugin/33576-repo-lens)**.
Installing a built zip from disk still works; see
[docs/installation.md](docs/installation.md) for that path, the optional-plugin
mapping, and uninstall notes.

## Quick start

1. Open the **Repo Lens** tool window (bottom panel, or Search Everywhere → "Repo Lens")
2. Pick a scope — start with **Local Changes** or **Branch Diff** for review work
3. Press **Analyze**
4. Single-click a finding for details; double-click or Enter to jump to the code
5. Select one or more findings and press **Copy for AI**, then paste anywhere

## Checks

| ID | Check | Measures | Default | Languages |
|---|---|---|---:|---|
| RL-F001 | Large File | physical lines | 800 | any text |
| RL-T001 | TODO / FIXME | marker presence (+ age via git) | — | any text |
| RL-G001 | Large Diff | changed lines vs. diff base | 300 | any text |
| RL-C001 | Large Type | type body lines | 500 | Java, Kotlin, JS/TS |
| RL-M001 | Large Function / Method | function body lines | 80 | Java, Kotlin, Go, JS/TS |
| RL-M002 | Too Many Parameters | declared parameters | 7 | Java, Kotlin, Go, JS/TS |
| RL-M003 | Deep Nesting | control-flow nesting depth | 5 | Java, Kotlin, Go, JS/TS |
| RL-D001 | Circular Dependency | package dependency cycles | — | Java, Kotlin |
| RL-U001 | Unused Candidate | unreferenced public declarations | — | Java, Kotlin |
| RL-H001 | Hotspot | commits × structural findings | ≥3 commits | any (needs git) |

Findings are review evidence, not verdicts: each carries its measured value, the
threshold, and a plain-language reason. Details in [docs/checks.md](docs/checks.md).

## Scopes

Project · Current File · Selected Files (Project View → *Analyze with Repo Lens*) ·
Module · Local Changes · **Branch Diff** (merge-base against a configured or
auto-detected base branch, uncommitted and untracked work included). Semantics in
[docs/scopes.md](docs/scopes.md).

## Copy for AI

Selected findings become one Markdown document: file, symbol, location, value/threshold,
confidence, git evidence, dependency paths, and a bounded code snippet (context lines and
a hard cap with explicit truncation). Two plain-text variants exist as **Copy** and
**Copy with Code**. No AI provider is involved — paste the result wherever you like.

## Noise control

- **Filters**: live search, severity, and check filters over the current result
- **Ignore**: right-click any finding → *Ignore Finding* (keyed by a stable ID)
- **Suppress rules**: `check-id | path-glob | symbol-glob` lines in Settings, e.g.
  `RL-M001 | **/*_test.go` hides large functions in Go tests
- **Exclusions**: glob patterns for paths that never get analyzed (defaults cover
  build output, dependency caches, lock files, virtual environments)

## Language support

Universal checks run on anything the IDE can read as text. Structure checks are supplied
by providers that load only when their language plugin is present — Settings → Tools →
Repo Lens shows exactly which are available in your IDE, and a missing provider is a
normal state, not an error. Git-based checks need the bundled Git plugin.

## Privacy

No network calls, no telemetry, no API keys, no source in logs, findings never persisted.
See [docs/privacy.md](docs/privacy.md).

## Known limitations

Collected honestly in [docs/known-limitations.md](docs/known-limitations.md).

## Development

Kotlin, two Gradle modules: `core` (pure JVM — domain model, analyzers, formatters; no
IntelliJ types) and `plugin` (IntelliJ Platform adapters, providers, UI). See
[docs/design.md](docs/design.md) (Japanese) for the architecture.

```bash
./gradlew :core:check            # domain tests, no IDE download
./gradlew build                  # everything (fetches the IntelliJ Platform)
./gradlew :plugin:verifyPlugin   # binary compatibility against 2026.1 / 2026.2
./gradlew runIde                 # sandbox IDE with the plugin installed
```

Manual verification procedure: [docs/manual-testing.md](docs/manual-testing.md)
(Japanese). Release history: [CHANGELOG.md](CHANGELOG.md).

### Releasing

Pushing a `v<version>` tag runs [.github/workflows/release.yml](.github/workflows/release.yml):
build and test, Plugin Verifier against both targets, a check that the tag matches the
`version` in `build.gradle.kts`, `publishPlugin` to the Marketplace, and a GitHub
release carrying the zip. It needs a Marketplace Personal Access Token stored as the
`MARKETPLACE_TOKEN` repository secret.

## License

[Apache License 2.0](LICENSE)
