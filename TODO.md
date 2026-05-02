# TODO

## Product Direction

**Conclusion:** PojoLens is the embedded reporting and governed query layer for
Java apps working over already-materialized object snapshots.

**Winning niche:** Safe configurable reporting over data the application already
owns in memory. Strong diagnostics, plan preview, explain, keyset pagination,
telemetry hooks, reusable report/chart/schema helpers, optional Spring Boot
wiring, and repo-local AI orchestration that helps maintain the library without
becoming part of the runtime artifact.

**Non-goals:**
- Not a replacement for jOOQ, Querydsl, or Spring Data for DB-backed queries.
- No free-form AI/chatbot natural queries.
- No auth, RBAC, or tenant-security framework in core.
- No AI orchestration runtime inside the published PojoLens Java artifacts.

**AI orchestration requirements:**
- Parallel agent execution is a first-class requirement for independent tasks.
- Parallelism must stay bounded by declared dependencies, write scopes, and
  workspace isolation; conflicting write-capable tasks must serialize.
- CLI, runtime layering, and any LangGraph backend must preserve the existing
  concurrent-ready task-plan model instead of collapsing to sequential-only
  execution.

---

## Status Overview

Execution order is dependency-first, not ticket-number order.

| WP  | Title                                | Status   | Key deliverables |
|-----|--------------------------------------|----------|------------------|
| WP27| Orchestrator CLI Productization      | Complete    | Installable local CLI around the existing multi-agent commands, with stable JSON and one canonical `scripts/ai` implementation home |
| WP28| Orchestrator Runtime Layering        | Complete | Internal package split for plan governance, workspace safety, provider calls, manifests, validation, and parallel scheduling |
| WP29| LangGraph Execution Spike            | Complete | Decision record and prototype for checkpointed parallel graph execution; keep the custom scheduler and manifest model as the production path for now |
| WP30| Run Visibility And Operator UX       | Complete | Added `status`, richer inventory/review/promotion summaries, and documented the retained-run operator flow |
| WP31| Orchestrator Trace And Evaluation    | Complete | Added run-event lineage, retained trace/branch summaries, branch-context handoff IDs, a run evaluator surface, and a tracked multi-batch regression fixture |
| WP32| Orchestrator Bench And Evals         | Complete | Added a machine-readable run-quality score surface, tracked eval fixtures, and a retained-run corpus view for comparing decomposition, retries, review/promotion accuracy, and parallel efficiency |
| WP33| Approval State Machine               | Complete | Persist explicit approval lifecycle states, coordinator review/validation/promotion checkpoints, and retained-run approval summaries |
| WP36| Orchestrator Run And Planner Decomposition | Complete | Reduced `claude-orchestrator.py` to a 50-line shim, brought `pojo_lens_agents.orchestrator_app` down to 863 lines, and split parser/contracts/utils/plan/review-provider support into focused `pojo_lens_agents` modules |
| WP35| Orchestrator Command Decomposition   | Complete | Split `claude-orchestrator.py` into focused package modules while preserving CLI and JSON contracts |
| WP34| Trace Export                         | Complete | Added `export-trace`, a stable `pojo-lens-orchestrator-trace/v1` span export, and parent-child task/batch/checkpoint lineage derived from retained events and branch contexts |
| WP37| Reviewer Findings And Promotion Governance | Complete | Structured reviewer findings with severity, promotion-readiness blocking from reviewer findings, stronger review summaries, and retained-run visibility for material review risk |
| WP38| Docs And Text Quality Guardrails     | Complete | Mojibake/text-sanity checks, ASCII-safe docs promotion checks, and coordinator validation for documentation-oriented runs |
| WP39| Low-Cost Worker Profiles And Output Discipline | Complete | Lean docs-oriented worker/reviewer profiles, tighter output contracts, retained verbosity visibility, and a tracked cheap-proof plan for repeated low-cost live proofs |
| WP41| Crash-Safe Manifest Flushing         | Complete | Atomic manifest writes via write-to-temp-then-rename so a process crash never corrupts a retained run |
| WP42| Within-Run Task Retry                | Complete | Automatic per-task retry with exponential backoff for transient failures (rate-limit, timeout, provider error) |
| WP43| Direct Anthropic SDK Provider        | Complete | Replace `claude` subprocess provider with the Anthropic Python SDK to unlock streaming, accurate cache stats, and SDK-managed rate-limit handling |
| WP44| Async Task Execution                 | Complete | Replace `ThreadPoolExecutor` with `asyncio` subprocess execution to remove one-thread-per-task overhead and enable streaming |
| WP45| OpenTelemetry Observability          | Complete | Added optional OTLP HTTP emission from retained run spans, live run/export endpoint overrides, and task cost/model attributes on OTEL spans |
| WP46| Typed Agent Contracts                | Complete | Added Pydantic v2 contract models, Pydantic-backed dataclasses, typed plan/agent/manifest validation boundaries, `py.typed`, and mypy coverage |
| WP47| Human-in-the-Loop Approval Gates     | Complete | Added batch-boundary HITL policy, run/resume CLI flags, persisted gate events, sentinel/interactive approval, auto-approve test mode, and abort blocking |
| WP48| Pre-Flight Cost Estimation           | Complete | Added tracked model pricing, pre-flight per-task/per-batch USD+token estimates, `run --estimate`, validate-time budget warnings, and retained `costEstimate` payloads/manifests |
| WP49| Dynamic Plan Mutation                | Complete | Added typed `followUpTasks`, run-policy/CLI follow-up mode, between-batch task injection, persisted lineage, and selected-plan mutation for resume |
| WP50| Rate-Limit-Aware Proactive Scheduling| Planned | Track rolling token consumption per time window and pre-throttle task dispatch before hitting quota, replacing pure reactive backoff |
| WP51| Cross-Run Memory and Pattern Learning | Complete | Persist a structured ledger of what worked and failed across runs so the planner can consult prior evidence when decomposing similar tasks |
| WP52| Diff-Aware Incremental Replay        | Planned | On resume or retry, skip tasks whose inputs (prompt, read paths, dependency outputs) are identical to a prior successful execution |
| WP53| CLI Ergonomics                       | Planned | Config file (`pojolens-agents.toml`) for default flags and a `--watch` live progress formatter that tails run events to stderr during long runs |
| WP54| TUI Dashboard                        | Planned | Live `textual`-based terminal dashboard during runs: task status grid, rolling cost, active-task log tail, and key bindings for HITL gate approval |
| WP55| Guided Wizard Mode                   | Planned | No-args interactive wizard that walks the operator through the full validate → run → review → promote lifecycle without needing to know any commands |
| WP56| Run Completion Notifications         | Planned | Desktop notification, webhook POST, or Slack message when a run finishes, keyed off the `run-finished` event with status and cost summary |
| WP57| Human Diff View Before Promote       | Planned | `diff-run <run-id>` command that renders git-style file diffs of workspace vs repo so the operator sees exactly what changed before promoting |
| WP40| End-To-End Coding Run Reliability    | Planned | Full run quality pass across planning, review, selective promotion, post-promotion validation, and tracked real-world orchestration proofs |
| WP18| JDK 25 Runtime Knob Evaluation       | Deferred | Optional runtime-performance guidance; not blocking the orchestration toolchain work |
| Release Gate | Release Gate                  | Deferred | Cut only after the active roadmap queue and release guardrails are complete |

Completed implementation detail is intentionally not kept here. Historical
detail stays in `CHANGELOG.md`, `ai/state/recent-validations.md`, and git
history.

Post-WP live-run hardening:
- `2026-05-01`: Coordinator contract hardening landed after the real quickstart coding runs: `validate` now warns about risky reviewer prompt budgets, promotion dedupes exact duplicate reviewer/materialized file ownership, and promoted coding runs stay `awaiting_validation` until repo-scope validation is recorded after promotion.
- `2026-05-02`: WP39 is complete; the orchestrator now has lean docs-oriented worker/reviewer profiles, `outputProfile = lean`, tighter worker JSON caps, retained verbosity visibility, and a tracked `example-cheap-proof-docs.json` plan for repeated low-cost live proofs.

---

## WP27: Orchestrator CLI Productization

**Priority:** High

**Goal:** Turn the repo-local multi-agent scripts into a real local CLI while
preserving the current command behavior and keeping AI tooling organized under
`scripts/ai/`.

**Context:**
- `scripts/ai/claude-orchestrator.py` already has an argparse command surface.
- `scripts/ai/claude-orchestrator.ps1` remains the direct Windows script
  entrypoint for contributors who do not install the console command.
- The current commands are powerful but still feel like repo scripts rather
  than an intentional tool.
- Productizing the CLI should not move AI orchestration into the Java library
  modules or published artifacts.

**Tasks:**
- [x] Choose the executable name and packaging shape, such as a Python
      `pyproject.toml` console script under a repo-local tooling package.
- [x] Move CLI code into an importable package with the `pojolens-agents`
      package wrapper around the existing script.
- [x] Preserve existing subcommands: `validate`, `plan`, `run`, `resume`,
      `retry`, `review`, `export-patch`, `promote`, `validate-run`,
      `inventory`, `prune`, and `cleanup`.
- [x] Add consistent global options for repo root, runtime root, JSON output,
      dry-run behavior, verbosity, and provider binary resolution; first slice
      adds package-level `--repo-root`.
- [x] Preserve `--max-parallel` behavior as a documented primary CLI feature
      for independent task execution.
- [x] Define stable exit-code semantics for validation errors, worker failures,
      blocked runs, unsafe promotion attempts, and unexpected tool crashes.
- [x] Add command help snapshots or focused CLI contract tests so future
      refactors do not drift the public tool surface by accident.
- [x] Update `ai/orchestrator/README.md` and `ai/orchestrator/SYSTEM-SPEC.md`
      to describe the CLI as the primary operator interface.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/cli.py scripts/ai/pojo_lens_agents/governance.py scripts/ai/pojo_lens_agents/path_safety.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/run_store.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/workspace_review.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP28: Orchestrator Runtime Layering

**Priority:** High

**Goal:** Split the monolithic orchestrator script into clear runtime layers so
the CLI can evolve without making run scheduling, workspace protection, and
manifest handling harder to reason about.

**Context:**
- The current script owns CLI parsing, plan validation, prompt rendering,
  workspace hydration, worker invocation, manifest writing, review, promotion,
  and validation execution in one file.
- LangGraph or any future execution backend will be easier to evaluate if the
  repo-specific safety model is already isolated from the scheduler.
- The safety rules are the valuable part: sparse workspaces, write-scope
  checks, protected paths, validation intents, review, export, and promotion.
- Parallel agent execution is required for independent tasks, but write-capable
  tasks with overlapping scopes must still serialize conservatively.

**Tasks:**
- [x] Extract plan contract support, topology analysis, and run-policy
      governance into package layers with focused tests.
- [x] Extract ready-task batching and conservative parallel scheduling into a
      runtime layer that preserves `--max-parallel` semantics outside the CLI
      parser; first slice added `pojo_lens_agents.runtime`.
- [x] Extract workspace hydration, diff auditing, artifact sizing, and
      review/promotion safety primitives into workspace and path-safety layers.
- [x] Extract provider invocation, provider JSON extraction, and usage parsing
      into a provider adapter layer that can support more than one execution
      backend.
- [x] Extract retained-run manifest path resolution, selected-plan fallback, and
      workspace directory derivation into a run-store layer used by run
      inventory, resume, retry, prune, cleanup, review, promotion, and
      validation commands.
- [x] Keep path traversal, protected-path, duplicate ownership, and out-of-scope
      write checks independent of the CLI command parser.
- [x] Preserve the current JSON output contracts or document any intentional
      versioned changes.
- [x] Add tests around layer boundaries before changing runtime behavior; first
      slice covers topology, conflict detection, and ready-batch selection.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/cli.py scripts/ai/pojo_lens_agents/governance.py scripts/ai/pojo_lens_agents/path_safety.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/run_store.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/workspace_review.py scripts/tests/test_agent_governance.py scripts/tests/test_agent_path_safety.py scripts/tests/test_agent_provider.py scripts/tests/test_agent_run_store.py scripts/tests/test_agent_runtime.py scripts/tests/test_agent_workspace_review.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-review.json --json`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-materialized-chain.json --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP29: LangGraph Execution Spike

**Priority:** Medium

**Goal:** Determine whether LangGraph should become an optional execution
backend for the orchestrator, focused on durable execution, interrupts, replay,
and graph-state visibility.

**Context:**
- The existing orchestrator is already a DAG runner with manifests, dependency
  batches, resume, retry, review, validation, and promotion.
- LangGraph may improve checkpointing, human approval gates, replay, and
  graph-level observability.
- Any LangGraph prototype must prove independent agent nodes can run in
  parallel while preserving dependency and write-scope constraints.
- LangGraph must not own repo safety rules. Workspace isolation, write scopes,
  protected paths, validation intent normalization, and promotion checks remain
  repo-owned.
- This work is a decision spike first, not a wholesale rewrite.

**Decision:** Complete. The spike produced a tracked decision record and
prototype, then concluded that PojoLens should keep the current custom
scheduler and manifest model as the production path for now. LangGraph remains
an optional future wrapper candidate for checkpointing, replay, and
graph-state visibility only.

**Work done:**
- Added `scripts/ai/pojo_lens_agents/langgraph_spike.py` as a dependency-free
      prototype boundary around lifecycle mapping, scheduler comparison, and
      decision readout.
- Recorded the final decision inline in this work package and the retained
      orchestration/memory state.
- Added focused tests covering lifecycle mapping, checkpointed parallel
      scheduling, manifest-first resume/retry semantics, and the final
      recommendation.

**Tasks:**
- [x] Map the current run lifecycle onto graph nodes: load plan, validate
      scope, hydrate workspace, invoke worker, parse result, audit diff,
      checkpoint, review interrupt, validate-run, and promote.
- [x] Prototype the smallest checkpointed graph that can execute a dry-run
      worker plan or simulated two-task parallel run.
- [x] Compare LangGraph parallel node execution with the current ready-batch
      scheduler, including failure, retry, and partial-checkpoint behavior.
- [x] Evaluate human-in-the-loop interrupts for review and promotion approval.
- [x] Evaluate replay/resume semantics against the current manifest-based
      `resume` and `retry` behavior.
- [x] Document what LangGraph would replace, what it would wrap, and what must
      remain custom.
- [x] Decide whether to proceed with an optional backend, keep the custom
      scheduler, or defer LangGraph until the CLI/runtime split is complete.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/cli.py scripts/ai/pojo_lens_agents/governance.py scripts/ai/pojo_lens_agents/path_safety.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/run_store.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/workspace_review.py scripts/tests/test_agent_governance.py scripts/tests/test_agent_path_safety.py scripts/tests/test_agent_provider.py scripts/tests/test_agent_run_store.py scripts/tests/test_agent_runtime.py scripts/tests/test_agent_workspace_review.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP30: Run Visibility And Operator UX

**Priority:** Medium

**Goal:** Make retained multi-agent runs easier to inspect, resume, validate,
and promote from the CLI without opening manifest JSON by hand.

**Context:**
- The orchestrator already stores useful run metadata, prompt estimates,
  topology summaries, usage, governance alerts, validation suggestions, and
  workspace diffs.
- Operators need fast answers: what is running, what failed, what can resume,
  what changed, what validation is suggested, and what is safe to promote.
- This package should stay terminal-first. A web UI is not needed for the next
  slice.

**Decision:** Complete. The retained-run CLI now has a dedicated `status`
surface, richer inventory/review/promotion summaries, and documented operator
flow while keeping `--json` machine-readable.

**Work done:**
- Added a `status` command for one retained run with compact task summaries,
      review counts, resumability, governance, and promotion readiness.
- Enriched `inventory` summaries with operator flags plus failed, blocked,
      resumable, costly, and promotion-ready counts.
- Added grouped review summaries for changed files, protected-path violations,
      write-scope violations, dependency materialization modes, and validation
      suggestions.
- Changed dry-run promotion to return an explicit allowed/refused summary with
      blocked reasons instead of only raising.
- Documented the recommended retained-run operator flow in
      `ai/orchestrator/README.md`.

**Tasks:**
- [x] Add or refine a compact `status` view for a single retained run.
- [x] Improve `inventory` output so incomplete, blocked, resumable, costly, and
      promotion-ready runs are obvious in text and JSON modes.
- [x] Add a concise review summary that groups changed files, scope violations,
      protected-path violations, dependency materialization, and validation
      suggestions.
- [x] Add a dry-run promotion summary that clearly explains why promotion is
      allowed or refused.
- [x] Keep stdout machine-readable in `--json` mode and send interactive
      progress/status details to stderr only.
- [x] Document the recommended operator flow from plan validation through run,
      review, validation, promotion, and cleanup.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/cli.py scripts/ai/pojo_lens_agents/governance.py scripts/ai/pojo_lens_agents/path_safety.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/run_store.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/workspace_review.py scripts/tests/test_agent_governance.py scripts/tests/test_agent_path_safety.py scripts/tests/test_agent_provider.py scripts/tests/test_agent_run_store.py scripts/tests/test_agent_runtime.py scripts/tests/test_agent_workspace_review.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 inventory --json`
- `scripts/ai/claude-orchestrator.ps1 prune --older-than-days 14 --dry-run --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP31: Orchestrator Trace And Evaluation

**Priority:** Medium

**Goal:** Make orchestration behavior easier to understand, evaluate, and
improve by adding explicit run-event lineage plus bounded quality/eval surfaces
 for decomposition, handoffs, retries, and approvals.

**Context:**
- The orchestrator already tracks manifests, task summaries, governance, review
  output, and validation outcomes, but it does not yet expose a first-class
  event trace of how a run progressed batch by batch and task by task.
- External agent stacks increasingly expose traces, handoff lineage, and
  workflow-state visibility because debugging multi-agent behavior without them
  is slow and imprecise.
- This package should improve observability and evaluation without replacing the
  current scheduler or weakening manifest-first operator semantics.

**Decision:** Complete. The orchestrator now records run-event lineage plus
branch-context lineage, surfaces compact retained-run trace and branch
summaries, exposes an operator-facing `evaluate-run` quality check, and keeps a
tracked multi-batch fixture that proves the contract.

**Work done:**
- Added manifest-backed run events for run start, ready batches, task
      completion/blocking, parent-task lineage, and run finish.
- Added task-level branch-context ids plus parent-context ids, surfaced them in
      retained task records and dependency handoff text, and summarized them in
      `branchSummary`.
- Added compact `traceSummary` and `branchSummary` rollups to retained-run
      inventory and status output.
- Added `evaluate-run` to score retained runs for over-delegation, optional
      reviewer hops, validation suggestion quality, retry/resume contract
      consistency, and promotion-readiness consistency.
- Added `ai/orchestrator/tasks/example-trace-multibatch.json` plus focused
      regression coverage as the tracked multi-batch trace/lineage fixture.

**Tasks:**
- [x] Add a manifest-backed run-event trace that records run start, ready
      batches, task completion/blocking, lineage via parent task ids, and run
      finish.
- [x] Surface compact trace summaries in retained-run operator views where
      useful without making `--json` noisy or unstable.
- [x] Add branch-local lineage/context identifiers so downstream tasks can tell
      which upstream path produced a summary or reviewed layer.
- [x] Add an evaluator harness for orchestration quality: over-delegation,
      unnecessary reviewer hops, invalid validation suggestions, retry/resume
      correctness, and promotion false positives/negatives.
- [x] Add at least one tracked sample or regression fixture that proves the
      event/lineage contract on a small multi-batch run.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP32: Orchestrator Bench And Evals

**Priority:** Medium

**Goal:** Turn retained orchestration output into something benchmarkable
against itself over time by adding a machine-readable run-quality score surface
plus a small tracked evaluation corpus.

**Context:**
- `evaluate-run` already emits useful checks, but it is still oriented around
  one-off human review rather than trendable scoring.
- Comparing the orchestrator against other multi-agent stacks requires stable
  eval fixtures and compact metrics for pass/warn/fail balance, promotability,
  resumability, and retained execution shape.
- This package should keep the current scheduler and manifest model while
  making orchestration quality measurable.

**Decision:** Complete. The orchestrator now exposes per-run `scoreSummary`,
first-pass benchmark dimensions, a retained-run corpus evaluation command, and
the first tracked eval fixture for regression anchoring.

**Work done:**
- Added `evaluate-run.scoreSummary` with pass/warn/fail counts, score percent,
      and retained run-shape fields.
- Added first-pass benchmark dimensions for decomposition quality, retry
      correctness, review/promotion accuracy, and parallel efficiency.
- Added `evaluate-corpus` to aggregate retained-run score status, average score
      percent, and benchmark-dimension counts across the runtime root.
- Added `ai/orchestrator/tasks/example-eval-readonly-review.json` as the first
      tracked eval-corpus fixture.
- Documented the stable operator-facing benchmark fields in
      `ai/orchestrator/README.md`.

**Tasks:**
- [x] Extend `evaluate-run` with a machine-readable score summary that can be
      compared across retained runs.
- [x] Add at least one tracked eval-oriented sample fixture so the score
      surface has a stable regression anchor.
- [x] Add a retained-run corpus workflow or helper that can evaluate multiple
      runs and report aggregate quality counts.
- [x] Add first-pass benchmark dimensions for decomposition quality, retry
      correctness, review/promotion accuracy, and parallel efficiency.
- [x] Decide which score fields are stable enough to treat as operator-facing
      benchmark outputs in `ai/orchestrator/README.md`.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-eval-readonly-review.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-eval-readonly-review.json --dry-run --json`
- `scripts/ai/claude-orchestrator.ps1 evaluate-run .claude-orchestrator/runs/<run-id> --json`
- `scripts/ai/claude-orchestrator.ps1 evaluate-corpus --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP33: Approval State Machine

**Priority:** Medium

**Goal:** Make review, validation, and promotion gates first-class persisted
run states rather than only follow-on commands over retained manifests.

**Decision:** Complete. Retained runs now derive explicit approval lifecycle
states, `review`/`validate-run`/`promote` persist coordinator checkpoints into
the run manifest, and retained summaries expose approval checkpoint visibility
without moving review or promotion ownership out of the coordinator.

**Work done:**
- Added explicit retained lifecycle states `awaiting_review`,
      `awaiting_validation`, and `awaiting_promotion`, plus a terminal
      `completed` state once promotion is applied or no promotable changes
      remain.
- Persisted `coordinatorReview`, `coordinatorValidation`, and
      `coordinatorPromotion` checkpoint summaries alongside their run-local
      `summary.json` artifacts.
- Surfaced `approvalSummary`, `lifecycleState`, and `lifecycleStateReason` in
      retained manifests plus `status`/`inventory` output.
- Added focused regression coverage for review checkpoint persistence,
      promotion checkpoint persistence, and lifecycle progression across
      review, validation, and promotion.

**Tasks:**
- [x] Add explicit retained states such as `awaiting_review`,
      `awaiting_validation`, and `awaiting_promotion`.
- [x] Persist resumable approval checkpoints and operator decisions in the run
      manifest.
- [x] Keep review/promotion ownership in the coordinator while making the
      interrupt states visible in retained-run summaries.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP34: Trace Export

**Priority:** Medium

**Goal:** Export span-style traces from retained orchestration events so runs
can be compared outside the manifest format.

**Decision:** Complete. Retained runs now export a stable
`pojo-lens-orchestrator-trace/v1` JSON payload through `export-trace`, keeping
the manifest contract unchanged while mapping run, batch, task, validation,
and approval checkpoints into one span graph.

**Work done:**
- Added `pojo_lens_agents.trace_export` as the dedicated export layer so WP34
  does not grow `orchestrator_app` again.
- Added `export-trace` with `--task`, `--out`, `--dry-run`, and `--json`.
- Exported stable run, batch, task, validation, and approval span kinds with
  status, timestamps, task metadata, and retained checkpoint paths.
- Mapped retained event batches plus branch-context dependency lineage into
  parent span ids so downstream analysis can reconstruct run structure and task
  handoffs.
- Added focused regression coverage that proves batch/task lineage plus
  review/validation/promotion checkpoint spans from a retained manifest.

**Tasks:**
- [x] Define a stable export shape for run, batch, task, validation, and
      approval spans.
- [x] Map retained event/branch lineage into parent-child trace relationships.
- [x] Add one CLI export surface that writes trace data without changing the
      core run manifest contract.

---

## WP35: Orchestrator Command Decomposition

**Priority:** High

**Goal:** Split `scripts/ai/claude-orchestrator.py` into focused package
modules so retained-run features stay manageable without changing the CLI or
JSON contracts.

**Decision:** Complete. The compatibility entrypoint now delegates retained-run
summary/lifecycle, review/promote, validation checkpoint persistence, and eval
logic into focused `pojo_lens_agents` modules while preserving the existing
CLI, manifest fields, exit codes, and JSON outputs.

**Work done:**
- Added `pojo_lens_agents.run_summary` for branch/event/approval summaries and
      retained lifecycle derivation.
- Added `pojo_lens_agents.review_ops` for review, patch export, promotion, and
      promotion-readiness helpers.
- Added `pojo_lens_agents.validation_ops` for validation target resolution,
      command aggregation/execution, and coordinator checkpoint persistence.
- Added `pojo_lens_agents.evals` for run-quality scoring and corpus
      aggregation.
- Reduced `scripts/ai/claude-orchestrator.py` to orchestration glue plus thin
      command wrappers around the extracted modules.

**Context:**
- WP28 split out runtime, provider, run-store, governance, path-safety, and
  workspace-review layers, but the top-level orchestrator file still owns too
  much command and retained-run behavior.
- WP30-WP33 added status, inventory, evaluation, approval state, and
  checkpoint persistence, which increases merge pressure and review cost inside
  one large file.
- Further work such as WP34 trace export will be safer if retained-run
  summarization, review/promotion, validation, and evals are isolated behind
  package boundaries.

**Tasks:**
- [x] Extract retained-run summary and lifecycle helpers into a dedicated
      package module.
- [x] Extract `review`, `export-patch`, and `promote` command behavior into a
      focused review/promotion module.
- [x] Extract `validate-run` and checkpoint-persistence helpers into a
      validation module.
- [x] Extract `evaluate-run` / `evaluate-corpus` score helpers into an evals
      module.
- [x] Leave `claude-orchestrator.py` as a thin compatibility entrypoint plus
      CLI wiring.
- [x] Preserve existing CLI arguments, exit codes, manifest fields, and JSON
      output contracts with focused regression coverage.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/*.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/docs/check-doc-consistency.ps1`

---

## Deferred: WP18 JDK 25 Runtime Knob Evaluation

**Priority:** Experimental Runtime Performance

**Goal:** Measure JDK 25 runtime features as deployment guidance rather than as
mandatory code changes.

**Deferred tasks:**
- [ ] Define a small runtime matrix covering default JVM settings, compact
      object headers, generational Shenandoah, and AOT cache startup for the
      benchmark runner and one Spring example app.
- [ ] Execute the matrix with `$env:JAVA_HOME\bin\java.exe` and capture startup
      time, heap footprint, and relevant throughput/parity outputs.
- [ ] Decide which knobs are worth documenting in `docs/benchmarking.md` or
      release guidance, and which should remain experimental notes only.
- [ ] Keep runtime-feature guidance explicitly optional until the data is stable
      across multiple runs and environments.

**Validate when reactivated:**
- `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP36: Orchestrator Run And Planner Decomposition

**Priority:** High

**Goal:** Extract the remaining run, retry/resume, planner, and runtime-admin
control flow out of `scripts/ai/claude-orchestrator.py` so the entrypoint is
mostly CLI wiring plus thin orchestration glue.

**Context:**
- WP35 removed retained-run summaries, review/promotion, validation, and evals,
  but the entrypoint still carries the largest orchestration paths: live run
  scheduling, resume/retry shaping, planner flow, and runtime admin helpers.
- The support burden is now concentrated in the run-control block, which is
  still the hardest part of the file to review and change safely.
- WP34 trace export should sit on top of cleaner run/control-plane boundaries,
  not make the remaining entrypoint bigger again.

**Tasks:**
- [x] Keep `scripts/ai/claude-orchestrator.py` as a thin compatibility shim
      under the 1000-line target by moving the full implementation behind
      package modules.
- [x] Extract `run_loaded_plan`, `run_plan`, `resume_run`, and `retry_run`
      into a dedicated run-ops module.
- [x] Switch extracted `pojo_lens_agents` layers to lazy loading so the
      entrypoint imports modules on demand instead of loading the full split
      stack up front.
- [x] Extract planner prompt/build/invocation flow into a dedicated planner
      module.
- [x] Extract cleanup/prune/runtime inventory helpers into a runtime-admin
      module.
- [x] Extract retained run-record coercion plus branch-context helpers into a
      manifest-records module so runtime admin and review surfaces stop
      rebuilding that logic inline.
- [x] Extract prompt/model/effort shaping plus worker-contract parsing into
      dedicated prompt/worker contract modules.
- [x] Extract task execution/workspace preparation and run manifest
      serialization into dedicated execution/manifest IO modules.
- [x] Keep the entrypoint focused on argparse, dispatch, and high-level glue.
- [x] Continue the split until `pojo_lens_agents.orchestrator_app` itself is
      below the 1000-line target instead of stopping at the 50-line
      compatibility shim.
- [x] Preserve CLI arguments, exit codes, manifest fields, and JSON contracts
      with focused regression coverage.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/*.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP37: Reviewer Findings And Promotion Governance

**Priority:** High

**Goal:** Make reviewer conclusions machine-meaningful so promotion readiness
and retained-run status do not depend on prose-only summaries.

**Context:**
- Real quickstart coding and docs runs showed that reviewers can identify real
  promotion risks while still returning a `completed` task and a human-only
  summary.
- The coordinator currently has no structured way to distinguish "review
  passed" from "review found a material issue" other than manual operator
  reading.
- Promotion already supports task-level selection and coordinator-owned
  checkpoints; this package should strengthen that governance rather than
  replace it with automatic merging.

**Tasks:**
- [x] Extend reviewer worker output with structured findings that include
      severity such as `info`, `warn`, and `block`.
- [x] Persist reviewer findings in task records, review summaries, and
      retained-run manifests without breaking existing JSON contracts more than
      necessary.
- [x] Make promotion readiness and dry-run promotion summaries surface blocking
      reviewer findings explicitly.
- [x] Decide whether blocking reviewer findings should refuse promotion by
      default or require an explicit coordinator override flag, and implement
      the chosen behavior.
- [x] Reflect reviewer finding severity in retained-run `status`,
      `inventory`, `approvalSummary`, and `evaluate-run` where useful.
- [x] Add focused regression coverage for prose-only warnings, blocking review
      findings, selective promotion after mixed review outcomes, and retained
      lifecycle visibility.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/prompt_contracts.py scripts/ai/pojo_lens_agents/worker_contracts.py scripts/ai/pojo_lens_agents/review_ops.py scripts/ai/pojo_lens_agents/run_summary.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/ai/claude-orchestrator.ps1 review .claude-orchestrator/runs/<run-id> --json`
- `scripts/ai/claude-orchestrator.ps1 promote .claude-orchestrator/runs/<run-id> --dry-run --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP38: Docs And Text Quality Guardrails

**Priority:** High

**Goal:** Add coordinator-side safeguards for documentation/text quality so
common low-signal content failures do not get promoted silently.

**Context:**
- The live quickstart docs run produced mojibake in promoted README text even
  though the substantive content was useful.
- Documentation-oriented runs currently rely on general review and generic doc
  checks, but they do not have text-sanity rules specific to AI-authored docs.
- The repo already prefers ASCII edits by default; this package should make
  that preference enforceable for common docs workflows.

**Tasks:**
- [x] Add a text-sanity check for common mojibake and encoding-corruption
      patterns in promoted text files.
- [x] Add a docs-oriented coordinator validation helper that can run against
      selected promoted files or retained workspaces before promotion.
- [x] Decide where ASCII-safe enforcement should apply by default and where
      Unicode is acceptable, then encode that policy in the orchestrator.
- [x] Surface text-quality failures in review, dry-run promotion, and
      retained-run status instead of forcing operators to spot them manually.
- [x] Add regression tests for mojibake detection, ASCII-safe docs behavior,
      and non-doc false-positive avoidance.
- [x] Update operator docs so contributors know when docs/text checks are
      expected in a run plan.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/review_ops.py scripts/ai/pojo_lens_agents/validation_ops.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/docs/check-doc-consistency.ps1`
- `scripts/ai/claude-orchestrator.ps1 promote .claude-orchestrator/runs/<run-id> --dry-run --json`
- `scripts/ai/refresh-ai-memory.ps1`
- `scripts/ai/refresh-ai-memory.ps1 -Check`

---

## WP39: Low-Cost Worker Profiles And Output Discipline

**Priority:** Medium

**Goal:** Reduce token cost and verbosity for small real-world orchestration
proofs without weakening correctness or governance.

**Context:**
- Even `simple` plus `--effort low` runs are still producing large outputs and
  higher-than-desired cost for bounded docs/read-only work.
- The current worker contract already caps fields, but live runs still show
  excessive summaries, notes, and reviewer prose.
- The orchestrator needs a practical "cheap proof" path for repeated live
  validation of multi-agent behavior.

**Tasks:**
- [x] Add leaner docs-oriented implementer/reviewer profiles or prompt modes
      for bounded documentation and read-only tasks.
- [x] Tighten worker output expectations for `summary`, `notes`,
      `followUps`, and reviewer findings where the task shape is small.
- [x] Review whether default task/agent effort should remain `high` for all
      roles or whether selected orchestration profiles should default lower.
- [x] Add retained-run visibility for "unexpectedly verbose" tasks so cost
      debugging is easier.
- [x] Add at least one tracked low-cost live-proof task plan explicitly aimed
      at repeated cheap orchestration validation.
- [x] Add regression coverage for the cheaper profile/contract behavior.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/prompt_contracts.py scripts/ai/pojo_lens_agents/worker_contracts.py scripts/ai/pojo_lens_agents/evals.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/<cheap-proof-plan>.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/<cheap-proof-plan>.json --max-parallel 2 --effort low --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP40: End-To-End Coding Run Reliability

**Priority:** High

**Goal:** Close the remaining gaps between "the orchestrator can run" and "the
orchestrator is dependable for real coding work from plan to promoted repo
state".

**Context:**
- The repo now has real proofs for read-only runs, parallel runs, coding runs,
  selective promotion, and post-promotion validation, but each proof also
  exposed governance or content-quality gaps.
- The remaining work is not one bug. It is an end-to-end reliability pass
  across planning, execution, review, promotion, and validation.
- This package should use tracked real-world examples in this repo rather than
  synthetic plans only.

**Tasks:**
- [ ] Revisit the tracked quickstart coding/doc plans and align them with the
      stronger reviewer and docs guardrails from WP37-WP39.
- [ ] Add at least one clean end-to-end coding proof where implementer output,
      reviewer approval, promotion, and post-promotion validation all succeed
      without manual coordinator patching.
- [ ] Add at least one clean end-to-end docs proof where multiple parallel
      implementers plus a reviewer succeed without leaving unpromoted fixes.
- [ ] Expand `evaluate-run` and/or corpus evaluation to reflect the new
      reviewer-governance and text-quality signals.
- [ ] Decide which retained-run outcomes are release-grade proof points for
      the orchestrator and document them in `ai/orchestrator/README.md`.
- [ ] Update `CHANGELOG.md`, memory state, and tracked validations to reflect
      the final reliability baseline.

**Validate:**
- `py -3 -m unittest scripts.tests.test_claude_orchestrator`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel-implement-review-quickstart.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel-implement-review-quickstart.json --max-parallel 2 --json`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json --max-parallel 2 --json`
- `mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml test`
- `scripts/docs/check-doc-consistency.ps1`
- `scripts/ai/refresh-ai-memory.ps1`
- `scripts/ai/refresh-ai-memory.ps1 -Check`

---

## WP41: Crash-Safe Manifest Flushing

**Priority:** High

**Goal:** Make all manifest writes atomic so a process crash, OOM kill, or
power loss mid-task never leaves a corrupted or partial retained run.

**Decision:** Complete. All orchestrator writes now go through `write_text()`
in `orchestrator_utils.py`, which writes to a unique `.tmp` sibling then
`os.replace()`. `_atomic_replace()` retries on Windows `PermissionError`.
`recover_orphaned_write_temps()` cleans crash-left temps recursively;
`workspace_run_review.py` triggers recovery before loading a run manifest.

**Work done:**
- Added `_atomic_replace()`, `write_text()`, `write_json()`, and
  `recover_orphaned_write_temps()` to `orchestrator_utils.py`; all call
  sites in `task_execution.py`, `review_ops.py`, `validation_ops.py`, and
  `workspace_run_review.py` use the shared helpers.
- `recover_orphaned_write_temps()` scans recursively for `.tmp` siblings
  matching the write-temp naming pattern and removes them with a warning.
- `workspace_run_review.py` calls `recover_orphaned_write_temps()` before
  reading the run manifest so stale temps from prior crashes are cleared.
- Added regression coverage for the atomic write path and orphaned-temp
  recovery sweep; full suite 375 green after WP41.

**Context:**
- Manifests are currently written with direct file writes. A crash during a
  write leaves a partial file; the next `resume`, `status`, or `inventory`
  call then fails to parse it.
- The fix is the standard atomic-write pattern: write to a `.tmp` sibling,
  then `os.replace()` (atomic on both POSIX and Windows NTFS).
- A companion recovery sweep on run load can detect and remove orphaned
  `.tmp` files from a prior crashed write.

**Tasks:**
- [x] Identify every manifest write path in `manifest_io.py`,
      `task_execution.py`, `run_ops.py`, and coordinator checkpoint helpers
      in `review_ops.py` and `validation_ops.py`.
- [x] Replace each with an atomic write-to-temp-then-`os.replace()` helper;
      keep the helper in `manifest_io.py` so all call sites share one
      implementation.
- [x] Add a manifest recovery helper that detects orphaned `.tmp` sibling
      files on run load and removes them after logging a warning.
- [x] Add regression coverage for the atomic write path and the orphaned-temp
      recovery sweep.
- [x] Verify no existing test fixture depends on in-place manifest mutation
      order; update any that do.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/manifest_io.py scripts/ai/pojo_lens_agents/task_execution.py scripts/ai/pojo_lens_agents/run_ops.py scripts/ai/pojo_lens_agents/review_ops.py scripts/ai/pojo_lens_agents/validation_ops.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP42: Within-Run Task Retry

**Priority:** High

**Goal:** Automatically retry failed tasks for transient errors (rate limits,
provider timeouts, subprocess errors) without requiring a manual `retry` run.

**Decision:** Complete. `retry_policy.py` classifies transient failures
(rate-limit 429, timeout, 5xx, overload, SDK typed errors) vs permanent
(scope violation, auth, JSON parse, prompt budget). `execute_task_with_retry`
in `orchestrator_app.py` wraps `execute_task` with exponential backoff
(1s/2s/4s + jitter, capped 30s), records `attempt`/`attempt_errors` in task
records, emits retry events to the trace, and supports `--max-task-retries`
CLI override and `maxRetries` JSON field on task/agent definitions.

**Work done:**
- Added `retry_policy.py` with `classify_failure()`, `compute_backoff()`,
  `DEFAULT_MAX_TASK_RETRIES=3`, and pattern lists for transient vs permanent
  error strings.
- Added `execute_task_with_retry()` in `orchestrator_app.py`; wraps patchable
  `execute_task` dep, records `attempt`/`attempt_errors`/`retry_delay_ms` in
  `TaskRunRecord`, emits `task-retry-attempt` events to run trace.
- Added `--max-task-retries` CLI override on `run`/`resume`/`retry` commands;
  `maxRetries` JSON field on task and agent definitions.
- Retry reuses same workspace and prompt; no re-hydration between attempts.
- Added regression coverage for attempt counting, backoff timing, transient vs
  permanent classification, and max-retry exhaustion; 44 new tests; suite 419.

**Context:**
- Any task failure currently requires the operator to issue a separate `retry`
  run, which creates a new run and loses the original run context.
- Transient failures (Claude rate-limit 429, subprocess timeout, provider 5xx)
  are not logic failures and should not block a run permanently.
- Logic failures (write-scope violation, JSON parse error, protected-path
  violation, blocked dependency) must not be retried automatically.
- Retry state must be visible in the task record and event trace so operators
  can distinguish automatic recovery from a first-attempt success.

**Tasks:**
- [x] Define a transient-error classifier that maps exit codes, exception
      types, and provider error strings to `transient` vs `permanent`; keep
      the classifier in `task_execution.py` or a dedicated `retry_policy.py`.
- [x] Add per-task retry config: `maxRetries` (default `3`), backoff base
      (`1s`/`2s`/`4s` + jitter), and a `retryPolicy` field in agent/task
      definitions; add a `--max-task-retries` CLI override.
- [x] Record `attempt`, `attemptErrors`, and `retryDelayMs` in the task record
      so retry history is visible in `status`, `inventory`, and
      `evaluate-run`.
- [x] Emit a retry-attempt event in the run event trace for each retried
      attempt so `export-trace` captures the full attempt timeline.
- [x] Ensure retry attempts consume the same workspace and prompt; do not
      re-hydrate the workspace between attempts unless the workspace mode
      requires it.
- [x] Add regression coverage for attempt counting, backoff timing, transient
      vs permanent classification, and max-retry exhaustion behavior.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/task_execution.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/run_ops.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP43: Direct Anthropic SDK Provider

**Priority:** Medium

**Goal:** Replace the `claude` subprocess provider with the Anthropic Python
SDK to unlock streaming output, accurate cache hit stats, and SDK-managed
rate-limit handling with clean backoff.

**Decision:** Complete. The orchestrator now supports a dual-provider model:
`POJO_LENS_PROVIDER=sdk` (or auto-detect when `anthropic` importable and
`ANTHROPIC_API_KEY` set) routes tasks through the Anthropic Python SDK with a
bounded agentic tool loop; the original `claude` subprocess path remains the
default fallback. No CLI or manifest contract changed.

**Work done:**
- Added `scripts/ai/pojo_lens_agents/sdk_provider.py` (260 lines): agentic
  tool loop with 4 workspace tools (`read_file`, `write_file`,
  `str_replace_based_edit_tool`, `bash`), path-traversal protection,
  streaming via `client.messages.stream()` when stderr is a TTY, usage
  accumulation across tool-use turns, and full exception capture returning
  error strings so WP42 `classify_failure()` patterns match without changes.
- Added `[project.optional-dependencies] sdk = ["anthropic>=0.40.0"]` to
  `pyproject.toml`.
- Extended `provider_worker.py` with `ensure_provider_available()` that
  validates SDK package + `ANTHROPIC_API_KEY` before SDK runs.
- Updated `task_execution.py`: provider mode detected once per task, command
  artifact written with `providerMode: "sdk"` for manifest consistency, SDK
  result and subprocess result unified into `stdout_text`/`stderr_text`/
  `return_code`/`usage` variables so all downstream logic is shared.
- Updated `orchestrator_app.py`: lazy module proxy for SDK layer, deps wired
  with `provider_mode` and `run_sdk_provider`, `ensure_provider_available`
  replaces `ensure_claude_available` in run entrypoint.
- Added `scripts/tests/test_sdk_provider.py` with 65 tests covering all
  SDK provider paths; total suite 484 tests, all passing.

**Context:**
- `provider.py` and `provider_worker.py` currently invoke `claude` as a
  subprocess and parse stdout JSON. This works but loses: streaming tokens,
  real-time usage/cache stats, SDK-level retry and rate-limit backoff, and
  clean error classification without subprocess exit-code guessing.
- The Anthropic Python SDK (`anthropic`) exposes the same models via
  `client.messages.create()` and `client.messages.stream()`, returns
  structured `Usage` objects, and raises typed exceptions
  (`RateLimitError`, `APITimeoutError`) that map cleanly to WP42's
  transient-error classifier.
- The provider layer is already isolated in `provider.py` /
  `provider_worker.py`; the rest of the orchestrator calls it through a thin
  interface. Swapping the backend should not change any public CLI or manifest
  contract.
- Keep the `claude` subprocess path as a fallback or local-dev mode; select
  the SDK path when `ANTHROPIC_API_KEY` is present.

**Tasks:**
- [x] Add `anthropic` as an optional dependency in `pyproject.toml` with a
      clearly named extras group (e.g., `[sdk]`).
- [x] Implement an SDK-backed provider in `provider.py` that calls
      `client.messages.create()` with the selected model, system prompt, and
      user message; map the response to the existing provider result shape.
- [x] Wire the SDK `Usage` object (input tokens, output tokens, cache read,
      cache write, cost) directly into the task record `usage` field instead
      of parsing subprocess stdout.
- [x] Add a streaming path (`client.messages.stream()`) that emits partial
      text to `stderr` for interactive runs and accumulates the full response
      for JSON parsing; keep `stdout` machine-readable.
- [x] Map `RateLimitError`, `APITimeoutError`, and `InternalServerError` to
      the transient-error classifier from WP42; map `AuthenticationError` and
      `InvalidRequestError` to permanent.
- [x] Keep the `claude` subprocess path active via a provider-mode flag or
      env var; document which mode is used in dry-run and manifest output.
- [x] Add regression coverage for the SDK provider result shape, usage
      mapping, and error classification.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/provider_worker.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-cheap-proof-docs.json --dry-run --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP44: Async Task Execution

**Priority:** Medium

**Goal:** Replace `ThreadPoolExecutor` with `asyncio` subprocess execution
to remove one-OS-thread-per-task overhead, enable partial output streaming,
and align the concurrency model with the SDK-backed provider from WP43.

**Context:**
- The current batch scheduler uses `concurrent.futures.ThreadPoolExecutor`
  with synchronous `subprocess.run()` calls. Each in-flight task burns a
  full OS thread for the entire duration of a Claude invocation, which can be
  minutes. Under `--max-parallel 4`, that is four blocked OS threads doing
  nothing but waiting.
- `asyncio.create_subprocess_exec()` + `asyncio.gather()` handles the same
  concurrency with a single event loop thread and no blocking, enabling 10x+
  more concurrent tasks per core. When combined with the SDK provider from
  WP43, `await client.messages.stream()` fits naturally.
- The batch scheduler logic (topological batching, write-scope conflict
  detection, dependency readiness) does not need to change; only the
  execution transport switches from thread pool to event loop.
- Preserve `--max-parallel` semantics exactly; the semaphore replaces the
  thread pool's `max_workers`.

**Decision:** Full async conversion via `asyncio.Semaphore` + `asyncio.as_completed`; sync CLI entry points preserved with `asyncio.run()` wrapper; SDK provider wrapped with `asyncio.to_thread`; subprocess via `asyncio.create_subprocess_exec`.

**Work done:**
- `provider.py`: Added `async_run_process` / `async_run_subprocess` using `asyncio.create_subprocess_exec` and `asyncio.wait_for(proc.communicate(), ...)`.
- `provider_worker.py`: Added `run_subprocess_async` async wrapper; sync `run_subprocess` kept for planner path.
- `task_execution.py`: `execute_task` and `execute_task_with_retry` converted to `async def`; SDK call wrapped with `asyncio.to_thread`; `time.sleep` → `asyncio.sleep`.
- `run_ops.py`: `run_loaded_plan` converted to `async def`; `ThreadPoolExecutor` + `concurrent.futures.as_completed` replaced with `asyncio.Semaphore` + `asyncio.as_completed` (preserves per-task manifest writes).
- `orchestrator_app.py`: `execute_task` / `execute_task_with_retry` / `_execute_task_with_retry` converted to `async def`; `run_loaded_plan` sync wrapper uses `asyncio.run(run_ops_layer.run_loaded_plan(...))`.
- All test files updated: `fake_execute_task` → `async def`, `fake_run_subprocess` → `async def`, direct `execute_task` / `execute_task_with_retry` calls wrapped in `asyncio.run()`; stale `ThreadPoolExecutor` test scaffolding removed from `test_agent_retry_policy.py`.

**Tasks:**
- [x] Replace `ThreadPoolExecutor` in `run_ops.py` with `asyncio.Semaphore(max_parallel)` + `asyncio.as_completed`.
- [x] Convert task dispatch and provider invocation to `async def`; sync CLI entry points preserved with `asyncio.run()`.
- [x] Replace `subprocess` in `provider.py` with `asyncio.create_subprocess_exec` + `await proc.communicate()`.
- [x] Ensure `--max-parallel` still caps concurrency via semaphore.
- [x] Update all test files to use async fakes and `asyncio.run()` wrappers.
- [ ] Stream partial stdout lines to `stderr` during interactive runs (deferred — needs streaming API work separate from async transport).

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/run_ops.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/task_execution.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP45: OpenTelemetry Observability

**Priority:** Medium

**Decision:** Complete. The orchestrator now reuses the retained
`pojo-lens-orchestrator-trace/v1` graph to emit standard OTEL spans to any
OTLP HTTP collector when enabled through `OTEL_EXPORTER_OTLP_ENDPOINT` or a
`--otel-endpoint` override on `run`, `resume`, `retry`, or `export-trace`.

**Work done:**
- Added optional `[otel]` dependencies in `pyproject.toml` for
  `opentelemetry-sdk` and OTLP HTTP export.
- Added `pojo_lens_agents.otel_spans`, which maps the retained custom span
  graph onto OTEL span names, timestamps, status codes, attributes, and links.
- Wired live `run`, `resume`, and `retry` to emit OTEL spans after the final
  manifest write so OTEL export matches retained run state.
- Wired `export-trace` to optionally send the retained span graph to an OTLP
  collector in addition to writing the JSON export.
- Added task OTEL attributes for `model`, `modelProfile`, `effort`,
  `outputProfile`, `usage.totalCostUsd`, `usage.inputTokens`,
  `usage.outputTokens`, and `usage.cacheReadTokens`.
- Added regression coverage for OTEL span-plan construction, status mapping,
  parent-plus-link translation, dry-run behavior, and CLI flag parsing.

**Tasks:**
- [x] Add `opentelemetry-sdk` and `opentelemetry-exporter-otlp-proto-http`
      as optional dependencies in `pyproject.toml` under an `[otel]` extras
      group.
- [x] Add an `otel_spans.py` module that wraps the existing `trace_export`
      span structure and emits OTEL spans; activate it when
      `OTEL_EXPORTER_OTLP_ENDPOINT` is set.
- [x] Map existing span kinds to OTEL span names: `run` -> `orchestrator.run`,
      `batch` -> `orchestrator.batch`, `task` -> `orchestrator.task`,
      `validation` -> `orchestrator.validation`, `approval` ->
      `orchestrator.approval`.
- [x] Attach task record fields as span attributes: `model`, `modelProfile`,
      `effort`, `outputProfile`, `usage.totalCostUsd`, `usage.inputTokens`,
      `usage.outputTokens`, `usage.cacheReadTokens`.
- [x] Emit span status `ERROR` for `failed`/`blocked` task outcomes and
      `OK` for `completed`; keep `UNSET` for pending or unknown.
- [x] Add a `--otel-endpoint` CLI flag that overrides the env var for
      one-off runs; document both in `ai/orchestrator/README.md`.
- [x] Add regression coverage for span construction correctness and
      attribute mapping without requiring a live OTEL collector.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/trace_export.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 export-trace .claude-orchestrator/runs/<run-id> --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP46: Typed Agent Contracts

**Priority:** Low

**Decision:** Complete. The orchestrator now uses Pydantic v2 at its major
contract boundaries while preserving the existing CLI JSON, retained manifest,
and dataclass compatibility contracts.

**Work done:**
- Added `pydantic>=2.7,<3` as a core tooling dependency and published
  `py.typed` for `pojo_lens_agents`.
- Added `pojo_lens_agents.orchestrator_models` with typed models for task
  plans, agent definitions, run policies, prompt-budget structures, validation
  intents, reviewer findings, dependency outputs, dependency layers,
  coordinator checkpoints, task records, and retained run manifests.
- Converted the existing orchestrator contract dataclasses to
  Pydantic-backed dataclasses so internal construction now benefits from
  Pydantic validation/coercion while existing `dataclasses.asdict` consumers
  continue to work.
- Wired typed validation through run-policy parsing, agent loading, task-plan
  loading, task-record coercion, manifest construction, and coordinator
  checkpoint persistence.
- Added model regression coverage for plan round-trips, invalid task ids,
  generated manifest validation, and retry attempt field preservation.
- Added package-level mypy configuration and validation. Intentionally dynamic
  legacy wrapper modules remain excluded from strict checking while the new
  typed contract layer and migrated boundaries are checked.

**Goal:** Introduce Pydantic models at the major orchestrator call boundaries
to replace large dict passing, make contracts self-documenting, and catch
shape mismatches at the type layer rather than at runtime.

**Context:**
- Many orchestrator functions accept 50+ parameter dicts (`deps`, task dicts,
  agent dicts, plan dicts) because the initial layering extracted behavior
  without introducing typed boundaries. This makes type-checking, IDE
  navigation, and safe refactoring harder than it should be.
- The highest-value boundaries are: `TaskPlan`, `AgentDef`, `TaskRecord`,
  `RunManifest`, `OutputProfile`, and `RunPolicy`. These are already
  implicitly typed through JSON schema validation; making them explicit
  Pydantic models would surface discrepancies at parse time.
- This is a pure internal refactor. No CLI arguments, manifest fields, JSON
  output contracts, or test fixtures should change.

**Tasks:**
- [x] Define Pydantic v2 models for `TaskPlan`, `TaskDef`, `AgentDef`,
      `RunPolicy`, `OutputProfile`, and `RunManifest` in a new
      `orchestrator_models.py` module; derive them from the existing JSON
      schema definitions in `orchestrator_contracts.py`.
- [x] Define `TaskRecord`, `DependencyOutput`, and `CoordinatorCheckpoint`
      Pydantic models and use them as the output type of task execution and
      checkpoint persistence helpers.
- [x] Migrate the main plan, execution, run, and manifest boundaries to typed
      contract objects; keep intentionally dynamic helper injection dicts
      where the migration cost exceeds the benefit.
- [x] Fence off the remaining dynamic `deps` helper modules in mypy config
      while the new typed contract layer and migrated JSON boundaries are
      checked package-wide.
- [x] Add `py.typed` marker to `pojo_lens_agents` so downstream consumers
      benefit from type checking.
- [x] Run `mypy` or `pyright` over `pojo_lens_agents` after migration; fix
      all errors before marking complete.
- [x] Add regression coverage that the Pydantic models round-trip correctly
      through the existing JSON manifest fixtures.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/*.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP47: Human-in-the-Loop Approval Gates

**Priority:** High

**Decision:** Complete. Runs can now pause at batch boundaries through
tracked `runPolicy` fields or explicit CLI overrides, persist the manifest at
the gate, and continue or abort based on operator approval.

**Work done:**
- Added `RunPolicy.hitl` and `RunPolicy.hitl_mode` with modes `none`,
  `batch`, `on-failure`, and `always`; serialization and Pydantic validation
  preserve the public `hitl` / `hitlMode` JSON fields.
- Added `--hitl`, `--hitl-mode`, and `--hitl-auto-approve` to `run` and
  `resume`; `--hitl-mode` alone does not enable gates.
- Added `pojo_lens_agents.hitl` for policy resolution, trigger decisions,
  sentinel-file writing, interactive stdin approval, and auto-approval.
- `run_ops.run_loaded_plan` now emits `hitl-gate`, `hitl-approved`, and
  `hitl-aborted` events after batch completion, writes the manifest before
  waiting, and blocks pending tasks with a clear summary when a gate aborts.
- Added regression tests for CLI parsing, policy serialization, auto-approved
  gate events, and abort behavior that blocks pending work.

**Goal:** Allow the operator to pause a run at any batch boundary, inspect
workspace diffs and task outputs, then approve or reject continuation before
the next batch dispatches — closing the gap between post-hoc review and
mid-run oversight.

**Context:**
- The current review/promotion flow is post-hoc: all tasks run, then the
  operator reviews the full run before promoting. This is fine for read-only
  or low-risk docs runs, but for coding runs with many tasks the risk
  accumulates across the whole DAG before any human sees it.
- Tools like LangGraph supervisor patterns, Temporal workflow signals, and
  Claude Code's multi-agent interrupt model all support mid-run escalation.
  The gap is not that we lack approval state (WP33 added that) but that there
  is no mechanism to pause before the next batch and ask the operator.
- The right boundary is the batch: after each batch completes, before the
  next `batch-ready` event fires, allow an optional gate that blocks until
  the operator explicitly continues, aborts, or modifies the plan.
- This does not require a UI — a CLI prompt or a sentinel file the operator
  touches is sufficient for the repo-local case.

**Tasks:**
- [x] Add `hitl` and `hitl_mode` fields to `RunPolicy`: `"none"` (default),
      `"batch"` (pause after every batch), `"on-failure"` (pause after any
      failed task), `"always"` (pause after first batch only).
- [x] Add `--hitl` CLI flag to `run` and `resume` commands; add a
      `--hitl-mode` option defaulting to `"batch"`.
- [x] In `run_ops.run_loaded_plan`, after each batch result loop, check
      `hitl` policy; if triggered, emit a `hitl-gate` run event, write
      current manifest, and block on operator input (stdin prompt or sentinel
      file at `run_dir/hitl-gate.lock`).
- [x] Add `hitl-approved` and `hitl-aborted` lifecycle events to the event
      trace so retained runs record where operator gates fired.
- [x] Support `--hitl-auto-approve` for unattended CI runs that set the flag
      but want HITL gates to pass silently (for testing gate logic without
      blocking).
- [x] Add regression coverage for gate emission, manifest state at gate, and
      continue/abort paths without requiring interactive stdin.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --hitl --hitl-auto-approve --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP48: Pre-Flight Cost Estimation

**Priority:** High

**Goal:** Estimate token spend and USD cost from a plan before the run starts,
based on plan topology, model selection, effort tiers, and prompt budgets, so
the operator knows the expected bill before committing.

**Context:**
- Every modern LLM platform (Vercel AI Gateway, LangSmith, OpenAI usage
  estimators) surfaces cost prediction before dispatch. The orchestrator has
  excellent post-run cost tracking (`totalCostUsd`, per-task usage records,
  run governance limits) but no pre-run estimate.
- The inputs needed for a useful estimate already exist: model per task (from
  `modelProfile`), effort tier (maps to approximate context and output tokens
  per turn), task count, number of agentic turns (bounded by tool loop), and
  prompt budget size. A simple linear model over these produces estimates
  accurate to ±30%, which is enough for "will this cost $1 or $100?".
- The planner already exposes a `--dry-run` path that skips Claude invocation.
  Pre-flight estimation can run in the same path and emit cost estimates to
  the dry-run JSON payload without needing a live API call.
- `run_budget_usd` in `RunPolicy` already lets operators cap spend. Pre-flight
  should warn when the estimate exceeds the declared budget before the first
  task fires.

**Work done:**
- Added `scripts/ai/pojo_lens_agents/cost_estimation.py` with
  `estimate_task_cost(...)` and `estimate_plan_cost(...)`, a tracked pricing
  loader for `ai/orchestrator/model-pricing.json`, canonical model-alias
  handling, effort/profile heuristics, per-batch rollups, and wall-clock
  ranges.
- Wired `costEstimate` into `validate --json`, `run --estimate --json`,
  `run --dry-run --json`, live `run` payloads, and retained manifests; dry
  runs upgrade from heuristic prompt sizing to observed prompt estimates after
  prompt assembly.
- Added validate-time budget warnings when `runPolicy.runBudgetUsd` is already
  below the minimum pre-flight estimate.
- Added `--estimate` to `run` so operators can price a selected plan without
  creating a retained run or invoking Claude.
- Added regression coverage for pricing-table loading, alias canonicalization,
  estimate arithmetic, validate warnings, `run --estimate`, parser support,
  and retained-manifest cost-estimate persistence.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --estimate --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP49: Dynamic Plan Mutation

**Priority:** Medium

**Goal:** Let the coordinator consume task `followUps` at runtime to inject
new tasks or modify the pending DAG mid-run without restarting, closing the
gap between fixed-DAG execution and plan-act-reflect loops.

**Context:**
- Workers already emit `followUps` in their JSON output (tracked in
  `TaskRunRecord`). Today these are purely informational and visible in the
  run summary, but the coordinator never acts on them.
- In practice, a coding run often discovers mid-task that an additional
  change is needed (a test file, a missing migration, a second module touched
  by the first). Without dynamic mutation the operator has to start a new run
  from scratch for the follow-up work.
- The safe boundary for mutation is the same as HITL: between batches, after
  a completed task emits follow-ups and before the next `batch-ready` fires.
  New tasks injected mid-run must still satisfy the write-scope conflict model
  and receive dependency wiring to the emitting task.
- Mutation scope must be bounded: only the current run's pending queue may
  change; retroactive task changes and re-runs of completed tasks are out of
  scope for this WP.

**Work done:**
- Added `RunPolicy.followUpBehavior` with `ignore` (default) and `inject`,
  plus `--follow-up-mode` on `run` and `resume`.
- Added structured worker `followUpTasks` alongside informational
  `followUps`; workers now have a typed path for proposing new runtime tasks.
- `run_ops.run_loaded_plan` now consumes completed-task `followUpTasks`
  between batches, validates each proposal through the shared task-definition
  loader, forces a dependency edge from the emitting task, re-runs graph and
  scope-contract validation, and injects accepted tasks into the pending DAG.
- Accepted injections emit `task-injected` events, rejected proposals emit
  `task-injection-rejected`, and injected tasks persist `injectedFrom` lineage
  in both task records and the run-local `selected-plan.json` snapshot.
- Same-run `resume` continues from that mutated `selected-plan.json`, so
  injected follow-up tasks survive later resumes without rebuilding the plan
  by hand.
- Regression coverage now locks the `ignore` default, injection behavior,
  selected-plan persistence, worker-result normalization for `followUpTasks`,
  and CLI parsing for `--follow-up-mode`.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --follow-up-mode inject --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP50: Rate-Limit-Aware Proactive Scheduling

**Priority:** Medium

**Goal:** Track rolling token consumption per time window and pre-throttle
task dispatch when approaching Anthropic quota limits, replacing the current
purely reactive retry-on-429 model with a smoother submission curve.

**Context:**
- The current model is: submit task → if 429 → retry with exponential backoff
  (WP42). This works but produces bursty submission patterns that generate
  unnecessary 429s and waste wall-clock time on backoff delays.
- Anthropic's rate limits are expressed as tokens per minute (TPM) and
  requests per minute (RPM). Both are knowable in advance from the model
  tier and the account limit tier.
- The orchestrator already tracks `usage.inputTokens` + `usage.outputTokens`
  per task record. Adding a rolling window over recent task completions
  produces a running TPM estimate. When projected consumption for the next
  batch exceeds the window budget, the scheduler should delay dispatch rather
  than submit and absorb a 429.
- This is especially valuable for large parallel runs (`--max-parallel 4+`)
  where simultaneous task completions spike output token counts.

**Tasks:**
- [ ] Add a `RateLimitBucket` abstraction in `run_ops.py` (or a new
      `rate_limiter.py`) that tracks sliding-window token and request counts
      with configurable `tpm_limit` and `rpm_limit` capacities.
- [ ] Read `ANTHROPIC_TPM_LIMIT` and `ANTHROPIC_RPM_LIMIT` env vars (with
      sane defaults per model tier) to initialize the bucket; allow
      `--tpm-limit` / `--rpm-limit` CLI overrides.
- [ ] In `run_ops._run_one`, before acquiring the semaphore, check the rate
      bucket; if the projected next-task cost would exceed the window, sleep
      until the window refills.
- [ ] Track per-task token cost before dispatch using the cost estimation
      module from WP48 (or a simpler heuristic if WP48 is not yet done).
- [ ] Emit `rate-throttle` run events when the scheduler voluntarily delays
      a task dispatch due to budget proximity; record delay duration.
- [ ] Add regression coverage for throttle logic, window refill, and the
      `rate-throttle` event without requiring live API calls.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP51: Cross-Run Memory and Pattern Learning ✅ 2026-05-02

**Priority:** Medium

**Goal:** Persist a structured ledger of outcomes, failure patterns, and
successful decompositions across runs so the planner can consult prior
evidence when generating new plans for similar tasks on the same codebase.

**Context:**
- Today every run starts from scratch. The planner has no memory of which
  task decompositions worked well, which agents struggled with which module
  paths, or which read-path declarations were too narrow and needed expanding.
- LangGraph, Microsoft Foundry AgentDB, and similar frameworks all provide
  some form of persistent cross-run memory. The gap is not retrieval
  infrastructure (the repo already has `ai/indexes/cold-memory.db` from the
  memory system) but a structured run-quality ledger that the planner can
  query.
- The safest initial form is a tracked `ai/state/run-ledger.jsonl` where each
  completed run appends a compact record: plan name, task count, per-task
  status, failure kinds, high-cost tasks, reviewer blocks, and key module
  paths. The planner prompt can then load the last N ledger entries for the
  same plan prefix and surface patterns as planning context.
- This is deliberately shallow: it is a ledger, not a vector store. The goal
  is to give the planner concrete evidence about this codebase, not to build
  a general RAG system.

**Tasks:**
- [x] Define a compact `RunLedgerEntry` schema: run id, plan name, generated
      at, task count, per-task `{id, status, failureKind, costUsd, modules}`,
      reviewer block count, and a brief `plannerNotes` string the coordinator
      can optionally emit.
- [x] Append a `RunLedgerEntry` to `ai/state/run-ledger.jsonl` at the end of
      every `run_loaded_plan` call (both live and dry-run); keep the file
      tracked in git as part of AI state.
- [x] Add `--ledger-context N` flag to `plan` command; when set, load the
      last N ledger entries matching the same plan name prefix and inject a
      compact "prior run evidence" section into the planner prompt.
- [x] Add `summarize-ledger` subcommand that prints a human-readable summary
      of ledger entries (success rate, average cost, common failure kinds,
      high-cost tasks by module) for a given plan name or date range.
- [x] Prune ledger entries older than 90 days in `cleanup` command to keep
      the tracked file bounded.
- [x] Add regression coverage for ledger append, entry schema validation,
      pruning, and planner context injection.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --json`
- `scripts/ai/claude-orchestrator.ps1 summarize-ledger --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP52: Diff-Aware Incremental Replay

**Priority:** Medium

**Goal:** On `resume` or `retry`, skip tasks whose inputs are identical to a
prior successful execution and reuse the existing task record, cutting
unnecessary re-execution after partial failures.

**Context:**
- The current `resume` command re-runs all non-completed tasks. The `retry`
  command re-runs all failed tasks. Neither checks whether the task's inputs
  (prompt, read-path contents, dependency outputs) have actually changed
  since the prior run. For large plans where one task fails late, this means
  re-running all the preceding tasks unnecessarily.
- Content-addressed task fingerprinting is standard in build tools (Gradle
  build cache, Bazel remote cache) and increasingly in LLM pipelines. The
  idea is identical: hash the task's deterministic inputs; if the hash matches
  a prior successful record, skip execution and reuse the result.
- Task input fingerprint components: task prompt text, read-path file contents
  (SHA-256 of each declared `readPaths` file), resolved agent definition hash,
  dependency output summaries, and model+effort selection. These are all
  available before task dispatch.
- Fingerprint reuse must be opt-in (`--reuse-unchanged`) to avoid unexpected
  skips in the default path.

**Tasks:**
- [ ] Add `compute_task_fingerprint(task, agent, plan, dep_records,
      workspace_root)` in a new `task_fingerprint.py` module; hash prompt,
      sorted read-path file contents, agent JSON, dep summaries, and model
      selection into a stable SHA-256 hex string.
- [ ] Store `fingerprint` and `fingerprintInputs` in `TaskRunRecord` and
      persist them in the manifest; existing records without a fingerprint
      are treated as uncacheable.
- [ ] In `run_ops.run_loaded_plan`, when `reuse_unchanged=True`, check if a
      prior record for the task exists with a matching fingerprint and
      `status="completed"`; if so, emit a `task-reused` run event and skip
      dispatch.
- [ ] Add `--reuse-unchanged` flag to `run`, `resume`, and `retry` commands.
- [ ] Add `--fingerprint-only` flag to `validate` that computes and prints
      task fingerprints without running, useful for debugging cache misses.
- [ ] Add regression coverage for fingerprint stability, cache hits, cache
      misses on prompt/read-path changes, and the `task-reused` event.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --reuse-unchanged --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP53: CLI Ergonomics

**Priority:** Medium

**Goal:** Eliminate per-run flag repetition with a repo-local config file and
add a `--watch` live progress formatter so long runs show task progress as it
happens instead of producing output only at completion.

**Context:**
- Every run today repeats the same flags: `--runtime-root`, `--claude-bin`,
  `--max-parallel`, `--continue-on-error`. A `pojolens-agents.toml` in the
  repo root provides sensible defaults so the daily operator invocation shrinks
  from a 6-flag command to just the subcommand and plan path.
- WP44 converted task execution to `asyncio`, so task completions already
  arrive incrementally. The missing piece is a formatter that emits a
  human-readable progress line to stderr as each `task-finished` event fires,
  making long coding runs observable without polling the manifest.
- Both improvements are additive and do not touch any existing JSON contracts,
  manifest format, or test fixtures.

**Tasks:**
- [ ] Define a `[defaults]` section in `pojolens-agents.toml` covering:
      `runtime_root`, `claude_bin`, `max_parallel`, `continue_on_error`,
      `dry_run`, `worker_validation_mode`. Load it from the repo root (or
      `POJOLENS_CONFIG` env var) before argparse defaults; explicit CLI flags
      still override config values.
- [ ] Add `config_loader.py` in `pojo_lens_agents` to read and validate the
      TOML; surface clear errors for unknown keys or wrong value types.
- [ ] Add `--config` global flag to override the config file path; add
      `config show` subcommand that prints resolved config as JSON.
- [ ] Add `--watch` flag to `run`, `resume`, and `retry`; when set, stream
      a one-line progress update to stderr for each `task-finished`,
      `task-retry`, `batch-ready`, and `run-finished` event as it is emitted
      from `run_loaded_plan`.
- [ ] Format watch lines as: `[HH:MM:SS] task-id  status  cost  summary…`
      (truncated to terminal width); write to stderr so `--json` stdout
      piping is unaffected.
- [ ] Add regression coverage for config loading, flag override precedence,
      unknown key rejection, and watch event formatting.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 config show --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --watch --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP54: TUI Dashboard

**Priority:** Medium

**Goal:** A live `textual`-based terminal dashboard that replaces staring at
a blank terminal during long runs: task status grid, rolling cost counter,
active-task log tail, and key bindings for HITL gate approval.

**Context:**
- WP44 made execution async and WP53 adds a `--watch` line formatter as the
  fallback for CI/pipes. WP54 is the interactive upgrade: a proper TUI that
  updates in place rather than scrolling lines.
- `textual` is the right library — it handles resize, mouse, and key events,
  runs on Windows/macOS/Linux, and does not require a special terminal. It
  composes well with the existing asyncio event loop from WP44.
- The dashboard should be opt-in via a `--tui` flag (or auto-detected when
  stdout is a TTY and `textual` is installed); `--watch` and `--json` remain
  the non-TUI paths for CI and piping.
- Key panels: task grid (id, status, model, cost, elapsed), run summary bar
  (total cost, tasks done/total, elapsed), active-task log pane (last N lines
  of the running task's stderr), and a status footer with key bindings.
- WP47 HITL gate approval maps naturally to a TUI prompt: when a gate fires
  the footer switches to `[a] approve  [x] abort` and the run waits for input.

**Tasks:**
- [ ] Add `textual>=0.60` as an optional dependency in `pyproject.toml` under
      a `[tui]` extras group; gate import behind `try/except ImportError` so
      missing `textual` degrades to `--watch` with a warning.
- [ ] Add `tui_app.py` in `pojo_lens_agents` with a `OrchestratorApp(App)`
      class; panels: `TaskGrid` (DataTable), `RunSummaryBar` (Static),
      `LogPane` (RichLog), `FooterBar` (Footer with bindings).
- [ ] Wire `tui_app.py` into the `run_loaded_plan` async loop via a shared
      asyncio `Queue`; run events (`task-finished`, `task-retry`,
      `batch-ready`, `run-finished`) post to the queue; the TUI worker
      consumes them and updates widgets without blocking task dispatch.
- [ ] Update `TaskGrid` on each `task-finished` event: status cell color
      (`green` completed, `red` failed, `yellow` running, `dim` pending),
      cost column, elapsed time.
- [ ] Add `LogPane` that tails the active task's stderr file path from the
      task record; refresh every 500 ms while the task is running.
- [ ] When WP47 HITL gate fires, post a `hitl-gate` message to the TUI;
      `FooterBar` switches bindings to `[a] approve  [x] abort`; keypress
      resolves the gate future and run continues or aborts.
- [ ] Add `--tui` flag to `run`, `resume`, and `retry`; auto-enable when
      stderr is a TTY and `textual` is importable unless `--watch` or `--json`
      is set.
- [ ] Add regression coverage for queue message routing and widget state
      updates using `textual`'s built-in test harness (no live terminal
      required).

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --tui`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP55: Guided Wizard Mode

**Priority:** Medium

**Goal:** Make `pojolens-agents` usable without reading the docs — a no-args
interactive wizard that walks the operator through the full lifecycle from
plan selection to promotion, chaining all commands with human-friendly prompts.

**Context:**
- The full operator workflow is: `validate → run → review → promote`. Each
  step is a separate command with its own flags. A new user has to read the
  README to know what to call in what order and with which options.
- The wizard collapses this into a single guided session: "which plan?",
  "dry run first?", "max parallel?", then executes, shows the outcome, and
  asks "promote?" — no command knowledge required.
- `pojolens-agents` with no arguments (or a `wizard` subcommand) enters the
  wizard. It uses `textual` from WP54 when available for a full TUI wizard,
  falling back to simple `rich`-formatted `input()` prompts when `textual`
  is not installed so it works in any terminal.
- The wizard does not replace the existing subcommands — it wraps them.
  Operators who know the commands keep using them directly; the wizard is for
  occasional users and onboarding.
- The wizard itself has no LLM calls for pure UI flow. One optional exception:
  a natural-language entry point ("describe what you want to do") where Claude
  interprets intent and matches or generates a plan. This step uses
  `claude-haiku-4-5` (cheapest, lowest latency) since the reasoning is simple
  — intent parsing, not code generation. All heavy work still runs through
  the normal worker agents at their declared model profiles.
- Inventory of prior runs, resume/retry suggestions, and cost estimates
  (WP48 if done) should surface naturally in the wizard flow so the operator
  can make informed choices without manually querying.

**Tasks:**
- [ ] Add `wizard.py` in `pojo_lens_agents`; entry point: `pojolens-agents`
      with no subcommand (or explicit `wizard` subcommand).
- [ ] Step 1 — Plan selection: list `ai/orchestrator/tasks/*.json` plans with
      name/goal previews; operator picks one or provides a path.
- [ ] Step 2 — Pre-flight: show task count, agent profiles, and cost estimate
      (WP48 if available); ask "dry run first?" and "max parallel?".
- [ ] Step 3 — Run: execute with TUI (WP54 if available) or `--watch` output;
      display run summary on completion (status counts, total cost, duration).
- [ ] Step 4 — Review gate: if any tasks completed with workspace changes, ask
      "review changes?" and invoke `review` command inline; show reviewer
      findings summary.
- [ ] Step 5 — Promote gate: if review passed (or no reviewer tasks), ask
      "promote to repo?" with a diff summary; invoke `promote` on confirm,
      skip on deny.
- [ ] Step 6 — Validation: after promotion, offer "run post-promotion
      validation?" and invoke `validate-run` inline; report pass/fail.
- [ ] Step 7 — Done: print a compact run receipt (run id, promoted files,
      cost) and exit.
- [ ] If any step fails (run failure, reviewer block, promotion rejection),
      surface the error clearly and offer relevant next steps: "retry failed
      tasks?", "open run dir?", "view manifest?".
- [ ] Add optional natural-language entry: `pojolens-agents "add pagination to
      the employee endpoint"` → `claude-haiku-4-5` call that matches intent to
      an existing tracked plan or generates a minimal task plan; operator
      confirms before execution. Model hard-coded to haiku — never escalates.
- [ ] Add `--resume` and `--retry` wizard entry points that skip to the
      appropriate step for an existing run id.
- [ ] Add regression coverage for wizard step sequencing, abort paths, and
      flag forwarding to underlying commands.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 wizard --dry-run --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP56: Run Completion Notifications

**Priority:** Medium

**Goal:** Notify the operator when a run finishes so they do not have to watch
the terminal — desktop notification, webhook POST, or Slack message with run
status and cost summary.

**Context:**
- Runs take 10-30 minutes. Watching a terminal is not viable. Right now the
  only signal is the process exit or `--watch` output. A notification fired
  on `run-finished` closes this gap with minimal architecture: hook into the
  existing event at the end of `run_loaded_plan` and dispatch based on config.
- Three channels cover the main use cases: desktop (solo developer, immediate
  feedback), webhook (CI/CD integration, post to any HTTP endpoint), Slack
  (team awareness). All three are opt-in via `pojolens-agents.toml` or env
  vars; no channel is required.
- Notification payload: run id, status (`completed`/`failed`/`blocked`),
  task counts, total cost, duration, and a one-line summary. Small enough to
  fit in a Slack message or desktop toast.

**Tasks:**
- [ ] Add `[notifications]` section to `pojolens-agents.toml` schema (WP53);
      fields: `desktop = true/false`, `webhook_url`, `slack_webhook_url`,
      `notify_on = ["success", "failure", "always"]`.
- [ ] Add `notify.py` in `pojo_lens_agents` with three dispatcher functions:
      `notify_desktop(payload)` via `plyer` (optional dep), `notify_webhook(
      url, payload)` via `urllib.request` (no extra dep), `notify_slack(url,
      payload)` formatting a Slack Block Kit message with status colour.
- [ ] Call `notify.py` dispatchers at the end of `run_loaded_plan` after the
      `run-finished` event is emitted; run in a background thread so a slow
      webhook does not delay process exit.
- [ ] Add `--notify` CLI flag to `run`, `resume`, and `retry` that enables
      desktop notification for that invocation without needing config file
      changes; `--no-notify` suppresses config-file notifications for one run.
- [ ] Add `plyer` as an optional dependency in `pyproject.toml` under a
      `[notifications]` extras group; degrade gracefully if not installed.
- [ ] Add regression coverage for payload construction and dispatcher
      routing without requiring live network calls (mock `urllib.request`).

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --notify --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP57: Human Diff View Before Promote

**Priority:** Medium

**Goal:** A `diff-run <run-id>` command that renders git-style file diffs of
workspace changes vs the live repo so the operator sees exactly what workers
changed before deciding to promote.

**Context:**
- The current promote flow shows an AI-generated reviewer summary but no
  literal file diffs. To see what actually changed the operator has to
  navigate to the workspace directory manually and run `git diff` themselves.
- A `diff-run` command closes this: read the task records from the manifest,
  find each task's workspace, diff touched files against the repo counterpart,
  and render the output with `rich` syntax highlighting. The wizard (WP55)
  can call it inline at the promote gate.
- This is read-only and zero-risk — it never touches the repo. It just
  presents the same information `promote --dry-run` already has, but in a
  human-readable diff format rather than a JSON payload.
- Selective diff by task id or file path covers the most common workflow:
  "show me just what the implementer changed in `src/`".

**Tasks:**
- [ ] Add `diff_run.py` in `pojo_lens_agents`; read manifest, iterate task
      records, collect `actualFilesTouched` paths, diff each workspace file
      against the repo counterpart using `difflib.unified_diff`.
- [ ] Render diffs with `rich` syntax highlighting: red for deletions, green
      for additions, dim for context lines; group by task then by file.
- [ ] Add `diff-run` subcommand: `pojolens-agents diff-run <run-id-or-path>
      [--tasks task-a,task-b] [--paths src/**] [--stat]`.
- [ ] `--stat` flag prints a compact summary (files changed, insertions,
      deletions per task) without full diff body, matching `git diff --stat`.
- [ ] `--json` flag emits structured diff payload: per-file unified diff
      strings, line counts, and task attribution for machine consumption.
- [ ] Wire `diff-run` into the wizard (WP55) promote gate: show `--stat`
      output and offer "full diff?" before the promote prompt.
- [ ] Add regression coverage for diff rendering, stat computation, task and
      path filtering, and missing workspace graceful handling.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 diff-run .claude-orchestrator/runs/<run-id> --stat --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## Release Gate

**Priority:** High

**Goal:** Cut the next release after the active roadmap queue is complete and
the final release guardrails are rerun.

**Tasks:**
- [ ] Run final release guardrails from `RELEASE.md`.
- [ ] Update `ai/state/current-state.md` and `ai/state/handoff.md` after
      release.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `mvn -B -ntp -Pstatic-analysis verify -DskipTests`
- `scripts/docs/check-doc-consistency.ps1`
- Release benchmark guardrails from `docs/benchmarking.md`.

