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
| WP41| Crash-Safe Manifest Flushing         | Planned | Atomic manifest writes via write-to-temp-then-rename so a process crash never corrupts a retained run |
| WP42| Within-Run Task Retry                | Planned | Automatic per-task retry with exponential backoff for transient failures (rate-limit, timeout, provider error) |
| WP43| Direct Anthropic SDK Provider        | Planned | Replace `claude` subprocess provider with the Anthropic Python SDK to unlock streaming, accurate cache stats, and SDK-managed rate-limit handling |
| WP44| Async Task Execution                 | Planned | Replace `ThreadPoolExecutor` with `asyncio` subprocess execution to remove one-thread-per-task overhead and enable streaming |
| WP45| OpenTelemetry Observability          | Planned | Emit standard OTEL spans from existing trace events so runs can plug into Grafana, DataDog, or Jaeger without a custom converter |
| WP46| Typed Agent Contracts                | Planned | Introduce Pydantic models at major call boundaries to replace large dict passing and catch contract violations at the type layer |
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

**Context:**
- Manifests are currently written with direct file writes. A crash during a
  write leaves a partial file; the next `resume`, `status`, or `inventory`
  call then fails to parse it.
- The fix is the standard atomic-write pattern: write to a `.tmp` sibling,
  then `os.replace()` (atomic on both POSIX and Windows NTFS).
- A companion recovery sweep on run load can detect and remove orphaned
  `.tmp` files from a prior crashed write.

**Tasks:**
- [ ] Identify every manifest write path in `manifest_io.py`,
      `task_execution.py`, `run_ops.py`, and coordinator checkpoint helpers
      in `review_ops.py` and `validation_ops.py`.
- [ ] Replace each with an atomic write-to-temp-then-`os.replace()` helper;
      keep the helper in `manifest_io.py` so all call sites share one
      implementation.
- [ ] Add a manifest recovery helper that detects orphaned `.tmp` sibling
      files on run load and removes them after logging a warning.
- [ ] Add regression coverage for the atomic write path and the orphaned-temp
      recovery sweep.
- [ ] Verify no existing test fixture depends on in-place manifest mutation
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
- [ ] Define a transient-error classifier that maps exit codes, exception
      types, and provider error strings to `transient` vs `permanent`; keep
      the classifier in `task_execution.py` or a dedicated `retry_policy.py`.
- [ ] Add per-task retry config: `maxRetries` (default `3`), backoff base
      (`1s`/`2s`/`4s` + jitter), and a `retryPolicy` field in agent/task
      definitions; add a `--max-task-retries` CLI override.
- [ ] Record `attempt`, `attemptErrors`, and `retryDelayMs` in the task record
      so retry history is visible in `status`, `inventory`, and
      `evaluate-run`.
- [ ] Emit a retry-attempt event in the run event trace for each retried
      attempt so `export-trace` captures the full attempt timeline.
- [ ] Ensure retry attempts consume the same workspace and prompt; do not
      re-hydrate the workspace between attempts unless the workspace mode
      requires it.
- [ ] Add regression coverage for attempt counting, backoff timing, transient
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
- [ ] Add `anthropic` as an optional dependency in `pyproject.toml` with a
      clearly named extras group (e.g., `[sdk]`).
- [ ] Implement an SDK-backed provider in `provider.py` that calls
      `client.messages.create()` with the selected model, system prompt, and
      user message; map the response to the existing provider result shape.
- [ ] Wire the SDK `Usage` object (input tokens, output tokens, cache read,
      cache write, cost) directly into the task record `usage` field instead
      of parsing subprocess stdout.
- [ ] Add a streaming path (`client.messages.stream()`) that emits partial
      text to `stderr` for interactive runs and accumulates the full response
      for JSON parsing; keep `stdout` machine-readable.
- [ ] Map `RateLimitError`, `APITimeoutError`, and `InternalServerError` to
      the transient-error classifier from WP42; map `AuthenticationError` and
      `InvalidRequestError` to permanent.
- [ ] Keep the `claude` subprocess path active via a provider-mode flag or
      env var; document which mode is used in dry-run and manifest output.
- [ ] Add regression coverage for the SDK provider result shape, usage
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

**Tasks:**
- [ ] Replace `ThreadPoolExecutor` in `runtime.py` and `run_ops.py` with an
      `asyncio.Semaphore(max_parallel)` guard around `asyncio.gather()`.
- [ ] Convert task dispatch and provider invocation to `async def` functions;
      keep synchronous entry points at the CLI boundary with
      `asyncio.run()`.
- [ ] Replace `subprocess.run()` in `provider.py` with
      `asyncio.create_subprocess_exec()` and `await proc.communicate()`.
- [ ] Stream partial stdout lines to `stderr` during interactive runs so the
      operator sees worker progress without waiting for task completion.
- [ ] Ensure the `--max-parallel` CLI flag still caps concurrency via the
      semaphore; behavior must be identical to the thread-pool path.
- [ ] Add regression coverage for semaphore-bounded parallel dispatch,
      dependency-ordering under async execution, and write-scope serialization
      correctness.
- [ ] Update `scripts/ai/claude-orchestrator.ps1` shim if needed to route
      through the async entry point cleanly on Windows.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/run_ops.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/task_execution.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP45: OpenTelemetry Observability

**Priority:** Medium

**Goal:** Emit standard OTEL spans from orchestration events so runs can be
monitored in any OTEL-compatible backend (Grafana, DataDog, Honeycomb, Jaeger)
without a custom converter.

**Context:**
- The orchestrator already has a rich internal trace model
  (`pojo-lens-orchestrator-trace/v1`) and `export-trace` produces a stable
  span graph. The gap is that this format is custom JSON, not OTEL, so
  connecting it to standard observability tooling requires a converter.
- Adding `opentelemetry-sdk` + `opentelemetry-exporter-otlp-proto-http` maps
  directly to the existing span structure: run span → batch child spans →
  task grandchild spans → coordinator checkpoint spans.
- Token counts, model, cost, and effort already exist in task records and
  can become span attributes, enabling per-task cost dashboards without any
  new data collection.
- OTEL emission should be opt-in via env var (`OTEL_EXPORTER_OTLP_ENDPOINT`)
  so existing runs that do not set the endpoint are unaffected.

**Tasks:**
- [ ] Add `opentelemetry-sdk` and `opentelemetry-exporter-otlp-proto-http`
      as optional dependencies in `pyproject.toml` under an `[otel]` extras
      group.
- [ ] Add an `otel_spans.py` module that wraps the existing `trace_export`
      span structure and emits OTEL spans; activate it when
      `OTEL_EXPORTER_OTLP_ENDPOINT` is set.
- [ ] Map existing span kinds to OTEL span names: `run` → `orchestrator.run`,
      `batch` → `orchestrator.batch`, `task` → `orchestrator.task`,
      `validation` → `orchestrator.validation`, `approval` →
      `orchestrator.approval`.
- [ ] Attach task record fields as span attributes: `model`, `modelProfile`,
      `effort`, `outputProfile`, `usage.totalCostUsd`, `usage.inputTokens`,
      `usage.outputTokens`, `usage.cacheReadTokens`.
- [ ] Emit span status `ERROR` for `failed`/`blocked` task outcomes and
      `OK` for `completed`; keep `UNSET` for pending or unknown.
- [ ] Add a `--otel-endpoint` CLI flag that overrides the env var for
      one-off runs; document both in `ai/orchestrator/README.md`.
- [ ] Add regression coverage for span construction correctness and
      attribute mapping without requiring a live OTEL collector.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/trace_export.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 export-trace .claude-orchestrator/runs/<run-id> --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP46: Typed Agent Contracts

**Priority:** Low

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
- [ ] Define Pydantic v2 models for `TaskPlan`, `TaskDef`, `AgentDef`,
      `RunPolicy`, `OutputProfile`, and `RunManifest` in a new
      `orchestrator_models.py` module; derive them from the existing JSON
      schema definitions in `orchestrator_contracts.py`.
- [ ] Define `TaskRecord`, `DependencyOutput`, and `CoordinatorCheckpoint`
      Pydantic models and use them as the output type of task execution and
      checkpoint persistence helpers.
- [ ] Migrate `plan_support.py`, `task_execution.py`, `run_ops.py`, and
      `manifest_io.py` to accept and return typed models instead of raw
      dicts at their public function signatures; keep internal helpers
      dict-backed where the migration cost exceeds the benefit.
- [ ] Replace the `deps` dict injection pattern with explicit typed parameters
      or a typed `OrchestratorDeps` dataclass at the top-level dispatch layer
      in `orchestrator_app.py`.
- [ ] Add `py.typed` marker to `pojo_lens_agents` so downstream consumers
      benefit from type checking.
- [ ] Run `mypy` or `pyright` over `pojo_lens_agents` after migration; fix
      all errors before marking complete.
- [ ] Add regression coverage that the Pydantic models round-trip correctly
      through the existing JSON manifest fixtures.

**Validate:**
- `py -3 -m py_compile scripts/ai/pojo_lens_agents/*.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json --json`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2 --json`
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
