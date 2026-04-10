# AI Memory + Orchestration Spec

This file defines the portable contract for recreating the repository's AI memory and local Claude orchestration system in another project.

## Goals

- keep durable repo knowledge in tracked Markdown, not only in transient chat context
- keep derived search and retrieval artifacts reproducible from tracked sources
- keep orchestration control-plane files versioned with the repo
- keep per-run manifests, prompts, logs, and worker workspaces out of tracked AI memory
- support concurrent worker execution without shared-workspace writes

## Required Tracked Layout

- `AGENTS.md`: session workflow, hot-context contract, conditional cold-load rules
- `ai/AGENTS.md`: memory layout, freshness rules, orchestration ownership rules
- `ai/core/*.md`: durable repo truths and durable process notes
- `ai/state/current-state.md`: startup-critical current snapshot
- `ai/state/handoff.md`: startup checklist and validation guidance for the next session
- `ai/state/recent-validations.md`: warm ledger of exact validation commands and outcomes
- `ai/log/events.jsonl`: small recent discovery log
- `ai/log/archive/*`: compacted older discovery history
- `ai/indexes/*`: derived navigation and retrieval artifacts only
- `ai/orchestrator/README.md`: operating guide for local runs
- `ai/orchestrator/agents.json`: reusable worker definitions
- `ai/orchestrator/tasks/*.json`: tracked task-plan samples and reusable plans
- `scripts/refresh-ai-memory.{ps1,py}`: rebuild and check derived memory artifacts
- `scripts/query-ai-memory.{ps1,py}`: retrieve memory with tier and path facets
- `scripts/benchmark-ai-memory.{ps1,py}`: benchmark refresh and retrieval quality
- `scripts/claude-orchestrator.{ps1,py}`: validate, plan, and run local worker DAGs
- `.gitignore`: ignore the runtime root `/.claude-orchestrator/`

## Memory Model

- Source-of-truth order is code, then tests, then build configuration, then `/ai`.
- Hot context is exactly:
  - `ai/core/agent-invariants.md`
  - `ai/core/repo-purpose.md`
  - `ai/state/current-state.md`
  - `ai/state/handoff.md`
- Hot files must stay small and startup-focused; durable facts move to `ai/core/*`.
- Warm state keeps compact validation history in `ai/state/recent-validations.md`.
- Cold context is loaded on demand from `ai/core/*`, `ai/state/*`, `ai/log/*`, and `ai/indexes/*`.
- `ai/indexes/*.json` and optional `ai/indexes/cold-memory.db` are derived artifacts and must be regenerated, not hand-maintained.
- Coordinator/project-manager memory lives in `AGENTS.md`, `ai/AGENTS.md`, and the `ai/state/*` snapshot; worker tasks must not depend on that memory implicitly.
- After tracked AI memory changes, run:
  - `scripts/refresh-ai-memory.ps1`
  - `scripts/refresh-ai-memory.ps1 -Check`

## Orchestrator Model

- `ai/orchestrator/` is the tracked control plane.
- The default runtime root is repo-local `.claude-orchestrator/`.
- Runtime paths are:
  - `.claude-orchestrator/runs/<run-id>/`
  - `.claude-orchestrator/workspaces/<run-id>/<task-id>/`
- Worker task plans declare:
  - task id, title, agent, and prompt
  - optional top-level `runPolicy` with `runBudgetUsd`, `budgetBehavior`, per-task artifact byte limits (`maxTaskStdoutBytes`, `maxTaskStderrBytes`, `maxTaskResultBytes`), and `artifactBehavior`
  - optional dependencies
  - `sharedContext.readPaths` for cross-task read context
  - task-local `readPaths` for additional read context
  - task-local `writePaths` for allowed edits
  - optional `dependencyMaterialization` with default `summary-only` and opt-in `apply-reviewed`
  - constraints and validation hints
  - optional `contextMode` to keep worker prompts minimal by default
  - optional `modelProfile` to trade off speed, cost, and depth
  - workspace mode overrides when needed
- Planner guidance should prefer the smallest actor set that can finish the work; `analyst` and `reviewer` should be optional roles rather than default stages for every plan.
- For narrow code changes, the default topology should be one `implementer` task or an `implementer -> reviewer` path only when the extra review hop materially lowers risk.
- Supported workspace modes are:
  - `copy`: isolated sparse filesystem copy seeded only from declared `readPaths` and any existing file-backed `writePaths`; default
  - `worktree`: detached git worktree rooted at `HEAD`; requires a clean repo
  - `repo`: live repo root; explicit high-risk exception only
- Prompt context should default to `contextMode = minimal`:
  - include the shared summary
  - include task-local read context and write scope unless fuller shared context is explicitly required
- Task-local worker prompts should keep only coordinator- and workspace-specific rules in the prompt body; role-stable JSON/output-discipline rules should live in the selected agent definition so they are not duplicated inside every task prompt.
- Workers should treat the selected agent definition plus the task prompt and declared workspace as the full execution contract; if a repo file matters to the task, it should be declared explicitly in `readPaths` or `writePaths`.
- include task-local validation hints by default
- dependency outputs should act as the bounded coordinator handoff from prior tasks, including a compact summary and a few key notes when needed, plus explicit unknown markers when an upstream worker could not verify those sections, so downstream workers do not depend on reading prior task artifacts directly
- dependency handoff should remain `summary-only` by default; `dependencyMaterialization = apply-reviewed` should be opt-in for any downstream `copy` or `worktree` task — including reviewer tasks — that needs reviewed upstream code state materialized into its workspace before execution; use `apply-reviewed` on reviewer tasks when the upstream implementer creates new files that would otherwise be absent from the reviewer workspace
- reviewer-oriented dependency handoff should also include bounded changed-file summaries and diff previews from dependency workspaces so downstream review can inspect the proposed patch without reading prior task artifacts directly; diff previews alone are insufficient when the upstream task creates new files, which is the primary reason to add `dependencyMaterialization = apply-reviewed` to a reviewer task
- Minimal worker prompts should keep shared file lists out of the prompt body unless `contextMode = full`; shared summaries can stay visible without forcing every worker to reread the same file inventory.
- Live Claude invocations should pass only the selected agent definition instead of the full agent catalog when a single planner or worker role is being invoked.
- The orchestrator should expose section-level prompt accounting for planner and worker prompts so prompt growth is visible in dry-runs and manifests.
- Validate, dry-run, and manifest surfaces should expose resolved task models/profiles plus a compact list or count of any `complex` tasks so accidental `opus` usage is easy to spot.
- Validate, run, and manifest surfaces should also expose a compact topology summary: agent counts, read-only vs write-capable task counts, batch shape, dependency depth, and conservative warnings when the plan is obviously heavier than necessary.
- Agent/task definitions may declare `maxPromptEstimatedTokens` and/or `maxPromptChars`; oversized prompts should fail locally before live Claude execution.
- Copy-mode workspace hydration should copy only declared `readPaths` and any existing file-backed `writePaths`; missing or directory `readPaths` must fail validation explicitly, and oversized inputs should be surfaced instead of being skipped silently.
- If task-workspace validation is expected before promotion, any runtime-loaded config or fixture files needed by that validation should be declared in `readPaths` or `writePaths`; sparse copies should not be assumed to contain undeclared repo files.
- When `dependencyMaterialization = apply-reviewed`, the coordinator should replay reviewed dependency layers into the downstream `copy` or `worktree` workspace after base hydration, reject ambiguous overlaps across direct dependencies, and record which dependency layers were applied.
- The orchestrator should expose prompt-size estimates (`prompt_chars`, `prompt_estimated_tokens`) before live runs and capture actual Claude usage or cost fields when the CLI returns them.
- Optional run-level governance should be expressible in tracked plans via `runPolicy`; `budgetBehavior` / `artifactBehavior` should support `warn` and `stop`, and `stop` should block unscheduled later batches rather than trying to cancel already-running tasks.
- Run governance should cover aggregate spend plus per-task stdout, stderr, and result artifact size, and should surface the highest-cost tasks plus aggregate artifact totals for operator review.
- Prompt assembly should keep the most stable coordinator instructions and shared summary ahead of run-specific workspace paths or dependency detail so provider-side prefix caching can reuse more of each request.
- Worker execution-context text should prefer stable workspace labels over absolute filesystem paths, and the repeated worker-rules block should stay compact enough to avoid normal prompt truncation.
- Live planner, worker, and coordinator validation execution should emit progress lines on interactive `stderr` only, with phase-tagged status text, so operators can see in-flight work without contaminating machine-readable `stdout`.
- Worker prompts should keep structured output bounded: `summary` should stay short, `notes` / `followUps` should stay capped to a few high-signal items, and `validationIntents` should stay capped to a few high-signal suggestions.
- Worker result semantics should distinguish known-empty from unknown list fields: workers should emit `[]` when `filesTouched`, `validationIntents`, `followUps`, or `notes` are known-empty, and `null` only for `filesTouched`, `followUps`, or `notes` when those values are genuinely unknown or unverified.
- Live worker results should emit structured `validationIntents`; the initial supported intent kinds are `repo-script` and `tool`, and the coordinator should preserve them separately from legacy raw command strings found only in older manifests or review surfaces.
- Coordinator-side shell-free execution of `tool` intents should resolve PATH-backed wrappers before launch so Windows entrypoints like `mvn` can execute as `mvn.cmd` without reopening shell composition.
- Live worker prompts should require structured `validationIntents` only and should reject raw `validationCommands` during worker-result parsing.
- The coordinator may still expose a run-time worker validation mode override, but the live worker contract accepts only `intents-only`.
- Agent and task definitions may still carry `workerValidationMode = intents-only`; explicit CLI override still wins over task, then agent, then the default live mode.
- Live worker JSON schemas handed to Claude should require `validationIntents` and omit `validationCommands` so raw legacy command items fail at the schema boundary before coordinator parsing.
- Model selection should support both explicit `model` strings and profile-based routing:
  - `simple` -> `claude-haiku-4-5`
  - `balanced` -> `claude-sonnet-4-6`
  - `complex` -> `claude-opus-4-6` only as an explicit exception when cheaper models are likely insufficient

## Concurrency Contract

- The coordinator may run ready tasks concurrently up to `--max-parallel`.
- Parallel safety depends on isolated workspaces plus low-coupling task boundaries.
- Workers that need to edit the same files must not be scheduled in parallel.
- The coordinator should detect overlapping declared write scopes conservatively and serialize conflicting ready tasks.
- Copy-mode workspace creation must ignore `.claude-orchestrator/` so live runtime artifacts are not copied back into worker sandboxes.
- Each run id must be unique so overlapping orchestrator invocations do not collide on manifests or workspaces.
- The coordinator owns review, merge or cherry-pick decisions, memory updates, and final validation after worker runs.
- The coordinator should expose a review surface that summarizes workspace diffs, can export unified patches for copy/worktree runs, and can conservatively promote isolated workspace changes back into the repo.
- The coordinator should support bounded lifecycle helpers for resuming a retained run in place from its per-run selected-plan snapshot, retrying failed or blocked tasks into a new run, inventorying retained runs, pruning aged runtime state, and cleaning run-scoped artifacts, including detached worktrees.
- Same-run resume should default to unfinished or missing tasks, preserve already-completed task records, reuse the same run id and runtime directories, and allow explicit task narrowing.
- Same-run resume is run continuity rather than partial sandbox continuity; resumed `copy` or `worktree` tasks may rebuild fresh workspaces before rerun.
- Run inventory should expose compact status, resume-candidate, coordinator-validation, prompt, and cost summaries across the runtime root.
- Validate surfaces should expose tracked `runPolicy`, and run/retry/manifests plus retained-run summaries should expose run-governance status, alert counts, highest-cost tasks, and aggregate artifact totals.
- Retained-run summaries should also surface compact topology fields such as batch count, max parallel width, and topology warning count so inventory remains useful without opening each manifest.
- Age-based prune should support keeping the newest `N` runs and should skip incomplete runs by default unless the operator opts into pruning them.
- The coordinator should support a run-validation surface that consolidates worker-suggested validation commands or structured validation intents, defaults to completed-task suggestions unless the coordinator explicitly broadens the policy, rejects shell-composed or unknown-entrypoint commands by default unless the coordinator explicitly overrides that policy, can execute accepted suggestions from repo root or from the suggesting task workspace when the operator requests that scope, may expose an intent-only mode that rejects legacy raw `validationCommands`, and records actual coordinator validation outcomes separately in the run manifest.
- When validation runs against task workspaces, dedupe should happen by command plus execution workspace rather than command text alone so the same suggestion can run independently against multiple worker sandboxes before promotion.
- Retry flows should preserve any explicit source-run worker-validation override when present; otherwise they should resolve the effective mode again from tracked task/agent settings, and they should not replay old manifest-level `compat` fallbacks into live workers.

## Worker Protection Rules

- Workers must not edit `TODO.md`.
- Workers must not edit `ai/state/*`, `ai/log/*`, or `ai/indexes/*`.
- Workers may edit `ai/orchestrator/**` only when that is the assigned task.
- Planner output should prefer `copy` workspaces plus concrete `readPaths` and conservative `writePaths`.
- The coordinator should compare worker-reported touched files with actual workspace diffs and fail task records that touch protected paths or fall outside declared `writePaths`.
- `dependencyMaterialization = apply-reviewed` should be rejected for `workspaceMode = repo` and should require direct dependencies whose effective workspace mode is `copy` or `worktree`.
- The coordinator should normalize worker JSON after parsing: reject invalid statuses or malformed arrays/null usage, compact oversized summaries, cap `notes`, `followUps`, and `validationIntents` to a few high-signal items, preserve explicit unknown list fields in task records before writing manifests, and normalize structured `validationIntents` into a safe manifest form.
- Worker validation suggestions should use structured `validationIntents` for direct repo-local script invocations or approved tool commands in all live worker paths; legacy raw `validationCommands` survive only in old manifests or review-time interpretation, and shell composition should be treated as low-quality and rejected by default at coordinator validation time.
- Worker prompts should explicitly tell workers to mirror approved validation hints exactly, avoid swapping entrypoints like `mvn` and `mvnw`, use `repo-script` only for `scripts/...` or `mvnw(.cmd)`, use `tool` only for approved executables, and emit `[]` instead of inventing pseudo scripts, `grep`, or shell fragments.
- When a legacy raw `validationCommand` already matches a safe direct tool or repo-script shape, the coordinator should preserve the original command text for review while also normalizing it into an argv-safe execution form so accepted legacy commands do not require shell execution.
- Validation surfaces should expose each task's effective worker validation mode plus its source (`override`, `task`, `agent`, or `default`) so authoring and runtime policy are inspectable.
- Run manifests should record the summarized effective worker validation mode, any explicit override, per-task modes, and per-task sources so runtime enforcement choices are visible during review and retry.
- Task records, review output, promotion summaries, and retry manifests should surface the effective dependency-materialization mode plus the applied dependency layers so chained workspace state is inspectable.
- Coordinator-side promotion should refuse `workspaceMode="repo"` task changes, protected-path violations, duplicate changed-file ownership across selected tasks, and path traversal outside the repo root.
- Coordinator-side retry may reuse completed dependency records from the prior run manifest, but incomplete dependencies must be rerun rather than assumed.
- Legacy manifest `validationCommands` remain suggestions for review-time validation; coordinator-run validation results should be persisted separately so manifests distinguish suggested validation from actually executed validation.

## Bootstrap Procedure For Another Repo

1. Create `AGENTS.md` and `ai/AGENTS.md` with startup, freshness, and ownership rules.
2. Create the hot context files under `ai/core/` and `ai/state/`.
3. Add warm state and event-log files under `ai/state/` and `ai/log/`.
4. Add refresh, query, and benchmark scripts that understand the target repo's paths and document taxonomy.
5. Add the orchestrator guide, agent catalog, and at least one lean single-task sample plus one parallel-ready task-plan example under `ai/orchestrator/`; keep heavier review or dependency-chain samples only when they demonstrate a distinct contract.
6. Set the orchestrator default runtime root to repo-local `.claude-orchestrator/`.
7. Ignore `/.claude-orchestrator/` in Git.
8. Encode repo-specific validation commands in the task plans and memory state.
9. Regenerate derived AI indexes after the first bootstrap pass.

## Minimum Validation Checklist

- `python -m py_compile scripts/claude-orchestrator.py scripts/refresh-ai-memory.py scripts/query-ai-memory.py`
- `scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json`
- `scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json`
- `scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2`
- `scripts/refresh-ai-memory.ps1`
- `scripts/refresh-ai-memory.ps1 -Check`

## Repo-Specific Inputs To Customize

- `ai/core/repo-purpose.md`
- `ai/core/agent-invariants.md`
- the doc and file classification rules inside the refresh or query scripts
- validation commands for the repo's build, tests, docs, and release flow
- the worker agent catalog in `ai/orchestrator/agents.json`
- sample task plans under `ai/orchestrator/tasks/`
