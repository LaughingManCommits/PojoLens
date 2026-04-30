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
| WP29| LangGraph Execution Spike            | Pending  | Decision record and small prototype for checkpointed parallel graph execution without weakening repo safety rules |
| WP30| Run Visibility And Operator UX       | Pending  | Better status, inventory, review, and validation surfaces for retained multi-agent runs |
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

**Tasks:**
- [ ] Map the current run lifecycle onto graph nodes: load plan, validate
      scope, hydrate workspace, invoke worker, parse result, audit diff,
      checkpoint, review interrupt, validate-run, and promote.
- [ ] Prototype the smallest checkpointed graph that can execute a dry-run
      worker plan or simulated two-task parallel run.
- [ ] Compare LangGraph parallel node execution with the current ready-batch
      scheduler, including failure, retry, and partial-checkpoint behavior.
- [ ] Evaluate human-in-the-loop interrupts for review and promotion approval.
- [ ] Evaluate replay/resume semantics against the current manifest-based
      `resume` and `retry` behavior.
- [ ] Document what LangGraph would replace, what it would wrap, and what must
      remain custom.
- [ ] Decide whether to proceed with an optional backend, keep the custom
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

**Tasks:**
- [ ] Add or refine a compact `status` view for a single retained run.
- [ ] Improve `inventory` output so incomplete, blocked, resumable, costly, and
      promotion-ready runs are obvious in text and JSON modes.
- [ ] Add a concise review summary that groups changed files, scope violations,
      protected-path violations, dependency materialization, and validation
      suggestions.
- [ ] Add a dry-run promotion summary that clearly explains why promotion is
      allowed or refused.
- [ ] Keep stdout machine-readable in `--json` mode and send interactive
      progress/status details to stderr only.
- [ ] Document the recommended operator flow from plan validation through run,
      review, validation, promotion, and cleanup.

**Validate:**
- `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/cli.py scripts/ai/pojo_lens_agents/governance.py scripts/ai/pojo_lens_agents/path_safety.py scripts/ai/pojo_lens_agents/provider.py scripts/ai/pojo_lens_agents/run_store.py scripts/ai/pojo_lens_agents/runtime.py scripts/ai/pojo_lens_agents/workspace_review.py scripts/tests/test_agent_governance.py scripts/tests/test_agent_path_safety.py scripts/tests/test_agent_provider.py scripts/tests/test_agent_run_store.py scripts/tests/test_agent_runtime.py scripts/tests/test_agent_workspace_review.py scripts/tests/test_claude_orchestrator.py`
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 inventory --json`
- `scripts/ai/claude-orchestrator.ps1 prune --older-than-days 14 --dry-run --json`
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
