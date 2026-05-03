# Claude Orchestrator

```
╔═════════════════════════════════════════════════════════════════════╗
║ ██████╗  ██████╗      ██╗ ██████╗ ██╗     ███████╗███╗  ██╗███████╗ ║
║ ██╔══██╗██╔═══██╗     ██║██╔═══██╗██║     ██╔════╝████╗ ██║██╔════╝ ║
║ ██████╔╝██║   ██║     ██║██║   ██║██║     █████╗  ██╔██╗██║███████╗ ║
║ ██╔═══╝ ██║   ██║██   ██║██║   ██║██║     ██╔══╝  ██║╚████║╚════██║ ║
║ ██║     ╚██████╔╝╚█████╔╝╚██████╔╝███████╗███████╗██║ ╚███║███████║ ║
║ ╚═╝      ╚═════╝  ╚════╝  ╚═════╝ ╚══════╝╚══════╝╚═╝  ╚══╝╚══════╝ ║
║                                                                      ║
║          CLAUDE ORCHESTRATOR  ·  multi-agent coding runs            ║
╚═════════════════════════════════════════════════════════════════════╝
```

Coordinate a team of specialized Claude workers on your codebase in a
single command.  Each worker runs in an isolated sandbox, produces a
diff, and nothing lands in your repo until **you** call `promote`.

---

## How it works

```
  YOU                  COORDINATOR              WORKERS (Claude)
  ─────────────────────────────────────────────────────────────────
  $ wizard          ─► clarifies goal
                       generates plan
                       validates + estimates
                                            ──► [analyst    ]  batch 1
                                            ──► [implementer]  batch 2
                                            ──► [reviewer   ]  batch 3
                       collects results  ◄───
  $ review          ─► shows per-task diffs & findings
  $ validate-run    ─► runs worker-suggested tests
  $ promote         ─► applies diffs back into repo
  $ cleanup / prune ─► removes sandbox artifacts
```

Everything between `run` and `promote` stays in `.claude-orchestrator/`
and never touches the live repo.

---

## Setup

**Directory matters for install — not for running.**

`pip install -e .` reads `pyproject.toml` from the current directory,
so you must run it from the **repo root** (where that file lives).
After that, `pojolens-agents` resolves the repo root automatically from
its installed package location — you can run commands from any
subdirectory inside the repo.

```powershell
# 1. cd to repo root first (the folder that contains pyproject.toml)
cd C:\data\pojolens

# 2. Install once
py -3 -m pip install -e .
```

**If `pojolens-agents` is not found after install**, Python's Scripts
directory is not on your PATH.  Add it permanently (restart terminal after):

```powershell
[Environment]::SetEnvironmentVariable(
    "PATH",
    [Environment]::GetEnvironmentVariable("PATH","User") + ";" +
    (py -3 -c "import sysconfig; print(sysconfig.get_path('scripts'))"),
    "User"
)
```

Or skip PATH entirely and use the PowerShell shim, which always works
from the repo root:

```powershell
scripts/ai/claude-orchestrator.ps1 wizard
```

Both the `pojolens-agents` CLI and the `.ps1` shim are equivalent.
The shim is the fallback when PATH is not set up.

Optional extras:

```powershell
py -3 -m pip install -e ".[tui]"            # Textual live dashboard
py -3 -m pip install -e ".[notifications]"  # desktop alerts via plyer
py -3 -m pip install -e ".[otel]"           # OTEL trace export
```

---

## Your first run — the wizard

The wizard is the recommended entry point for every new task.  It
asks what you want, generates a plan, shows a cost estimate, and waits
for your explicit approval before invoking any workers.

```powershell
pojolens-agents wizard
```

What happens internally:

```
  Step 1  You describe your goal (or pass --goal "…")
          ↓
  Step 2  Planner asks up to 3 haiku-powered clarifying questions
          ↓
  Step 3  Planner generates or matches a task plan
          ↓
  Step 4  Coordinator validates topology and estimates cost:
          ┌──────────────────────────────────────────────────────┐
          │  Plan: add-pagination  │  2 tasks  │  est. ~$0.03   │
          │  batch 1: implementer  (sonnet, copy workspace)      │
          │  batch 2: reviewer     (haiku,  copy workspace)      │
          └──────────────────────────────────────────────────────┘
          ↓
  Step 5  [accept]  run → review → promote → validate flow
          [revise]  reset goal, re-clarify, re-validate (up to 3×)
          [stop]    exit cleanly, no side-effects
```

Try a completely free dry run first — no Claude workers are invoked,
no API tokens spent:

```powershell
pojolens-agents wizard --dry-run --json
```

Pass a natural-language goal directly:

```powershell
pojolens-agents wizard --goal "add rate limiting to the /items endpoint"
```

---

## Core concepts

### Agents and roles

```
  Role              Model profile  Purpose
  ───────────────── ─────────────  ─────────────────────────────────────
  planner           simple         decomposes goals into task plans
  analyst           simple         read-only investigation, no writes
  implementer       balanced       writes or edits code
  reviewer          balanced       reviews diffs, approves changes
  docs-implementer  simple         docs-only write tasks (lean output)
  docs-reviewer     simple         docs-only review tasks (lean output)
```

Definitions live in `ai/orchestrator/agents.json`.  Each has a
`agents/<role>/prompt.md` file.  You rarely need to change these.

### Task plans

A task plan is a JSON file that tells the coordinator what to do:

```json
{
  "version": 1,
  "name":    "add-pagination",
  "goal":    "Add cursor pagination to the /items endpoint",
  "sharedContext": {
    "summary":   "Spring Boot REST API, single Maven module",
    "readPaths": ["src/main/java/com/example/ItemsController.java"]
  },
  "tasks": [
    {
      "id":          "implement",
      "agent":       "implementer",
      "description": "Add page/size params and Page<Item> return type",
      "readPaths":   ["src/main/java/com/example/ItemsController.java"],
      "writePaths":  ["src/main/java/com/example/ItemsController.java",
                      "src/test/java/com/example/ItemsControllerTest.java"]
    },
    {
      "id":        "review",
      "agent":     "reviewer",
      "dependsOn": ["implement"]
    }
  ]
}
```

Tasks with no `dependsOn` overlap run **in parallel** (up to
`--max-parallel`).  Tasks with `dependsOn` form a DAG and execute in
topological batches.

Start from the tracked samples in `ai/orchestrator/tasks/` and modify.

### Workspace isolation

Workers do not touch your live repo.  Each runs in a sandboxed
workspace whose mode is set per task:

```
  ┌─────────────┬──────────────────────────────────────┬────────┐
  │ Mode        │ What the worker sees                  │ Risk   │
  ├─────────────┼──────────────────────────────────────┼────────┤
  │ copy        │ Sparse copy — only declared readPaths │ Safe ✓ │
  │             │ and existing writePaths files         │        │
  ├─────────────┼──────────────────────────────────────┼────────┤
  │ worktree    │ Full detached git worktree at HEAD    │ Medium │
  ├─────────────┼──────────────────────────────────────┼────────┤
  │ repo        │ Live repo root (high-risk, opt-in)    │ High ⚠ │
  └─────────────┴──────────────────────────────────────┴────────┘
```

Default is `copy`.  Use `worktree` only when the task needs git
metadata.  Avoid `repo` unless you have a specific reason.

### Where things live

```
  repo/                              .claude-orchestrator/  (gitignored)
  ├── ai/orchestrator/               ├── generated-plans/
  │   ├── agents.json                └── runs/
  │   ├── tasks/my-plan.json ──────►     └── <run-id>/
  │   └── skills/registry.json              ├── manifest.json
  └── src/  (read-only to workers)           ├── selected-plan.json
                                             ├── shared-context.jsonl
                                             └── workspaces/
                                                 ├── implement/ (copy)
                                                 └── review/    (copy)
```

Tracked specs stay versioned with the repo.  Transient worker output
lives under `.claude-orchestrator/` and never pollutes AI memory indexes.

---

## Operator lifecycle

```
  ┌─────────┐    ┌─────────┐    ┌──────────┐    ┌──────────┐
  │  PLAN   │──► │   RUN   │──► │ INSPECT  │──► │ PROMOTE  │
  └─────────┘    └─────────┘    └──────────┘    └──────────┘
  validate        run             status          review
  wizard          resume          inventory       diff-run
  estimate        retry           evaluate-run    validate-run
                                  export-trace    promote
                                                  ──► cleanup / prune
```

---

## Phase 1 — Plan

Validate a task plan before spending any tokens:

```powershell
# Topology check: agents, batches, cost estimate, warnings
pojolens-agents validate ai/orchestrator/tasks/example-parallel.json --json

# Cost/token/wall-clock estimate without creating a run
pojolens-agents run ai/orchestrator/tasks/example-parallel.json --estimate --json
```

`validate --json` also reports:
- resolved `effort` and model per task and source (`task` / `agent` / `default`)
- `topology` — read-only vs write-capable task counts, batch shape, parallel width
- `runPolicy` thresholds if declared
- `agentExtraTools` and per-task `extraTools` for custom tool wiring

---

## Phase 2 — Run

```powershell
# Dry run: writes manifest and prompts, no Claude calls, free
pojolens-agents run ai/orchestrator/tasks/example-parallel.json \
    --dry-run --max-parallel 2 --json

# Live run with TUI dashboard (needs pip install pojolens-agents[tui])
pojolens-agents run ai/orchestrator/tasks/example-parallel.json \
    --max-parallel 2 --tui

# Live run, machine-readable output
pojolens-agents run ai/orchestrator/tasks/example-parallel.json \
    --max-parallel 2 --json
```

Useful run flags:

| Flag | What it does |
|------|-------------|
| `--dry-run` | Manifest + prompts only — zero Claude calls |
| `--estimate` | Pre-flight cost/token estimates, no manifest |
| `--tui` | Live Textual dashboard; auto-enables on interactive stderr |
| `--watch` | Line-by-line progress on stderr (no Textual required) |
| `--json` | Machine-readable JSON on stdout |
| `--max-parallel N` | Concurrent task limit per batch |
| `--effort <level>` | Override planner/worker effort (`low`/`medium`/`high`) |
| `--worker-validation-mode` | Override validation mode for this run |
| `--follow-up-mode inject` | Promote worker `followUpTasks` into real tasks |

### Resume and retry

```
  resume  ──  same run-id, same workspaces, continues unfinished tasks
  retry   ──  new run-id, seeds completed deps from prior run
```

```powershell
# Continue an interrupted run from where it stopped
pojolens-agents resume .claude-orchestrator/runs/<run-id> --json

# Retry only the failed tasks; completed tasks seed forward
pojolens-agents retry .claude-orchestrator/runs/<run-id> \
    --task <failed-task-id> --json
```

---

## Phase 3 — Inspect

```powershell
# Summary of one run: status, costs, governance, promotion-readiness
pojolens-agents status .claude-orchestrator/runs/<run-id> --json

# All retained runs at a glance
pojolens-agents inventory --json

# Quality score: topology, validation quality, retry/resume consistency
pojolens-agents evaluate-run .claude-orchestrator/runs/<run-id> --json

# Aggregate scores across all retained runs
pojolens-agents evaluate-corpus --json

# Per-task diffs, scope violations, dependency materialization, findings
pojolens-agents review .claude-orchestrator/runs/<run-id> --json

# Literal file delta before promotion
pojolens-agents diff-run .claude-orchestrator/runs/<run-id> --stat
pojolens-agents diff-run .claude-orchestrator/runs/<run-id>        # full diff

# Export patch file for external review tooling
pojolens-agents export-patch .claude-orchestrator/runs/<run-id> \
    --out .claude-orchestrator/runs/<run-id>/review/combined.patch

# Export OTEL trace spans from retained run
pojolens-agents export-trace .claude-orchestrator/runs/<run-id> --json
```

`status`, `inventory`, and retained manifests expose:

| Field | Contents |
|-------|----------|
| `lifecycleState` | Current state: `running`, `completed`, `budget_exceeded`, … |
| `lifecycleStateReason` | Why the state was set |
| `approvalSummary` | Review / validation / promotion gate status |
| `usageTotals` | Aggregated input, output, cache, cost |
| `costEstimate` | Per-task and per-batch USD/token ranges |
| `traceSummary` | Compact event and branch lineage rollup |
| `runGovernance` | Budget alerts, artifact size totals |
| `taskModels` | Resolved model per task (spot accidental opus usage) |

---

## Phase 4 — Validate

Workers emit structured `validationIntents` (not raw shell commands).
`validate-run` executes them:

```powershell
# Preview what would run, no execution
pojolens-agents validate-run .claude-orchestrator/runs/<run-id> --dry-run --json

# Execute accepted validation suggestions
pojolens-agents validate-run .claude-orchestrator/runs/<run-id> --json

# Run from inside each task workspace (before promotion)
pojolens-agents validate-run .claude-orchestrator/runs/<run-id> \
    --execution-scope task-workspace --json

# Reject legacy raw validationCommands; accept only structured intents
pojolens-agents validate-run .claude-orchestrator/runs/<run-id> \
    --intents-only --dry-run --json
```

When retained completed tasks touch only docs-like files and no docs
validation was suggested, `validate-run` automatically adds a
`scripts/docs/check-doc-consistency.ps1` gate.

---

## Phase 5 — Promote

`promote` applies reviewed worker diffs back into the live repo:

```powershell
# Always dry-run first to confirm nothing is blocked
pojolens-agents promote .claude-orchestrator/runs/<run-id> --dry-run --json

# Apply reviewed changes
pojolens-agents promote .claude-orchestrator/runs/<run-id> --json

# Confirm everything works after promotion
pojolens-agents validate-run .claude-orchestrator/runs/<run-id> --json
```

`promote` **refuses** if any of these hold:
- Worker touched a protected path (`TODO.md`, `ai/state/*`, etc.)
- Conflicting changed-file ownership across selected tasks
- Path traversal outside repo root
- Duplicate operations with differing workspace content

Exact duplicate operations with matching content are deduped
automatically so reviewer materialization does not require `--task`
workarounds.

---

## Phase 6 — Cleanup

```powershell
# Remove one run's artifacts and detached worktrees
pojolens-agents cleanup .claude-orchestrator/runs/<run-id> --json

# Prune aged runs (keeps newest N, skips incomplete by default)
pojolens-agents prune --older-than-days 14 --keep 5 --dry-run --json
pojolens-agents prune --older-than-days 14 --keep 5 --json

# Prune also evicts old wizard-generated plans (30 days / 20 most recent)
```

---

## Dry run reference

| Command | Claude called? | Manifest written? | Cost |
|---------|:-------------:|:-----------------:|:----:|
| `validate` | No | No | Free |
| `run --estimate` | No | No | Free |
| `run --dry-run` | No | Yes (+ prompts) | Free |
| `wizard --dry-run` | Planner only | Yes | ~1¢ |
| `run` (live) | Yes | Yes | API cost |

Dry-run manifests include `promptSections`, `promptBudget`, and
`costEstimate` so you can check prompt size and cost before committing.

---

## Human-in-the-loop (HITL) gates

Pause the run before a batch and wait for your approval:

```powershell
# Pause before every batch
pojolens-agents run my-plan.json --hitl --hitl-mode always --json

# Auto-approve gates (useful for testing the mechanism)
pojolens-agents run my-plan.json --hitl --hitl-auto-approve --json
```

| Mode | When the gate fires |
|------|---------------------|
| `none` | Never (default) |
| `batch` | Before every batch with pending tasks |
| `on-failure` | Only when the completed batch had failures |
| `always` | Before **every** batch including the first |

The gate writes a manifest snapshot, then waits.  You can also write a
`hitl-gate.lock` sentinel file in the run directory to approve or abort
without interactive input — gateId validation prevents stale sentinels
from prior gates being accidentally accepted.

Set `hitl: true` and `hitlMode` in `runPolicy` to make the gate part
of a tracked plan instead of a one-off CLI flag.

---

## Follow-up task injection

Workers may emit structured `followUpTasks` in their JSON result.  By
default they are inert.  Opt in at the plan level or per run:

```json
"runPolicy": { "followUpBehavior": "inject" }
```

```powershell
pojolens-agents run my-plan.json --follow-up-mode inject
```

Injected tasks appear as new pending work between batches with a
`dependsOn` edge back to the emitter.  Use `conditionField` /
`conditionValue` to gate injection on a field of the emitter's run
record (case-insensitive substring match); unmatched tasks emit a
`task-injection-skipped` event instead.

---

## Shared context scratchpad

Workers may call the built-in tool `write_shared_context(note, tags?)`
to leave notes for later workers in the same run.

```
  task-1 writes → shared-context.jsonl (run-local)
                          ↓
  task-2 prompt  ← bounded tail injected as "Shared context notes"
```

Set `sharedContextTags` on a task to filter which notes it receives.
`sharedContextPath` in the run manifest points to the file.

---

## Rate limiting

Prevent 429 errors when your API tier has hard caps:

```powershell
pojolens-agents run my-plan.json --tpm-limit 50000 --rpm-limit 100
```

Or set `ANTHROPIC_TPM_LIMIT` / `ANTHROPIC_RPM_LIMIT` environment
variables.

How the limiter works:

```
  Before dispatch:  pre-deduct estimated tokens from the window
  During task:      task runs; overrun is absorbed within the batch
  After task:       charge only max(0, actual − estimated) as correction
  Later batches:    throttled by the corrected window state
  After injection:  recomputes budgets for newly injected tasks
```

The window budget is advisory for individual tasks (a task cannot be
cancelled mid-flight) but enforced across later batches.  Only set
`--tpm-limit` when your API tier actually enforces a hard ceiling.

---

## OTEL tracing (optional)

Export run traces to any OTLP HTTP collector:

```powershell
# During a live run
pojolens-agents run my-plan.json \
    --otel-endpoint http://localhost:4318/v1/traces

# Export from a retained run after the fact
pojolens-agents export-trace .claude-orchestrator/runs/<run-id> \
    --otel-endpoint http://localhost:4318/v1/traces
```

Or set `OTEL_EXPORTER_OTLP_ENDPOINT` and the coordinator picks it up
automatically.

Span kinds: `orchestrator.run` → `orchestrator.batch` → `orchestrator.task`
/ `orchestrator.validation` / `orchestrator.approval`.  Additional
lineage parents are exported as OTEL span links.

---

## Token and cost visibility

```powershell
# Before the run — topology + cost estimate
pojolens-agents validate my-plan.json --json

# Before the run — detailed USD/token/wall-clock ranges
pojolens-agents run my-plan.json --estimate --json

# After the run — per-task usage in the manifest
pojolens-agents status .claude-orchestrator/runs/<run-id> --json
```

Cap aggregate run spend with `runPolicy`:

```json
"runPolicy": {
  "runBudgetUsd":    0.50,
  "budgetBehavior":  "stop",
  "artifactBehavior":"warn",
  "maxTaskResultBytes": 131072
}
```

When the budget cap fires:
- a `budget-exceeded` event is emitted with `{actualCostUsd, limitCostUsd, remainingTaskIds}`
- `budgetExceeded: true` in the run payload
- `lifecycleState` becomes `budget_exceeded`
- process exits with code `8`

`validate --json` warns when `runBudgetUsd` is already below the
pre-flight minimum estimate so under-budget plans are visible before
the first task starts.

---

## Notifications

```powershell
# Fire a desktop notification when the run finishes
pojolens-agents run my-plan.json --notify

# Suppress config-file notifications for one run
pojolens-agents run my-plan.json --no-notify
```

Configure channels in `pojolens-agents.toml`:

```toml
[notifications]
desktop           = true
webhook_url       = "https://hooks.example.com/abc"
slack_webhook_url = "https://hooks.slack.com/..."
notify_on         = ["always"]    # or ["success"] / ["failure"]
```

Notifications fire in a background thread and are suppressed for
`--dry-run` and `--estimate` runs.

---

## Writing a task plan

Minimum viable plan (copy, extend, rename):

```json
{
  "version": 1,
  "name":    "my-plan",
  "goal":    "One sentence describing the overall goal",
  "sharedContext": {
    "summary":   "One paragraph about the repo and feature area",
    "readPaths": ["path/to/relevant/File.java"]
  },
  "tasks": [
    {
      "id":          "do-thing",
      "agent":       "implementer",
      "description": "What this specific worker should do",
      "readPaths":   ["src/main/java/..."],
      "writePaths":  ["src/main/java/...", "src/test/..."]
    }
  ]
}
```

Common optional fields per task:

| Field | Default | Purpose |
|-------|---------|---------|
| `workspaceMode` | `copy` | `copy` / `worktree` / `repo` |
| `dependsOn` | `[]` | Task ids this task must wait for |
| `modelProfile` | agent default | `simple` / `balanced` / `complex` |
| `effort` | agent default | `low` / `medium` / `high` |
| `outputProfile` | `standard` | `lean` for cheap read-only work |
| `skills` | agent defaults | Extra skill names from registry |
| `contextMode` | `minimal` | `full` to include all shared read paths |
| `sharedContextTags` | `[]` | Filter shared-context scratchpad by tag |
| `extraTools` | agent defaults | Custom shell/script tools for this task |
| `sharedContextTags` | `[]` | Shared-context note filter by tag |
| `conditionField` / `conditionValue` | none | Injection predicate on emitter record |

Top-level `runPolicy` fields:

| Field | Purpose |
|-------|---------|
| `runBudgetUsd` | Cap total cost across completed tasks |
| `budgetBehavior` | `warn` or `stop` when cap fires |
| `artifactBehavior` | `warn` or `stop` on oversized task output |
| `followUpBehavior` | `ignore` (default) or `inject` |
| `hitl` / `hitlMode` | Enable HITL gates in the tracked plan |

---

## Context discipline

- Coordinator memory (`AGENTS.md`, `ai/state/*`) is not visible to workers
  unless explicitly declared in `readPaths`
- Worker prompts default to `contextMode = minimal`: shared summary,
  task read context, write scope, dependency outputs, task validation hints
- Add `contextMode = "full"` only when a task genuinely needs all shared
  read paths in the prompt body
- Dependency outputs carry a bounded upstream handoff: summary + key notes
  + reviewer-only diff previews; no raw artifact files by default
- `dependencyMaterialization = "apply-reviewed"` is opt-in per task and
  replays reviewed upstream file state into the downstream workspace;
  rejected for `workspaceMode = "repo"`; requires direct copy/worktree deps
- Set `maxPromptEstimatedTokens` or `maxPromptChars` to fail oversized
  prompts locally before invoking Claude

---

## Model selection

| Profile | Model | Use when |
|---------|-------|----------|
| `simple` | `claude-haiku-4-5-20251001` | Read-only, docs, cheap proofs |
| `balanced` | `claude-sonnet-4-6` | Most coding tasks (default) |
| `complex` | `claude-opus-4-7` | Exceptional cases requiring deep reasoning |

Set `modelProfile` on the agent or the task.  Use `model` for a direct
override.  Tracked samples use `simple` for read-only analyst and
docs-oriented work.

---

## Tracked samples

| Plan file | What it demonstrates |
|-----------|----------------------|
| `example-parallel.json` | Two concurrent analyst tasks, no reviewer |
| `example-review.json` | Single reviewer, direct contract review |
| `example-implement-review-quickstart.json` | Minimal implementer→reviewer coding path |
| `example-materialized-chain.json` | Sequential chain with `apply-reviewed` materialization |
| `example-trace-multibatch.json` | Two-batch fixture for trace and lineage testing |
| `example-cheap-proof-docs.json` | Lean docs profile — cheapest valid proof |
| `wp16-live-run-policy-proof.json` | `runPolicy` budget cap with between-batch stop |
| `wp17-csv-typed-loader-slice.json` | Practical write-capable CSV starter slice |

Start with `example-parallel.json` (dry-run, free) to confirm the
install works before running anything live.

---

## Console mode

An interactive REPL with all subcommands available inline:

```powershell
pojolens-agents console           # Textual TUI (auto when textual installed)
pojolens-agents console --no-tui  # Plain readline REPL
```

Type `/exit` to quit.  History and tab-completion are available in the
plain REPL.

---

## Run lifecycle exit codes

| Code | Meaning |
|------|---------|
| `0` | All tasks completed |
| `5` | At least one task was blocked |
| `6` | At least one task failed |
| `7` | Unsafe promotion attempted |
| `8` | Run budget exceeded |
| `9` | Unexpected crash |

---

## Summarize run ledger

```powershell
# Human-readable summary of all recorded runs
pojolens-agents summarize-ledger

# Filtered
pojolens-agents summarize-ledger --plan-name add-pagination --since 2026-04-01 --json
```

---

## Coordinator rules (summary)

- Workers **must not** edit `TODO.md`, `ai/state/*`, `ai/log/*`, or `ai/indexes/*`
- Nothing reaches the live repo until `promote` is explicitly called
- Protected-path violations fail the task record immediately; out-of-scope edits
  are flagged in `protected_path_violations` and `write_scope_violations`
- Retry preserves an explicit source-run `workerValidationModeOverride`; older
  manifest-level `compat` fallbacks are not replayed into live workers
- Worker JSON is normalised coordinator-side: summaries compacted, lists capped,
  malformed status rejected, structured intents normalised
- Runtime mutation is bounded to future batches; completed task records are never
  rewritten by later injection

Full contract details are in `SYSTEM-SPEC.md`.

---

## File layout reference

```
ai/orchestrator/
├── README.md              ← this file (operating guide)
├── SYSTEM-SPEC.md         ← portable orchestration contract
├── agents.json            ← role definitions
├── agents/<role>/
│   └── prompt.md          ← file-backed role prompt body
├── model-pricing.json     ← Anthropic model prices (update without code changes)
├── skills/
│   ├── registry.json      ← tracked skill registry
│   └── <skill>/
│       └── SKILL.md       ← skill prompt body (≤4 KB)
└── tasks/
    └── *.json             ← tracked task plans

scripts/ai/
├── pojo_lens_agents/      ← installable CLI package source
└── claude-orchestrator.ps1← legacy shim (still works)

.claude-orchestrator/      ← runtime root (gitignored)
├── generated-plans/       ← wizard-generated ephemeral plans
└── runs/
    └── <run-id>/
        ├── manifest.json          ← full run record
        ├── selected-plan.json     ← snapshot of executed plan
        ├── shared-context.jsonl   ← worker scratchpad
        ├── hitl-gate.lock         ← optional HITL sentinel
        └── workspaces/
            └── <task-id>/         ← sparse copy or worktree
```

---

## Scope

- `ai/orchestrator/*` is control-plane memory for the local multi-agent system
- `ai/core/*`, `ai/state/*`, and `ai/log/*` are project memory — repo facts,
  active state, validation history, and handoff
- do not use this directory as a duplicate roadmap or session-state store;
  link back to project memory when the operator contract needs repo context
