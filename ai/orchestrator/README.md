# Claude Orchestrator

This directory is the tracked control plane for local multi-Claude runs.

Tracked files:
- `README.md`: operating guide for local runs
- `SYSTEM-SPEC.md`: portable AI memory plus orchestration contract for recreating this setup in another repo
- `agents.json`: reusable worker definitions for planner, analyst, implementer, and reviewer roles
- `tasks/*.json`: task-plan files the coordinator can validate or execute

Runtime artifacts are intentionally kept outside `ai/` under a repo-local runtime root:
- default runtime root: `.claude-orchestrator/`
- run manifests: `.claude-orchestrator/runs/<run-id>/`
- isolated workspaces: `.claude-orchestrator/workspaces/<run-id>/<task-id>/`

Why this split:
- tracked specs stay versioned with the repo
- transient worker output does not pollute AI memory indexes
- the runtime root stays easy to inspect or delete from the repo root
- workers can run in isolated `copy` or `worktree` workspaces
- repo-copy workspaces ignore `.claude-orchestrator/`, which prevents recursive copying during parallel or overlapping runs

Primary commands:

```powershell
scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json
scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-review.json --dry-run
scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2
scripts/claude-orchestrator.ps1 plan "Investigate scatter allocation follow-up" --dry-run
```

Dry runs:
- `plan --dry-run` prints the planner request and target output path without invoking Claude
- `run --dry-run` writes the run manifest, task prompts, and worker command files without invoking Claude or creating repo copies/worktrees
- dry-run planner/task payloads include `promptSections` plus `promptBudget`, and task records include `prompt_chars` / `prompt_estimated_tokens` so you can budget prompt size before spending Claude tokens

Workspace modes:
- `copy`: isolated filesystem copy of the current repo state; safe default
- `worktree`: detached git worktree rooted at `HEAD`; requires a clean repo
- `repo`: live repo root; high-risk and opt-in only

Context discipline:
- worker prompts default to `contextMode = minimal`
- minimal mode includes the shared summary, the task's own file hints, merged constraints, dependency outputs, and only task-local validation hints
- full shared file and validation context is opt-in via `contextMode = full`
- dependency summaries and prompt-facing list sections are compacted so worker prompts stay bounded as plans grow
- copy-mode workspace hydration only pulls explicit file hints, skips directories, and skips files above `512 KB`

Token and cost visibility:
- each task record captures the resolved model, prompt size, and Claude usage when the CLI returns it
- task records and planner dry-runs include section-level prompt accounting (`prompt_sections` / `promptSections`) plus budget results (`prompt_budget` / `promptBudget`)
- agent/task definitions may set `maxPromptEstimatedTokens` or `maxPromptChars`; the coordinator fails oversized prompts locally before invoking Claude
- run manifests and `run --json` output include `usageTotals` with prompt estimates plus aggregated input, output, cache, and cost fields
- per-task usage lives in the task record `usage` field; dry runs still show prompt estimates even when usage is `null`

Model selection:
- use `modelProfile = simple` for `claude-haiku-4-5`
- use `modelProfile = balanced` for `claude-sonnet-4-6`
- use `modelProfile = complex` for `claude-opus-4-6`
- `model` still works as an explicit override and wins over `modelProfile`
- `ai/orchestrator/tasks/example-review.json` and `ai/orchestrator/tasks/example-parallel.json` show `simple` overrides for cheap doc-summary work

Concurrency:
- ready tasks run in batches up to `--max-parallel`
- each run gets a unique `run-id`, run manifest, and per-task workspace under `.claude-orchestrator/`
- only schedule parallel workers when their declared file scopes do not materially overlap
- `ai/orchestrator/tasks/example-parallel.json` is the tracked sample for concurrent-ready tasks

Coordinator rules:
- workers must not update `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`
- the coordinator owns memory updates, final summaries, and merge decisions
- prefer `copy` mode unless a task clearly needs git metadata
- review worker outputs before applying or cherry-picking edits back into the main repo
