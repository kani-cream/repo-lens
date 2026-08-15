# Scopes reference

The scope decides which files one analysis run looks at. Pick it in the tool window;
Analyze re-runs the current scope.

| Scope | Files analyzed |
|---|---|
| Project | everything under the project content roots |
| Current File | the file open in the editor |
| Selected Files | what you picked in the Project View (directories expand recursively) |
| Module | the module containing the current file |
| Local Changes | files the VCS reports as changed or untracked |
| Branch Diff | everything different from the base branch, uncommitted and untracked work included |

## Rules that apply across scopes

- **Exclusions** (Settings → Repo Lens → Exclusions) filter every scope, with one
  deliberate exception: a file you picked *explicitly* (Current File, or a file — not a
  directory — chosen in Selected Files) is analyzed even if an exclusion pattern would
  hide it. You asked for that file; directories you expand, module walks, VCS lists and
  diffs still honour the rules, so a regenerated lock file does not come back as a
  finding.
- Binary files are always skipped.
- Scope resolution reads UI state (open file, selection) at the moment you press
  Analyze; the file walk itself happens in the background.

## Selected Files

The Project View selection is only reachable through an action, so this scope is driven
by right-click → **Analyze with Repo Lens** (same mechanism as the IDE's own Inspect
Code). The tool window remembers the selection, so pressing Analyze again re-runs it.

## Local Changes

Files that differ from the current revision as the IDE's VCS integration sees them,
plus untracked files. Committing (or reverting) removes a file from this scope; the
scope is re-read each run. Requires a configured VCS — without one the scope reports
why instead of showing an empty result.

## Branch Diff

Answers "what would a reviewer of this branch look at":

- Base branch from Settings → Repo Lens → Git; blank auto-detects `origin/main`,
  `origin/master`, `main`, `master` (first that exists)
- The diff is taken from the **merge-base** of the base branch and HEAD to the working
  tree — committed branch work, staged, unstaged, **and untracked files** all included
- Deleted files are dropped (nothing left to analyze or navigate to)
- Each file carries its change info (`added` / `modified` / `renamed`, +added/−deleted
  lines), which feeds the Large Diff check and the Copy output
- Requires the Git plugin; failures (no repository, unknown base branch) are reported
  as a status message with the fix, and the result table is cleared so stale findings
  cannot be mistaken for current ones

## Unavailable scopes

A scope that cannot run right now explains itself in the status area ("No file is open
in the editor", "No version control system is configured…", "Base branch 'x' does not
exist…"). Unavailability is a normal state, not an error.
