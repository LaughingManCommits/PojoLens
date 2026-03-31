# Claude Orchestrator

This directory is the tracked control plane for local multi-Claude runs.

Tracked files:
- `agents.json`: reusable worker definitions for planner, analyst, implementer, and reviewer roles
- `tasks/*.json`: task-plan files the coordinator can validate or execute

Runtime artifacts are intentionally kept outside `ai/` under a sibling runtime root:
- default runtime root: `../.claude-orchestrator/<repo-name>/`
- run manifests: `../.claude-orchestrator/<repo-name>/runs/<run-id>/`
- isolated workspaces: `../.claude-orchestrator/<repo-name>/workspaces/<run-id>/`

Why this split:
- tracked specs stay versioned with the repo
- transient worker output does not pollute AI memory indexes
- workers can run in isolated `copy` or `worktree` workspaces

Primary commands:

```powershell
scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json
scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-review.json --dry-run
scripts/claude-orchestrator.ps1 plan "Investigate scatter allocation follow-up" --dry-run
```

Dry runs:
- `plan --dry-run` prints the planner request and target output path without invoking Claude
- `run --dry-run` writes the run manifest, task prompts, and worker command files without invoking Claude or creating repo copies/worktrees

Workspace modes:
- `copy`: isolated filesystem copy of the current repo state; safe default
- `worktree`: detached git worktree rooted at `HEAD`; requires a clean repo
- `repo`: live repo root; high-risk and opt-in only

Coordinator rules:
- workers must not update `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`
- the coordinator owns memory updates, final summaries, and merge decisions
- prefer `copy` mode unless a task clearly needs git metadata
- review worker outputs before applying or cherry-picking edits back into the main repo
