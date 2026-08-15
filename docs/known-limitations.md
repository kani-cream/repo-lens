# Known limitations

Collected as they were discovered; each is a deliberate trade-off or an accepted gap,
not an unknown. Where behaviour could surprise, the surprise is documented here first.

## Findings and identity

- **Stable IDs contain line numbers.** An ignored finding whose code moves (lines
  shift after an edit) gets a new ID and reappears. That is the stable-key design
  working as specified; use suppress rules for durable exclusions. Circular Dependency
  is the exception — its identity derives from the member packages and survives
  movement.
- **Thresholds are global per project**, not per language or file kind. Real-world
  consequences observed: Go table tests and React components exceed the default
  function-body threshold easily. Suppress rules (e.g. `RL-M001 | **/*_test.go`) are
  the intended mitigation until per-kind thresholds are justified.

## Language coverage

- Structure checks cover Java, Kotlin, Go, and JS/TS/JSX/TSX. Other languages get the
  universal checks only.
- **Anonymous functions** (Go function literals, JS arrows/function expressions without
  a surfaced name) are not extracted as declarations; their bodies count toward the
  enclosing function's nesting instead.
- **Go has no Large Type findings** — methods live outside their receiver type, so a
  type-body size would measure nothing meaningful.
- **Circular Dependency covers dot-namespace packages** (Java/Kotlin). Go cannot have
  import cycles by language design; JS/TS module cycles are unimplemented. Edges come
  from imports only — fully-qualified references without an import, and reflection, do
  not create edges. Module-granularity cycles are not implemented (build tools largely
  prevent them).
- **Unused Candidate** searches references; reflection, DI, serialization, and external
  callers are invisible (every finding says so, with `Confidence: Low`). Names too
  common to search cheaply are skipped rather than searched expensively. Kotlin
  specifics beyond the shared UAST machinery (e.g. extension-function receivers) have
  limited coverage.

## Git

- **First repository only** in multi-repository projects (Branch Diff, history,
  hotspots).
- When the project base directory is not the repository root, history enrichment
  passes findings through unmatched rather than risking a wrong mapping.
- **Marker age is the line's last-touch time** (blame semantics), not first
  introduction — editing anything on the line resets it. Uncommitted lines report
  age 0.
- Paths containing tabs or newlines are outside the diff parsing's scope.
- Deleted files produce no Large Diff finding — there is nothing to navigate to.

## Platform

- **No dynamic plugin loading**: installing or updating Repo Lens requires an IDE
  restart (the class loader cannot be unloaded; Plugin Verifier's optimistic
  "probably dynamic" verdict is known to be wrong here).
- After files change outside the running IDE (branch switch, external tools), the
  platform's index can briefly be stale; the first analysis touching such a file may
  log an `Outdated stub in index` error **attributed to Repo Lens**. The platform
  rebuilds automatically and the analysis is unaffected (`failures=0`); re-run if a
  result looks incomplete.
- Test sources are deliberately excluded from Unused Candidate (frameworks call test
  code) and from the dependency graph (tests reuse production package names and import
  broadly, fabricating coupling).

## Performance envelope

- The per-run history query is bounded by the configured window and cached until HEAD
  moves; blame runs only for files carrying TODO findings, capped at 100 files.
- Structure parsing is memoized per run; text is deliberately not cached (memory over
  speed for large projects).
