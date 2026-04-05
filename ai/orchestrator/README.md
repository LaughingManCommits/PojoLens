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
scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-review.json --dry-run --json
scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2
scripts/claude-orchestrator.ps1 retry .claude-orchestrator/runs/<run-id> --task <task-id> --dry-run --json
scripts/claude-orchestrator.ps1 review .claude-orchestrator/runs/<run-id> --json
scripts/claude-orchestrator.ps1 export-patch .claude-orchestrator/runs/<run-id> --out .claude-orchestrator/runs/<run-id>/review/combined.patch --json
scripts/claude-orchestrator.ps1 promote .claude-orchestrator/runs/<run-id> --dry-run --json
scripts/claude-orchestrator.ps1 validate-run .claude-orchestrator/runs/<run-id> --dry-run --json
scripts/claude-orchestrator.ps1 validate-run .claude-orchestrator/runs/<run-id> --intents-only --dry-run --json
scripts/claude-orchestrator.ps1 validate-run .claude-orchestrator/runs/<run-id> --include-status blocked --allow-unsafe-commands --dry-run --json
scripts/claude-orchestrator.ps1 cleanup .claude-orchestrator/runs/<run-id> --json
scripts/claude-orchestrator.ps1 plan "Investigate scatter allocation follow-up" --dry-run
```

Dry runs:
- `plan --dry-run` prints the planner request and target output path without invoking Claude
- `run --dry-run` writes the run manifest, task prompts, and worker command files without invoking Claude or creating repo copies/worktrees
- dry-run planner/task payloads include `promptSections` plus `promptBudget`, and task records include `prompt_chars` / `prompt_estimated_tokens` so you can budget prompt size before spending Claude tokens
- `validate --json` now reports declared agent defaults plus each task's effective `workerValidationMode` and source (`override`, `task`, `agent`, or `default`)

Workspace modes:
- `copy`: isolated sparse filesystem copy seeded with `AGENTS.md`, `ai/AGENTS.md`, and explicit file hints; safe default
- `worktree`: detached git worktree rooted at `HEAD`; requires a clean repo
- `repo`: live repo root; high-risk and opt-in only

Context discipline:
- worker prompts default to `contextMode = minimal`
- minimal mode includes the shared summary, the task's own file hints, merged constraints, dependency outputs, and only task-local validation hints
- dependency outputs now carry a bounded upstream handoff: summary plus a few key notes when available, or explicit unknown markers when an upstream worker could not verify those sections, so downstream workers do not need to inspect prior task artifacts directly
- full shared file and validation context is opt-in via `contextMode = full`
- dependency summaries and prompt-facing list sections are compacted so worker prompts stay bounded as plans grow
- copy-mode workspace hydration seeds `AGENTS.md` plus `ai/AGENTS.md`, then copies explicit file hints, skips directory hints, and skips files above `512 KB`

Token and cost visibility:
- each task record captures the resolved model, prompt size, and Claude usage when the CLI returns it
- task records and planner dry-runs include section-level prompt accounting (`prompt_sections` / `promptSections`) plus budget results (`prompt_budget` / `promptBudget`)
- agent/task definitions may set `maxPromptEstimatedTokens` or `maxPromptChars`; the coordinator fails oversized prompts locally before invoking Claude
- run manifests and `run --json` output include `usageTotals` with prompt estimates plus aggregated input, output, cache, and cost fields
- per-task usage lives in the task record `usage` field; dry runs still show prompt estimates even when usage is `null`
- live doc-summary runs showed prompt text itself staying well under the configured ceilings; the larger cost driver is worker exploration and oversized JSON payloads, so worker prompts now cap `summary`, `notes`, `followUps`, and validation suggestions aggressively
- worker results now distinguish known-empty from unknown list fields, and may also emit structured `validationIntents`; use `[]` for known-empty `filesTouched` / `validationCommands` / `followUps` / `notes`, use `null` only when those values are genuinely unknown, and task records preserve that in `unknown_fields` / `unknownFields`
- worker prompts now treat raw `validationCommands` as a deprecated compatibility fallback; prefer structured `validationIntents` whenever the suggestion is a direct repo-script or tool invocation
- `run` and `retry` now treat `--worker-validation-mode` as an explicit override; tracked agent/task `workerValidationMode` settings can drive the same enforcement with no CLI flag
- the tracked `analyst`, `implementer`, and `reviewer` agents now default to `workerValidationMode = intents-only`; the tracked sample plans inherit agent defaults and reserve task-level `workerValidationMode = compat` for exceptions
- live planner, worker, and coordinator validation waits now emit phase-tagged slop-status lines on interactive `stderr` (for example `[TASK][FLOW] Slopsloshing .. (...)`) while subprocesses are still running, so `stdout` JSON remains machine-readable

Model selection:
- use `modelProfile = simple` for `claude-haiku-4-5`
- use `modelProfile = balanced` for `claude-sonnet-4-6`
- use `modelProfile = complex` for `claude-opus-4-6`
- `model` still works as an explicit override and wins over `modelProfile`
- `ai/orchestrator/tasks/example-review.json` and `ai/orchestrator/tasks/example-parallel.json` show `simple` overrides for cheap doc-summary work

Concurrency:
- ready tasks run in batches up to `--max-parallel`
- each run gets a unique `run-id`, run manifest, and per-task workspace under `.claude-orchestrator/`
- validate and run manifests expose `parallelConflicts` for overlapping write-capable task scopes
- overlapping write-capable tasks are serialized conservatively by declared file scope even when they are dependency-ready together
- `ai/orchestrator/tasks/example-parallel.json` is the tracked sample for concurrent-ready tasks

Coordinator rules:
- workers must not update `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`
- workers in `copy` or `worktree` mode should treat prompt dependency outputs as the only upstream handoff and should not inspect other task workspaces or prior run artifacts directly
- task records capture `actual_files_touched` from workspace diffs plus `protected_path_violations`; protected-path edits fail the task record
- `retry` can rerun failed or blocked tasks from a prior manifest while seeding already-completed dependencies from the earlier run
- retry runs preserve an explicit source-run `workerValidationModeOverride` when one exists; otherwise the effective mode resolves again from tracked task/agent settings, with legacy manifest fallback for older runs
- task plans may declare `workerValidationMode` per task, and agent definitions may declare role-wide defaults; precedence is CLI override, then task, then agent, then `compat`
- prefer agent defaults when a role should usually enforce the same policy across many tasks; use task-level `workerValidationMode = compat` only for targeted deviations
- the tracked `example-review.json` and `example-parallel.json` samples now inherit intent-only enforcement from the `analyst` and `reviewer` agent defaults instead of repeating the same task-level setting
- `validate-run` accepts both raw `validation_commands` and structured `validation_intents`, defaults to `completed` tasks only unless `--include-status` expands the policy, can execute accepted suggestions from repo root, and records coordinator-run results separately from worker suggestions in the run manifest
- structured `validation_intents` currently support `repo-script` and `tool` kinds, render back to command text for review output, and execute without shell wrapping
- accepted raw `validation_commands` are still preserved verbatim for review output, but the coordinator now normalizes direct tool/repo-script shapes into an argv intent internally so they can run without `shell=True`; raw command strings are now explicitly a compatibility path
- `validate-run --intents-only` rejects raw legacy `validation_commands` even when they normalize cleanly, accepts only worker-emitted structured intents, and reports which tasks still suggested legacy raw commands so migration is visible in the summary
- run manifests and `run --json` / `retry --json` payloads now include the summarized `workerValidationMode`, any explicit `workerValidationModeOverride`, `taskWorkerValidationModes`, and `taskWorkerValidationModeSources`; task records also carry `worker_validation_mode_source`
- when tracked task or agent modes differ inside the same run, run payloads surface `workerValidationMode = "mixed"` instead of collapsing the run to a misleading single mode
- `validate-run` still enforces command quality by default: direct repo-script or approved tool invocations are allowed, while shell-composed commands (`|`, `&&`, redirection, etc.) or unknown entrypoints are rejected unless `--allow-unsafe-commands` is used explicitly
- worker JSON is normalized coordinator-side before it becomes a task record: summaries are compacted, `notes` / `followUps` / `validationCommands` are capped, malformed status or list fields fail the task, structured `validationIntents` are normalized, and nullable list fields preserve explicit unknowns instead of collapsing into `[]`
- `review` summarizes per-task file diffs from worker workspaces; `export-patch` writes unified diffs for copy/worktree runs; `promote` applies reviewed copy/worktree changes back into the repo
- `promote` refuses protected-path violations, repo-mode records, path traversal, and ambiguous multi-task ownership of the same changed file
- `cleanup` removes run artifacts and deletes detached worktrees created for that run
- the coordinator owns memory updates, final summaries, and merge decisions
- prefer `copy` mode unless a task clearly needs git metadata
- review worker outputs before live promotion; use `promote --dry-run` when you want the adoption summary without mutating the repo
