# SPIKE: Local AI Multi-Agent Orchestration

## Question

What is still missing from the repo-local Claude multi-agent setup, and what do
we still need before treating it as a reliable local workflow instead of a
useful but still-partially-proven MVP?

## Thesis

The repo already has a real local orchestration surface.

This is not a blank-slate idea anymore:

- there is a tracked control plane under `ai/orchestrator/`
- there is a runnable coordinator in `scripts/claude-orchestrator.py`
- there are validated dry-run examples for sequential and parallel task plans

The missing work is mostly not "invent multi-agent orchestration."

The missing work is:

- prove the live path end to end
- tighten safety around parallel edits and protected paths
- give the coordinator a stronger review, retry, and cleanup workflow
- add regression coverage so the contract stays stable

## Current Status

As of `2026-04-03`, the local orchestration slice is real but still not fully
hardened.

Implemented and documented:

- tracked orchestration guide in `ai/orchestrator/README.md`
- portable orchestration contract in `ai/orchestrator/SYSTEM-SPEC.md`
- tracked worker catalog in `ai/orchestrator/agents.json`
- tracked sequential and parallel task-plan examples in
  `ai/orchestrator/tasks/*.json`
- `validate`, `plan`, and `run` commands in `scripts/claude-orchestrator.py`
- task-plan schema validation, topological batching, and selected-task runs
- repo-local runtime root under `.claude-orchestrator/`
- per-task prompts, command captures, worker result records, stdout/stderr
  artifacts, and run manifests
- model-profile routing, task-local tool allowlists, timeout settings, and
  per-task budget passthrough

Already validated:

- tracked plan validation
- dry-run manifest generation
- parallel dry-run batching
- prompt-size estimation and usage aggregation wiring
- live `example-review.json` `copy`-mode run after fixing the CLI command
  builder so variadic tool flags cannot consume the prompt argument
- real worker JSON capture, stdout/stderr capture, blocked-dependency handling,
  usage aggregation, and workspace artifact generation

## Verified Current Strengths

The current orchestrator already solves several important problems well:

- tracked control-plane files stay separate from transient runtime artifacts
- copy mode ignores `.claude-orchestrator/`, so overlapping runs do not recurse
  on their own runtime output
- worktree mode is guarded behind a clean-repo check
- task prompts include dependency summaries, task-local file hints, constraints,
  and validation hints
- `--continue-on-error` and dependency blocking already produce explicit task
  records instead of silent skips
- run manifests already aggregate prompt estimates and Claude usage when the CLI
  returns it

That means the repo already has a sound MVP shape for local bounded worker DAGs.

The live proof also added one important concrete lesson:

- the coordinator needs regression coverage around Claude CLI argument
  construction, because real `claude -p` behavior exposed a bug that dry-runs
  could not catch

## What External Token-Efficiency Guidance Changes

The `claude-token-efficient` guidance is useful here, but only in a narrow way.

What transfers well:

- recurring instruction text should stay tiny because it costs input tokens on
  every worker call
- pipeline-facing workers should prefer machine-readable output with minimal
  narration
- prompt-level guidance is useful for output discipline, but it does not
  replace mechanical safety gates

What does not transfer directly:

- a large repo-wide `CLAUDE.md` overlay for all orchestrator workers

Why not:

- this coordinator currently launches fresh `claude -p` sessions per planner or
  worker call
- the external repo explicitly notes that persistent instruction files are most
  worthwhile when output volume is high enough to offset recurring input
  overhead, and that fresh-session pipelines dilute that benefit

Recommended interpretation for this repo:

- keep planner and worker prompt scaffolding very small
- keep role-specific behavior rules in compact tracked prompt snippets or agent
  metadata, not a growing monolithic instruction layer
- add prompt-budget and output-discipline checks to the coordinator itself
- keep structured JSON output as the non-negotiable contract for planner and
  worker calls
- treat this as complementary to safety hardening, not a substitute for it

## What Is Missing

### 1. Live End-to-End Execution Proof `(Done)`

Completed proof:

- `scripts/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json --json`
- `scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-review.json --json`

Observed result:

- the live `copy`-mode run completed two `simple` workers successfully
- the coordinator captured worker JSON, stdout/stderr, per-task usage, usage
  totals, task workspaces, command files, prompt files, and result files
- the first live attempt exposed a real CLI integration bug: variadic
  `--allowed-tools` handling consumed the prompt argument, which dry-runs did
  not reveal
- fixing `claude_command(...)` to compact tool lists and terminate options with
  `--` resolved the issue

What this changed:

- live non-interactive `claude -p` execution is now proven
- Phase 1 is no longer the top unknown
- prompt economy and regression coverage moved up because the successful run
  still showed large cache-read/input context and very verbose outputs for small
  doc-only tasks

### 2. Real Sparse Copy Workspaces

As of `2026-04-04`, this is mostly implemented.

Now in place:

- `prepare_workspace(...)` no longer copies the whole repo for `copy` mode
- sparse workspaces are seeded with `AGENTS.md`, `ai/AGENTS.md`, and explicit
  task/shared file hints only
- directory hints are not hydrated
- oversized files above the configured cap are skipped

Why this matters:

- workers inherit less accidental repo context
- copy cost now aligns better with prompt minimization
- the runtime contract is now closer to what the docs promised

What is needed:

- live task-plan experience to confirm whether the small fixed base seed is
  sufficient in practice
- continued pressure on planner/task authors to keep file hints concrete for
  copy workspaces

### 3. Parallel File-Scope Safety Checks

The docs correctly say parallel workers should not edit materially overlapping
files.

As of `2026-04-04`, this is partially implemented.

Now in place:

- validate output and run manifests expose `parallelConflicts` for overlapping
  write-capable task scopes
- the run scheduler now serializes dependency-ready write-capable tasks when
  their declared scopes overlap conservatively

Current gap:

- overlap detection still relies on declared file scopes, not actual semantic
  edit intent
- there is no explicit plan-level override yet for intentionally overlapping
  parallel work

What is needed:

- continue tuning the conservative overlap rules as more live plans exist
- an explicit override only when the coordinator intentionally accepts the risk

### 4. Protected-Path Enforcement Beyond Prompt Wording

Worker prompts instruct agents not to edit `TODO.md`, `ai/state/*`,
`ai/log/*`, or `ai/indexes/*`.

As of `2026-04-04`, this is also partially implemented.

Now in place:

- post-run comparison between workspace diffs and worker-reported touched files
- task records now expose actual changed files plus protected-path violations
- tasks now fail when they report or produce forbidden edits, including in
  `workspaceMode="repo"` runs

Current gap:

- the protected-path policy is still limited to `TODO.md`, `ai/state/*`,
  `ai/log/*`, and `ai/indexes/*`

### 5. Coordinator Review and Apply Workflow

The current runtime captures prompts, command files, worker results, stdout,
stderr, manifest metadata, and workspace paths.

As of `2026-04-04`, this is partially implemented.

Now in place:

- a `review` command that summarizes per-task file diffs from worker
  workspaces
- an `export-patch` command that writes unified diff patches for copy/worktree
  runs
- a `promote` command that applies reviewed copy/worktree changes back into the
  repo when protected-path audits are clean and changed-file ownership is
  unambiguous

What it does not provide yet:

- retry or cleanup helpers around the adoption workflow
- richer cherry-pick or partial-merge helpers beyond task-level or whole-file
  promotion

Current state is therefore:

- worker execution, review/export, and conservative promotion now exist
- coordinator adoption back into the main repo is no longer fully manual for
  isolated clean tasks

What is needed:

- lifecycle support around retries and cleanup
- broader regression coverage so the promotion rules stay stable

### 6. Resume, Retry, and Cleanup Operations

Each run gets a unique runtime directory and workspace tree, which is good for
isolation.

What is missing:

- resume a partially completed run
- retry only failed or blocked tasks in the same run context
- prune or clean old runtime artifacts
- remove detached worktrees after worktree-mode runs

Without this, the coordinator can execute runs but has weak lifecycle
management.

### 7. Structured Final Validation Support

Workers can report `validationCommands`, but the orchestrator does not execute
them or summarize final validation state.

That leaves the coordinator with manual follow-through.

What is needed:

- a coordinator-side validation step that can run or at least consolidate the
  reported commands
- a manifest section that distinguishes "worker suggested validation" from
  "coordinator actually ran validation"

### 8. Automated Regression Coverage

There is currently no dedicated automated test suite for the orchestrator.

What is missing:

- parser and schema tests for agents and task plans
- batching tests for dependency ordering and `--task` selection
- failure-path tests for blocked, failed, and malformed JSON worker output
- workspace-prep tests for copy, worktree, and protected runtime-root behavior
- manifest and usage aggregation tests

This matters because the orchestration surface is now large enough that docs and
dry-runs alone are not strong regression protection.

### 9. Prompt Economy and Output Discipline

As of `2026-04-04`, prompt economy is partially implemented.

Now in place:

- compact role-specific output-discipline prompts in `agents.json`
- section-level prompt accounting for planner and worker prompts
- optional hard prompt ceilings via `maxPromptEstimatedTokens` and
  `maxPromptChars`
- coordinator-side prompt-budget failure before live Claude execution
- compacted dependency summaries and bounded prompt-facing list sections

Current gap:

- planner and worker prompts still repeat some shared coordinator framing on
  every invocation
- default prompt budgets are now present, but they still need live tuning based
  on real task mixes
- prompt compaction is currently generic list or summary truncation, not
  section-specific summarization
- worker-result guidance still relies on prompt wording rather than a richer
  schema for explicit unknown values

What is needed:

- measure and tune the default prompt ceilings against a few real multi-task
  runs
- decide whether some prompt sections need smarter summarization than simple
  truncation
- strengthen worker-result guidance for unknown values: use `null` or explicit
  unknown markers instead of guessing where the schema permits it
- a deliberate decision on whether any repo-level `CLAUDE.md` should apply to
  orchestrator work at all; default recommendation is no unless measurement
  proves a net benefit for fresh worker sessions

This is mostly a coordinator-design problem, not a new prompt-writing exercise.

## What We Still Need First

The best next slice is not "add more agents."

The best next slice is:

1. tighten safety around sparse copies, overlap detection, and protected paths
2. add a minimal coordinator review or diff workflow
3. add focused regression tests around the Python coordinator
4. tune prompt budgets against a few real multi-task runs if live evidence
   shows drift

That sequence raises trust faster than adding more planner cleverness or more
sample task plans.

## Recommended Work Order

### Phase 1: Proof `(Done)`

Deliver:

- one small live worker run
- documented observed artifact layout
- exact validated command recorded in AI memory

Success bar:

- live `claude -p` execution is no longer an explicitly unverified path

### Phase 2: Prompt Economy

Deliver:

- compact role-specific prompt rules
- section-level prompt accounting in manifests
- soft or hard prompt-size budgets
- dependency-summary and validation-hint compaction
- explicit "JSON only, minimum viable output, do not guess" worker discipline

Success bar:

- prompt size and worker output shape are intentionally bounded instead of
  drifting with each task-plan revision

Status:

- partially done as of `2026-04-04`; next follow-up is budget tuning rather
  than missing core plumbing

### Phase 3: Safety

Deliver:

- true sparse copy mode
- parallel overlap detection
- protected-path audit on worker outputs and workspace diffs

Success bar:

- the coordinator can reject unsafe multi-agent plans before or after worker
  execution

### Phase 4: Coordinator Ergonomics

Deliver:

- review or diff helper
- retry failed tasks
- cleanup or prune runtime artifacts
- explicit worktree removal

Success bar:

- coordinator workflow is practical across more than one run

### Phase 5: Regression Harness

Deliver:

- Python-side unit or integration tests for plan loading, batching, workspace
  prep, manifest generation, and failure handling

Success bar:

- future orchestrator changes no longer rely mainly on manual dry-run checking

## Non-Goals

This spike should not turn the repo into:

- a cloud orchestration service
- a distributed agent runtime
- a fully autonomous merge bot
- a long-lived agent-memory platform separate from the tracked repo memory
- an unrestricted multi-repo automation system

The target remains:

- a bounded local coordinator for low-coupling repo tasks

## Bottom Line

The repo already has a credible local multi-agent control plane.

What it does not have yet is full operational confidence.

The right next move is to harden and prove the current design:

- live-run proof is now done
- next: prompt economy, workspace safety, and review ergonomics
- then coordinator ergonomics
- then regression coverage

That keeps the orchestration surface useful, inspectable, and aligned with the
repo's existing local-first workflow.
