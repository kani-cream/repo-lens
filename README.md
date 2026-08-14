# repo-lens

Repo Lens is an IntelliJ IDEA plugin that works as a code review navigator: it collects
review candidates into one tool window, jumps from a finding to the code, and copies
findings as AI-ready Markdown. It calls no AI API, keeps no API keys, and never sends
source code anywhere.

See [docs/design.md](docs/design.md) and [docs/milestones](docs/milestones/README.md)
for the full specification.

## Modules

| Module | Contents | Dependencies |
|---|---|---|
| `core` | Domain model, analyzer SPI, Tier 0 analyzers, orchestrator, Markdown formatter | Pure Kotlin/JVM (no IntelliJ types) |
| `plugin` | Tool window UI, settings, navigation, clipboard, platform adapters, `plugin.xml` | IntelliJ Platform (IDEA 2026.1 baseline) |

## Build

Requires a JDK 21 toolchain; the build provisions one automatically if none is installed.

```bash
# Domain/analyzer unit tests (no IntelliJ distribution required)
./gradlew :core:check

# Full plugin build, fixture tests, verifier (requires access to JetBrains repositories)
./gradlew build
./gradlew :plugin:verifyPlugin

# Opt-in smoke run of the Tier 0 analyzers over a real checkout
./gradlew :core:test --tests '*RealRepositorySmokeTest*' -DrepoLens.smokeRoot=/path/to/repo -i
```
