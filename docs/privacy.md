# Privacy

Repo Lens is local-first by design, and this page is the whole contract.

## What Repo Lens does not do

- **No network calls.** The plugin implements no HTTP client, no sockets, no telemetry,
  no update checks. (The IDE itself may talk to JetBrains — e.g. the plugin manager —
  but Repo Lens adds nothing to that.)
- **No AI API calls and no API keys.** "Copy for AI" produces Markdown on your
  clipboard; where you paste it is your decision and outside Repo Lens' responsibility.
- **No source code in logs.** Diagnostics record analyzer IDs, counts, and timings
  only — never file content. Analyzer failures record the exception type, not the file.
- **No persistence of findings.** Results live in memory for the session; nothing about
  your code is written to disk. The only thing stored is your configuration
  (thresholds, rules, ignores) in the project's `.idea/repoLens.xml`.

## What touches your code, and where it stays

- Analysis reads files through the IDE's own file system and parser APIs, in memory.
- Git-based features run `git` locally against your repository (diff, log, blame, via
  the IDE's bundled Git integration). Nothing is fetched from or pushed to any remote.
- The clipboard is written only when you press one of the Copy actions, with a bounded
  code snippet (configurable context and a hard line cap with visible truncation).

Adding a language provider or a new check must not change any of the above; this
contract outranks features (see docs/design.md §16).
