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
  - optional dependencies
  - concrete file hints
  - constraints and validation hints
  - workspace mode overrides when needed
- Supported workspace modes are:
  - `copy`: isolated filesystem copy of the current working tree; default
  - `worktree`: detached git worktree rooted at `HEAD`; requires a clean repo
  - `repo`: live repo root; explicit high-risk exception only

## Concurrency Contract

- The coordinator may run ready tasks concurrently up to `--max-parallel`.
- Parallel safety depends on isolated workspaces plus low-coupling task boundaries.
- Workers that need to edit the same files must not be scheduled in parallel.
- Copy-mode workspace creation must ignore `.claude-orchestrator/` so live runtime artifacts are not copied back into worker sandboxes.
- Each run id must be unique so overlapping orchestrator invocations do not collide on manifests or workspaces.
- The coordinator owns review, merge or cherry-pick decisions, memory updates, and final validation after worker runs.

## Worker Protection Rules

- Workers must not edit `TODO.md`.
- Workers must not edit `ai/state/*`, `ai/log/*`, or `ai/indexes/*`.
- Workers may edit `ai/orchestrator/**` only when that is the assigned task.
- Planner output should prefer `copy` workspaces and concrete file scopes.

## Bootstrap Procedure For Another Repo

1. Create `AGENTS.md` and `ai/AGENTS.md` with startup, freshness, and ownership rules.
2. Create the hot context files under `ai/core/` and `ai/state/`.
3. Add warm state and event-log files under `ai/state/` and `ai/log/`.
4. Add refresh, query, and benchmark scripts that understand the target repo's paths and document taxonomy.
5. Add the orchestrator guide, agent catalog, and at least one sequential and one parallel-ready task-plan example under `ai/orchestrator/`.
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
