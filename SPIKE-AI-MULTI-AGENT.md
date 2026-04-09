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

The remaining blocker is more specific.

The coordinator is already credible for:

- bounded analysis or review plans
- isolated implementer tasks whose changes can be promoted directly
- pre-promotion validation of a single worker sandbox

The last reopened weakness was:

- longer runs that need run-level spend or artifact governance instead of only
  post-hoc inspection

## Verified Baseline

As of `2026-04-08`, the following are real in code and docs:

- task plans use explicit `sharedContext.readPaths`, task `readPaths`, and task
  `writePaths` instead of overloading task `files`
- sparse `copy` workspaces hydrate only declared `readPaths` and any existing
  file-backed `writePaths`
- copy-mode validation surfaces missing or directory `readPaths` and oversized
  hydration inputs instead of skipping them silently
- worker prompts plus declared workspace state are the worker contract; workers
  no longer inherit `AGENTS.md` or `ai/AGENTS.md` implicitly
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

### 3. Lifecycle Tooling Was The Third Reopened Gap

Original behavior:

- failed or blocked tasks can be retried into a new run
- completed dependencies can be reused
- run-scoped cleanup exists

Status as of `2026-04-07`:

- resolved in tracked code and docs via same-run `resume`, runtime-root
  `inventory`, and age-based `prune`
- `resume` now prefers the run's `selected-plan.json` snapshot, defaults to
  unfinished or missing tasks, preserves completed records, and reuses the
  same `runId`, run directory, and workspaces directory
- `inventory` surfaces compact status, resume-candidate, validation, prompt,
  and cost summaries across retained runs
- `prune` supports age filters plus `--keep` and skips incomplete runs unless
  `--include-incomplete` is set explicitly
- resumed `copy` or `worktree` tasks still rebuild fresh sandboxes before
  rerun; same-run resume is run continuity, not partial workspace recovery

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

Status as of `2026-04-08`:

- resolved in tracked code and docs via optional top-level `runPolicy`
- aggregate run spend can now warn or stop before later batches once
  completed-task cost crosses `runBudgetUsd`
- per-task `stdout`, `stderr`, and worker-result artifact sizes can now warn
  or stop later scheduling
- validate output now exposes tracked `runPolicy`, while run/manifests plus
  retained-run summaries expose run-governance status, alerts, highest-cost
  tasks, and artifact totals
- stop behavior is intentionally between-batch governance, not mid-flight task
  cancellation

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

### 6. Coordinator Memory And Worker Contract Were Still Coupled

Current evidence:

- `copy` workspaces auto-seeded `AGENTS.md` and `ai/AGENTS.md`
- worker prompts still told workers to follow those coordinator files
- tracked sample plans still pulled coordinator-memory files into shared
  worker context even when the task did not actually need them

Why this matters:

- worker behavior becomes less deterministic when it depends on ambient
  coordinator memory instead of only on the selected agent definition, task
  prompt, dependency handoff, and declared workspace files
- this also widens the worker memory footprint and makes it harder to reason
  about what context was truly necessary for a task

Status as of `2026-04-08`:

- resolved in tracked code and docs: `copy` workspaces no longer auto-seed
  coordinator memory files, worker prompts now treat the prompt plus declared
  workspace as the full contract, and tracked sample plans only pass
  `AGENTS.md` / `ai/AGENTS.md` when a task explicitly needs them

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

### WP11: Resume, Inventory, And Retention (Completed)

Status:

- completed `2026-04-07`

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

Findings:

- same-run `resume` now reuses the original run id plus runtime directories,
  defaults to unfinished or missing tasks, and prefers the run's own selected
  plan snapshot instead of drifting back to the broader tracked plan file
- `inventory` reports compact per-run status, resumable task ids,
  prompt-estimate, and cost fields across retained runtime state
- `prune` now supports age-based retention with `--keep` and skips incomplete
  runs by default
- resumed `copy` and `worktree` tasks still rebuild fresh task sandboxes
  before rerun, so the new surface is run-level continuity rather than
  partial workspace recovery

### WP12: Coordinator vs Worker Context Boundary (Completed)

Status:

- completed `2026-04-08`

Goal:

- separate coordinator/project-manager memory from worker runtime contract

Scope:

- stop auto-seeding `AGENTS.md` and `ai/AGENTS.md` into worker `copy`
  workspaces
- make the selected agent definition plus task prompt and declared workspace
  the full worker contract
- remove unnecessary coordinator-memory files from tracked sample plans and
  docs

Deliver:

- workers rely on explicit prompt/workspace packets instead of ambient
  coordinator memory

Success bar:

- worker execution is explainable from the selected agent definition, the task
  prompt, dependency handoff, and declared workspace files alone

### WP14: Run-Level Budget And Artifact Governance (Completed)

Status:

- completed `2026-04-08`

Goal:

- move from cost visibility to practical run-level cost control

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

Findings:

- tracked plans may now declare top-level `runPolicy` with aggregate budget
  plus per-task artifact byte limits and independent `warn` / `stop`
  behaviors
- governance is evaluated after each completed batch; when a blocking alert is
  present, the coordinator marks unscheduled tasks as blocked before the next
  batch starts
- `run --json` and run manifests now expose `runGovernance` with alert counts,
  blocking alerts, highest-cost tasks, and aggregate artifact totals; retained
  run summaries also surface governance status plus total artifact bytes
- `validate --json` exposes the tracked `runPolicy` so repo-local plan review
  can catch governance settings before live execution
- the implementation intentionally avoids mid-flight cancellation; already
  running tasks in the current batch finish and are then evaluated against the
  run policy

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

### WP15: Topology Audit And Lean-Plan Signals (Completed)

Status:

- completed `2026-04-08`

Goal:

- make overweight task graphs visible before or during a run instead of only
  relying on planner wording

Scope:

- expose compact topology summaries in `validate --json`, `run --json`, and
  run manifests
- surface agent mix, read-only vs write-capable task counts, batch shape, and
  dependency depth
- add conservative warnings only for obvious cases where the plan may be
  heavier than necessary
- surface compact topology fields in retained-run summaries so inventory stays
  useful without opening each manifest

Deliver:

- operators can inspect whether a DAG is genuinely lean before spending tokens
  on it

Success bar:

- the coordinator can point at an obviously heavy plan shape, not only ask the
  planner to avoid one

Findings:

- tracked validation and run surfaces now emit `topology` with agent counts,
  read-only/write-capable task counts, batch sizes, max dependency hops, and
  warning count
- retained-run summaries now expose topology batch count, max parallel width,
  and warning count for inventory-style review
- warnings stay intentionally conservative: the coordinator only flags a
  read-only plan that still adds reviewer hops, or a single write-capable task
  that is preceded by upstream analyst work
- tracked `example-review.json` is now a one-task reviewer sample, and
  `example-parallel.json` now demonstrates parallel analysts without an
  automatic review stage

### WP16: Live RunPolicy Proof (Completed)

Status:

- completed `2026-04-08`

Goal:

- prove the new run-level governance on a real retained run, not only in dry
  runs or unit tests

Scope:

- add one tiny tracked live plan with explicit `runPolicy` thresholds
- run it live on a read-only workflow with at least two batches so later work
  can be blocked
- confirm retained-run summaries surface the governance and topology result
- capture empirical cost and artifact data from the first real governed task

Deliver:

- one retained live run that proves between-batch governance stop with real
  usage data

Success bar:

- the first task completes, a later task is blocked by run policy, and the
  retained summary surfaces the stop cleanly

Findings:

- retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe`
  completed the first analyst task, then blocked the second task before the
  next batch because run cost `$0.079901` exceeded the configured
  `$0.000001` stop budget
- the same run also surfaced the configured artifact warning: the first task
  wrote a `921 B` worker-result file and `2642 B` total artifacts against a
  `1 B` result limit
- retained-run inventory now shows the same live run with
  `governanceStatus = stop`, `governanceAlertCount = 2`,
  `topologyBatchCount = 2`, and `topologyWarningCount = 0`
- even this tiny read-only analyst task consumed large cache-read volume
  (`186234` cache read input tokens), so practical `runPolicy` thresholds need
  empirical tuning rather than guessed micro-budgets

### WP17: Practical CSV Slice Guardrails (Completed)

Status:

- completed `2026-04-09`

Goal:

- move practical `runPolicy` tuning onto a real write-capable product slice
  instead of another synthetic proof

Scope:

- add one tracked representative plan around the first bounded CSV adapter
  slice
- keep topology lean with one implementer task and one reviewer task
- set non-contrived budget and artifact ceilings, then capture dry-run prompt
  budgets
- avoid reopening coordinator-contract work unless a later live run exposes a
  real gap

Deliver:

- a tracked representative plan plus dry-run calibration for practical
  `runPolicy` defaults

Success bar:

- the plan validates cleanly, stays lean, and dry-run surfaces practical
  thresholds without prompt-budget failures

Findings:

- tracked plan `ai/orchestrator/tasks/wp17-csv-typed-loader-slice.json` now
  models a real CSV starter slice with `runBudgetUsd = 2.0`,
  `maxTaskResultBytes = 16000`, and `maxTaskStdoutBytes = 24000`
- `validate --json` resolves the plan to one `implementer` and one
  `reviewer`, one write-capable task, one read-only task, and zero topology
  warnings
- retained live run `20260409T125416Z-wp17-csv-typed-loader-slice-b41544f3`
  completed both tasks with `governanceStatus = ok`, zero alerts, and
  `totalCostUsd = 0.93547`
- the same live run consumed `1599` estimated prompt tokens, wrote `10987 B`
  total artifacts, and stayed well under the configured budget and artifact
  ceilings
- because the CSV slice was already implemented in the repo, the implementer
  made no edits and instead confirmed that the nine-file write scope was
  already aligned with the spike
- coordinator-side `validate-run --intents-only` rejected all three suggested
  validation intents (`mvn`, `grep`, and a pseudo repo-script), which exposed
  a remaining quality gap in worker validation suggestions even when the task
  result itself is sound
- retained live run `20260409T141118Z-wp17-csv-typed-loader-slice-26f856cc`
  showed partial improvement after the first prompt hardening: the reviewer
  emitted `[]`, but the implementer still suggested `repo-script: mvnw ...`,
  which coordinator policy rejected
- follow-up hardening now renders approved validation hints with their
  normalized intent shape, explicitly tells workers not to swap entrypoints
  like `mvn` and `mvnw`, and resolves PATH-backed tool wrappers before
  shell-free execution
- retained live run `20260409T150845Z-wp17-csv-typed-loader-slice-e16f357e`
  then emitted `tool: mvn ...`, and coordinator-side
  `validate-run --include-status failed --intents-only` accepted and executed
  that Maven test successfully; the task itself still failed for a separate
  reason because the representative `WP17` write scope omitted `docs/csv.md`
- tracked plan `ai/orchestrator/tasks/wp17-csv-typed-loader-slice.json` now
  includes `docs/csv.md` in the implementer write scope so adjacent CSV guide
  work is in-bounds for the representative slice
- retained live run `20260409T160647Z-wp17-csv-typed-loader-slice-f939dec7`
  completed the implementer task cleanly, created `docs/csv.md`, updated the
  linked docs, stayed within `runPolicy` at `$0.949219`, and again produced an
  accepted `tool: mvn ...` validation intent that coordinator `validate-run`
  executed successfully
- the same live run left the reviewer task `blocked`, not because of write
  scope or validation policy, but because `summary-only` dependency handoff
  did not materialize the newly created `docs/csv.md` into the reviewer
  workspace; that is now the remaining product-shaped orchestration gap

## Recommended Order

1. The tracked reopened work packages are complete through `WP16`.
2. `WP17` now closes the practical-threshold follow-up on a representative
   write-capable product slice.
3. Reopen this area only if you need reviewer-visible materialization for new
   dependency files, tighter representative write scopes, or a different live
   plan.

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

The reopened phase already delivered explicit scope, portable dependency
state, a stronger live chained proof, retained-run lifecycle helpers,
coordinator-side lean-topology visibility, and a real governed run proof.

The remaining open work is now optional:

- tighten representative write scopes only if you want later live product
  slices to permit adjacent doc work like `docs/csv.md`
- materialize reviewed dependency outputs only if downstream reviewers need to
  inspect newly created files instead of diff previews alone
- calibrate additional representative live plans only if one CSV-based proof is
  not enough operationally

That is the right remaining scope for the spike.
