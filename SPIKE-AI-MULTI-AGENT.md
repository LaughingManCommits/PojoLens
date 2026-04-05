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

- selective cherry-pick helpers around the adoption workflow
- richer cherry-pick or partial-merge helpers beyond task-level or whole-file
  promotion

Current state is therefore:

- worker execution, review/export, and conservative promotion now exist
- coordinator adoption back into the main repo is no longer fully manual for
  isolated clean tasks

What is needed:

- broader coordinator-side adoption refinements
- broader regression coverage so the promotion rules stay stable

### 6. Resume, Retry, and Cleanup Operations

Each run gets a unique runtime directory and workspace tree, which is good for
isolation.

As of `2026-04-04`, this is partially implemented.

Now in place:

- a `retry` command that starts a new run from a prior manifest, defaults to
  failed or blocked tasks, and reuses already-completed dependencies
- a `cleanup` command that removes run directories, workspace directories, and
  detached worktrees created for that run

What is still missing:

- resume a partially completed run
- retry in the exact same run context
- age-based pruning across many runs
- richer selective cleanup flows

The coordinator now has basic lifecycle tooling, but not full run-resume or
fleet-style pruning.

### 7. Structured Final Validation Support

Workers can report validation suggestions, and the coordinator now has both raw
command and structured-intent paths.

As of `2026-04-05`, this is partially implemented.

Now in place:

- a `validate-run` command that dedupes worker-suggested validation commands
- default validation policy now excludes non-completed tasks unless the
  coordinator explicitly opts into other statuses
- command-quality policy now rejects shell-composed or unknown-entrypoint
  validation commands by default unless the coordinator explicitly overrides it
- optional coordinator-side execution of those commands from repo root
- a manifest section that distinguishes worker-suggested validation from
  coordinator-run validation results
- structured `validationIntents` for `repo-script` and `tool` suggestions,
  which render to command text for review output but execute via safe argv
  handling instead of shell parsing
- accepted legacy raw validation commands now reuse that same argv-safe path
  when the coordinator can normalize them into a direct tool or repo-script
  invocation
- `validate-run --intents-only`, which rejects legacy raw
  `validationCommands` even when they normalize cleanly and reports which
  tasks still emit those compatibility-only suggestions
- `run` / `retry --worker-validation-mode intents-only`, which tightens worker
  prompts, rejects non-empty raw `validationCommands` in worker JSON before a
  task record is accepted, and records the effective mode in runtime payloads
- tracked task or agent `workerValidationMode`, so the checked-in task plans
  can now opt into intent-only enforcement without relying on a CLI flag;
  runtime payloads surface `mixed` plus per-task modes when a run is not
  uniform, and the runtime flag is now just an explicit override
- agent-level `workerValidationMode = intents-only` defaults for the tracked
  `analyst`, `implementer`, and `reviewer` roles, with the sample task plans
  inheriting those defaults instead of repeating per-task settings
- `validate --json` plus run/manifests now surface each task's effective
  worker-validation source (`override`, `task`, `agent`, or `default`) so the
  authoring pattern is inspectable
- `intents-only` worker runs now use a stricter JSON schema that limits
  `validationCommands` to `[]` or `null`, so raw legacy command items are
  rejected before the coordinator's post-parse guard
- `validate --json` plus run/manifests now also surface explicit compat-task
  ids/counts, and planner guidance treats `workerValidationMode = compat` as a
  legacy exception path instead of a normal authoring choice
- `validate`, `run`, and `retry` now also accept
  `--require-intents-only-workers`, which fails fast when any effective task
  mode still resolves to `compat` and makes zero-compat tracked plans
  enforceable instead of merely inspectable
- live planner, worker, and `validate-run` waits now emit phase-tagged
  slop-status lines on interactive `stderr` so operators can see in-flight
  work without breaking `stdout` JSON consumers

What is still needed:

- decide whether to broaden the intent vocabulary beyond `repo-script` and
  `tool`
- decide whether raw `validationCommands` should remain in the compat worker
  schema at all now that `intents-only` workers reject them at the schema
  boundary, compat-task reporting makes the remaining legacy usage obvious,
  and strict zero-compat gates can now reject it before execution

### 8. Automated Regression Coverage

As of `2026-04-04`, this is partially implemented.

Now in place:

- Python-side regression tests for CLI command shaping and prompt-budget
  enforcement
- scheduler safety tests for overlapping write scopes, fail-fast behavior, and
  dependency blocking
- worker failure-path coverage for malformed JSON output
- workspace-prep coverage for sparse copy mode and protected-path auditing
- review/export/promote/retry/cleanup/validate-run coverage for the coordinator
  lifecycle

What is still missing:

- broader negative parser/schema coverage for invalid agents and task plans
- more worktree-mode and cleanup error-path coverage
- live CLI integration regression beyond the current focused proof

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
- live `example-review.json` and `example-parallel.json` runs now show prompt
  estimates staying comfortably within the current ceilings, so prompt text
  size itself is not the main cost driver on these tracked plans
- prompt compaction is currently generic list or summary truncation, not
  section-specific summarization
- worker-result guidance is no longer prompt-only: the coordinator now compacts
  and validates worker JSON, the worker schema now allows `null` for
  `filesTouched` / `validationCommands` / `followUps` / `notes` when those
  list fields are genuinely unknown, and manifests preserve that distinction
  from `[]` via explicit unknown-field metadata
- live runs also showed that downstream reviewers need a richer bounded
  dependency handoff than a one-line summary, and that output verbosity can
  still dominate cost even when prompt budgets are healthy

What is needed:

- keep validating the current ceilings against broader plans and only lower or
  raise them if later task mixes show real drift
- decide whether some prompt sections need smarter summarization than simple
  truncation
- decide whether any additional worker-result fields beyond the current list
  fields need explicit unknown semantics
- decide whether the current `repo-script` / `tool` validation-intent surface
  is enough or needs broader presets
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
4. tighten validation policy and worker-result discipline based on live runs

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
- live prompt-budget tuning is now grounded by tracked sample runs
- next: decide whether raw validation commands should eventually downgrade to a
  compatibility-only path, plus any further result-schema refinement
- regression coverage is now real, but it should keep growing around newly found
  failure modes

That keeps the orchestration surface useful, inspectable, and aligned with the
repo's existing local-first workflow.
