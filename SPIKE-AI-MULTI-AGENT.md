# SPIKE: Local AI Multi-Agent Orchestration

## Question

What still blocks the repo-local Claude orchestrator from being a dependable
workflow for real multi-step code changes, not just isolated analysis,
single-implementer patches, and review flows?

## Thesis

The orchestrator is no longer an MVP in the "can it run at all?" sense.

That part is already proven:

- tracked control-plane files live under `ai/orchestrator/`
- the coordinator supports `validate`, `plan`, `run`, `retry`, `review`,
  `export-patch`, `promote`, `cleanup`, and `validate-run`
- live worker output is structured-intent-only
- reviewer dependency handoff includes changed-file and diff previews
- `validate-run` can execute against repo root or the suggesting task
  workspace
- a non-trivial live proof already exercised implementer, reviewer, export,
  validation, and promotion paths

The remaining problems are more specific.

The coordinator is already credible for:

- bounded analysis or review plans
- isolated implementer tasks whose changes can be promoted directly
- pre-promotion validation of a single worker sandbox

It is still weak for:

- chained implementation DAGs where one implementer must build on another
- strict task-scope enforcement
- operator lifecycle beyond "retry in a new run" and "cleanup this run"
- run-level cost governance

## Verified Baseline

As of `2026-04-07`, the following are real in code and docs:

- task plans use explicit `sharedContext.readPaths`, task `readPaths`, and task
  `writePaths` instead of overloading task `files`
- sparse `copy` workspaces seed `AGENTS.md`, `ai/AGENTS.md`, declared
  `readPaths`, and any existing file-backed `writePaths`
- copy-mode validation surfaces missing or directory `readPaths` and oversized
  hydration inputs instead of skipping them silently
- overlapping write-capable tasks are serialized conservatively by declared
  write scope
- worker-reported touched files are audited against actual workspace diffs
- protected-path and out-of-scope edits fail task records and block promotion
- reviewer tasks see dependency diff previews instead of only one-line
  summaries
- promotion is conservative and blocks ambiguous file ownership
- retry reuses completed dependencies from an earlier manifest
- cleanup removes run artifacts and detached worktrees
- recent live proof showed the coordinator can promote a real bounded patch

So the spike should not reopen old validation-intent hardening work.

`WP9` is no longer open.

It should now focus on the gaps that still limit real multi-step orchestration.

## Re-Investigation Findings

### 1. Task Scope Contract Was The First Reopened Gap

Original problem:

- task `files` are doing too many jobs at once
- the same field drives prompt hints, sparse-copy hydration, and overlap
  detection
- `copy` hydration silently skips directory hints and missing files
- workspace audit only fails protected-path edits, not out-of-scope edits

Why this matters:

- a plan can look well-scoped on paper while still giving workers ambiguous or
  incomplete file context
- a worker can change files outside the intended task scope and still remain
  promotable if those files are not protected paths
- overlap detection is based on declared hints, not a stronger write contract

Conclusion:

- the coordinator still lacks a first-class read-scope vs write-scope contract

Status as of `2026-04-07`:

- resolved in tracked code and docs via explicit `readPaths`/`writePaths`,
  copy-mode validation, write-scope conflict detection, and workspace-audit
  enforcement

### 2. Dependency Handoff Is Still Prompt-Level For Implementers

Current behavior:

- downstream tasks get dependency summaries in the prompt
- reviewer tasks additionally get bounded changed-file and diff previews
- downstream workspaces are still created from `HEAD` plus explicit file hints,
  not from dependency workspaces or patches

Why this matters:

- dependent implementer tasks cannot reliably build on upstream code changes in
  their own sandbox
- the current design works well for review and low-coupling fan-out, but not
  for multi-step implementation chains
- the orchestrator currently proves "review a prior change" better than
  "continue a prior change"

Conclusion:

- real stacked implementation is still underpowered without dependency-state
  materialization

### 3. Lifecycle Tooling Stops At Retry-And-Cleanup

Current behavior:

- failed or blocked tasks can be retried into a new run
- completed dependencies can be reused
- run-scoped cleanup exists

What is still missing:

- same-run resume after interruption
- run inventory or status listing across many retained runs
- age-based prune or retention policy

Why this matters:

- the coordinator is usable for occasional runs, but not yet pleasant to
  operate as a repeated local tool with accumulating runtime state

### 4. Budget Controls Are Mostly Per-Task And Observational

Current behavior:

- prompt budgets are enforced locally before Claude execution
- task or agent `maxBudgetUsd` can be passed through to Claude
- manifests aggregate usage and cost when the CLI returns them

Current gap:

- there is no run-level budget ceiling or stop condition across batches
- there is no stronger artifact-volume governance beyond bounded JSON fields
- the live proof already showed worker exploration and output volume remain the
  dominant cost drivers

Why this matters:

- the orchestrator can report cost after the fact better than it can govern
  total spend or output volume during a longer run

### 5. Live Proof Is Still Narrow

Current evidence:

- one non-trivial live proof exercised implementer, reviewer, validation, and
  promotion successfully

What is not yet proven:

- two or more implementers in one real run
- a dependency chain where downstream work needs upstream code state
- reopened lifecycle after interruption or retained-run cleanup pressure

Conclusion:

- the next live proof should target the current weak spots, not repeat the old
  parser-proof shape

## Active Work Package Board

### WP9: Scope Contract And Enforcement (Completed)

Status:

- completed `2026-04-07`

Goal:

- make task scope explicit enough that hydration, overlap checks, auditing, and
  promotion all enforce the same contract

Scope:

- separate read context from write intent instead of overloading `files`
- validate copy-mode inputs so directory hints and missing files are surfaced
  explicitly instead of being silently skipped
- fail or explicitly flag workspace edits outside declared write scope
- switch overlap detection to the stronger write-scope contract

Deliver:

- task plans can distinguish what a worker needs to read from what it is
  allowed to change
- out-of-scope edits are visible and block promotion unless the coordinator
  explicitly accepts them

Success bar:

- no silent scope widening between planning, workspace prep, auditing, and
  promotion

### WP10: Dependency State Materialization (Completed)

Status:

- completed `2026-04-07`

Goal:

- make dependent implementer tasks able to work from upstream task state, not
  only from prompt summaries

Scope:

- add an opt-in dependency materialization mode for downstream tasks
- allow a downstream workspace to receive reviewed dependency changes as an
  applied layer or equivalent snapshot
- record which dependency layers were applied so review and retry stay
  inspectable
- keep the default conservative path for low-coupling tasks that should remain
  summary-only

Deliver:

- the orchestrator can support a real multi-step implementation chain without
  forcing early promotion back into the repo

Success bar:

- a downstream implementer can run, inspect, edit, and validate against
  upstream task changes inside its own isolated workspace

### WP11: Resume, Inventory, And Retention

Goal:

- make the coordinator manageable across interrupted or repeated local runs

Scope:

- add same-run resume from an existing manifest
- add a run-inventory surface with compact status summaries
- add age-based prune or retention helpers for old run directories and
  workspaces

Deliver:

- operators can see what exists, continue a partially completed run, and prune
  stale runtime state intentionally

Success bar:

- lifecycle ergonomics are no longer limited to "retry into a new run" or
  "delete one run manually"

### WP12: Run-Level Budget And Artifact Governance

Goal:

- move from cost visibility to cost control

Scope:

- add optional run-level budget ceilings
- stop or warn before later batches when aggregate spend crosses the configured
  run budget
- add stronger controls or summaries for large worker stdout or stderr and
  oversized result artifacts
- make the highest-cost tasks obvious in coordinator summaries

Deliver:

- longer runs can be bounded by repo-local policy instead of only by
  post-hoc inspection

Success bar:

- the coordinator can enforce a practical spend and artifact discipline across
  the whole run, not only per worker prompt

### WP13: Multi-Step Live Workflow Proof (Completed)

Status:

- completed `2026-04-07`

Goal:

- prove the reopened scope on the kind of workflow the current design is still
  weakest at

Scope:

- run a live plan with at least two implementer tasks and one downstream review
  or validation step
- exercise whichever new scope or dependency contract lands in `WP9` and
  `WP10`
- capture concrete operator findings around resume, budget behavior, and
  promotion readiness

Deliver:

- one real retained run that demonstrates the coordinator on a chained code
  workflow instead of an isolated patch

Success bar:

- the next live proof validates the reopened packages instead of only proving
  surfaces that were already known to work

Findings:

- retained live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c`
  proved that a downstream `apply-reviewed` task records its applied
  dependency layer and that promoting only the downstream workspace is enough
  to adopt the cumulative same-file change
- repo-root `validate-run` passed for the promoted regression slice, but
  `validate-run --execution-scope task-workspace` failed because sparse copy
  hydration did not include an undeclared runtime-loaded file
  (`ai/orchestrator/agents.json`) needed by an existing prompt-budget test
- reviewer reliability is still an operational risk: the reviewer falsely
  claimed the new regression tests were missing even though the downstream
  workspace contained them

## Recommended Order

1. `WP11` first because retained live runs now exist and same-run resume plus
   inventory are the largest remaining operational gap.
2. `WP12` second because the live proof showed meaningful cache-read and
   output cost without any run-level stop.

## Non-Goals

This spike should still not turn the repo into:

- a cloud orchestration platform
- a general distributed agent runtime
- an autonomous merge bot
- a long-lived worker-memory system separate from tracked repo memory
- an excuse to run tightly coupled edit tasks in parallel

The target remains:

- a bounded local coordinator for low-coupling repo work, with explicit
  support for the small number of dependency chains that are worth modeling

## Bottom Line

The orchestrator does not need another generic hardening pass.

It needs a narrower second phase:

- make scope explicit
- make dependency state portable across isolated workspaces when requested
- make retained runs easier to operate
- add run-level budget controls
- then prove the reopened design on a live multi-step change

That is the right reopening point for the spike.
