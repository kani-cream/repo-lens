# Changelog

Product versions are release ordinals; the `vX.Y` names in `docs/milestones/` are theme
milestones and do not map 1:1 (see `docs/milestones/README.md`).

## 1.0.0 — Initial JetBrains Marketplace release

- LICENSE (Apache-2.0), Marketplace plugin icon, and `<change-notes>` for the
  initial public release.
- Release hardening toward 1.0: untracked files included in Branch Diff, hotspot no
  longer credits package-group findings to single files, per-declaration reference
  searches, cached history queries, `enrich=` timing in diagnostics.
- Documentation set for release: README, checks/scopes/installation/privacy/known
  limitations, this changelog, CI.

## 0.4.0 — Git-aware review (milestone v0.3)

- **Branch Diff scope**: merge-base against a configured or auto-detected base branch,
  working tree included; reasoned failure messages
- **Large Diff (RL-G001)**: changed lines vs. the diff base, default 300
- **Git evidence** on findings: commits, authors, last-modified age within a bounded
  window (one repository-wide query per run)
- **Long-lived TODO**: marker age from blame, labeled at a configurable threshold
- **Hotspot (RL-H001)**: commits × structural findings, fully explainable score
- Column sorting; failed runs clear stale results; focus lands on the findings table

## 0.3.0 — Structural analysis (milestone v0.2)

- **Ignore / suppress**: per-finding ignore on stable IDs, `check | path | symbol`
  suppress rules, Show hidden toggle with a split hidden count
- **Circular Dependency (RL-D001)**: package-level SCCs with a real representative
  cycle, per-edge evidence, movement-stable identity
- **Unused Candidate (RL-U001)**: unreferenced public declarations with
  `Confidence: Low` and stated blind spots; first index-dependent check with a visible
  indexing skip
- Confidence on the finding model; analyzer skip reporting

## 0.2.0 — Language providers (milestone v0.4, pulled forward)

- **Go** and **JavaScript / TypeScript / JSX / TSX** structure providers on the same
  seam as UAST, both optional at runtime; zero core changes required (the seam's
  proof)
- **Language Capabilities** view in Settings; per-analyzer diagnostics timing
- Per-run memoization; language-neutral check names; release-ordinal versioning;
  monotonic timing

## 0.1.0 — Core review workflow (milestone v0.1)

- Tool window with findings table, detail pane, filters, and three copy formats
  (Copy / Copy with Code / Copy for AI with bounded snippets)
- Scopes: Project, Current File, Selected Files, Module, Local Changes
- Checks: Large File, TODO / FIXME, Large Type, Large Function / Method, Too Many
  Parameters, Deep Nesting (Java/Kotlin via UAST)
- Navigation from findings; per-project settings; exclusion globs; no network, no
  telemetry, no persistence of findings
