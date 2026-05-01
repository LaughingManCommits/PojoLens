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
| WP34| Trace Export                         | Planned  | Export span-style traces from retained run events, handoffs, validations, and approval gates for external analysis |
| WP18| JDK 25 Runtime Knob Evaluation       | Deferred | Optional runtime-performance guidance; not blocking the orchestration toolchain work |
| Release Gate | Release Gate                  | Deferred | Cut only after the active roadmap queue and release guardrails are complete |

Completed implementation detail is intentionally not kept here. Historical
detail stays in `CHANGELOG.md`, `ai/state/recent-validations.md`, and git
history.

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

**Tasks:**
- [ ] Define a stable export shape for run, batch, task, validation, and
      approval spans.
- [ ] Map retained event/branch lineage into parent-child trace relationships.
- [ ] Add one CLI export surface that writes trace data without changing the
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
