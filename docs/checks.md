# Checks reference

Every finding carries: the check name, severity, file path (project-relative), line
range, a plain-language reason, and — where applicable — a symbol, the measured value,
the threshold, a confidence level, and git evidence. Findings are review candidates,
never verdicts.

All thresholds live in Settings → Tools → Repo Lens and are stored per project. Every
check can be disabled there individually.

## RL-F001 — Large File

Physical line count (blank lines and comments included) of any text file. Default
threshold **800**. Language-independent; works even where no parser exists.

## RL-T001 — TODO / FIXME

Whole-word, case-insensitive scan for the configured markers (default `TODO`, `FIXME`)
in any text file. One finding per line, carrying the marker type and the matched text.

With the Git plugin available, each marker also gains its **age** (last time the line was
touched, from blame). Markers older than the configured age (default **90 days**,
inclusive) are labeled *long-lived* and say so in their reason. Uncommitted lines report
age 0. Without git data the finding is a plain TODO/FIXME finding.

## RL-G001 — Large Diff

Changed lines (added + deleted, as git reports them) against the diff base, for
diff-based scopes. Default threshold **300**. Untracked files count their whole content
as the addition. Outside diff scopes this check is inert.

## RL-C001 — Large Type

Body lines of a class, interface, object, or similar container. Default **500**.
Supplied by structure providers: UAST (Java/Kotlin) and JS/TS. Go deliberately produces
no type findings — methods live outside their receiver type, so a type-body size would
measure nothing meaningful.

## RL-M001 — Large Function / Method

Body lines of a function or method (the declaration range is reported for navigation;
the metric counts the body). Default **80**. Java, Kotlin, Go, JS/TS. Anonymous
functions are not extracted as declarations; they count toward nesting instead.

## RL-M002 — Too Many Parameters

Declared parameters. Default **7**. Go counting follows Go's grammar: `a, b int` is two,
an unnamed parameter is one, a variadic parameter is one, receivers are not parameters.
Explicit constructors are included; synthesized members are not.

## RL-M003 — Deep Nesting

Deepest nesting of control-flow constructs in one function body. Default **5**.
Counted constructs per language family:

- Java/Kotlin: `if`, `when`/`switch`, all loops, `try`, lambdas (`else if` counts one
  level per `if`, as the AST models it)
- Go: `if`, all `for` forms, `switch` (expression and type), `select`, function literals
- JS/TS: `if`, all loops, `switch`, `try`, and nested function expressions — callbacks
  and arrows, where JS depth actually lives

## RL-D001 — Circular Dependency

Cycles in the project's package dependency graph (dot-namespace languages; imports
resolve to the longest known project package, so JDK/library imports never create
edges). One finding per strongly connected group: the members, a representative cycle
that provably exists, and per-edge `file:line` evidence. Navigation lands on an import
that closes the loop; the finding's identity derives from the member packages, so
ignoring it survives code movement. Test sources contribute no edges. Go is excluded by
language design (its compiler forbids import cycles); JS/TS module cycles are a future
consideration.

## RL-U001 — Unused Candidate

Public declarations (types, functions, methods) with no reference anywhere in the
project. **Candidate, not verdict** — every finding states the blind spots: reflection,
dependency injection, serialization, and callers outside the project are invisible to
reference search, hence `Confidence: Low`.

Skipped up front: non-public members, constructors, `main`, overrides, annotated members
(annotations are how frameworks take over calling; synthesized nullability annotations
do not count), language-synthesized accessors (property getters, `valueOf`, data-class
members, companions), test sources, and names too common to search cheaply.

Needs the index; while the IDE is indexing this check skips with a visible reason.

## RL-H001 — Hotspot

Files that changed at least the configured number of times (default **3**) within the
history window (default **90 days**) *and* carry structural findings (RL-C001, RL-M001,
RL-M002, RL-M003). Score = commits × structural findings — deliberately naive so it
stays arguable: the message spells out every component. Circular Dependency does not
count (its finding models a package group, not a file). Tier-0-only files never qualify.

## Git evidence on all findings

When the Git plugin is available, every finding on a file with history in the window
gains `Git: N commit(s) by M author(s) in the last W days, last modified X day(s) ago`
in the detail pane and Copy output. One repository-wide history query runs per analysis
(cached until HEAD moves); blame runs only for files carrying TODO findings.
