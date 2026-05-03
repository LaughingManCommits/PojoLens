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
- Every WP that changes orchestrator behavior must update both
  `ai/orchestrator/README.md` and `ai/orchestrator/SYSTEM-SPEC.md` in the same
  package.

---

## Status Overview

Execution order is dependency-first, not ticket-number order.

| WP  | Title                                | Status   | Key deliverables |
|-----|--------------------------------------|----------|------------------|
| WP27| Orchestrator CLI Productization      | Complete | Installable local CLI around the existing multi-agent commands, with stable JSON and one canonical `scripts/ai` implementation home |
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
| WP50| Rate-Limit-Aware Proactive Scheduling| Complete | `RateLimitBucket` sliding-window TPM+RPM; acquire before dispatch; record_completion charges delta; `rate-throttle` events; `rateLimiting` payload; `--tpm-limit`/`--rpm-limit`; env-var fallback; 37 tests |
| WP51| Cross-Run Memory and Pattern Learning | Complete | Persist a structured ledger of what worked and failed across runs so the planner can consult prior evidence when decomposing similar tasks |
| WP52| Diff-Aware Incremental Replay        | Complete | Content-addressed task fingerprinting, `--reuse-unchanged` on run/resume/retry, `--fingerprint-only` on validate, task-reused events, and fingerprint stored on every executed record |
| WP53| CLI Ergonomics                       | Complete | Config file (`pojolens-agents.toml`) for default flags and a `--watch` live progress formatter that tails run events to stderr during long runs |
| WP54| TUI Dashboard                        | Complete | Added optional `textual` dashboard with task grid, rolling cost/elapsed summary, stderr tailing, auto-enable/fallback logic, and TUI HITL approve/abort controls |
| WP55| Guided Wizard Mode                   | Complete | No-args or natural-language wizard that walks the operator through preflight, run, review, promote, and validate-run with resume/retry entry points |
| WP56| Run Completion Notifications         | Complete | Desktop notification, webhook POST, or Slack message when a run finishes, keyed off the `run-finished` event with status and cost summary |
| WP57| Human Diff View Before Promote       | Complete | Added `diff-run`, task/path-filtered workspace-vs-repo diff/stat output, structured JSON diff payloads, and wizard promote-gate diff preview |
| WP58| Persistent Operator Console          | Complete | `pojolens-agents console` session with `/exit`, `/help`, `/jobs`, `/focus`, `/clear`; inline command routing; `run`/`resume`/`retry` as background jobs; waits for jobs on exit |
| WP67| Interactive Surface Consolidation   | Complete | Ownership: `console.py` = session/routing; `tui_console.py` = all Textual UI; `tui_app.py` = run dashboard + canonical `textual_is_available`; `wizard.py` = pure logic. Dispatch routing unified via `route_line`. 697 tests pass. |
| WP68| Matrix Console Visual System        | Complete | Shared `_MTX_VARS` token set + `get_css_variables()` on all App subclasses; full Matrix CSS in `ConsoleApp` and `OrchestratorApp`; wizard prompts styled; `RunSummaryBar`/`FooterBar`/`_status_style()` use Matrix colors; 837 tests pass |
| WP69| Planner-First Wizard Flow           | Complete | Make planner the first-class stage inside `wizard`: clarification loop, staged workspace/setup proposal, explicit approve/edit checkpoint, then execution handoff |
| WP59| TUI Console Test Coverage            | Complete | `test_tui_console.py`: 65 tests across `_ThreadLocalStdout`, `_capture`, `_payload_text`, `ConsoleApp._dispatch`, bg/inline routing, history navigation, `_ExitConfirmModal`, and exit flow. 762 tests pass. |
| WP60| Interactive Streaming During Runs    | Complete | `on_partial_text` callback in sdk_provider; tool-loop `[tool: name]` markers; `[task-id]` line-prefixed stderr; subprocess-provider gated; `_PARTIAL_FACTORY_CTX` contextvar injection; TUI `LogPane` streaming; 38 tests; 836 pass |
| WP62| Hard Budget Cap Enforcement          | Complete | `budget-exceeded` event + `budgetExceeded` payload flag; `budget_exceeded` lifecycleState + flag; `EXIT_BUDGET_EXCEEDED=8`; `--estimate` warns via `estimateBudgetWarning`; 30 regression tests; 925 pass |
| WP70| HITL Gate Correctness                | Complete | `always` fires every batch; stale sentinel gateId validation; 18 new regression tests; 949 pass |
| WP63| Worker Tool Registry                 | Complete | `ExtraToolDef` dataclass + Pydantic validation; `extraTools` in agent/task JSON; `effective_task_tools` task-overrides-agent merge; shell/script execution via `execute_extra_tool`; collision + traversal guards; 27 regression tests; 976 pass |
| WP64| Conditional Task Routing             | Complete | `conditionField`/`conditionValue` predicate on followUpTask proposals; case-insensitive substring match against emitter record fields; skipped tasks emit `task-injection-skipped`; Pydantic mutual-requirement validator; 17 regression tests; 993 pass |
| WP65| Scheduled and Event-Triggered Runs  | Planned | Add `schedule` subcommand to trigger a plan on a cron expression or file-watch pattern, wired through the existing run machinery with retained run output |
| WP66| Agent Shared Context File           | Complete | `write_shared_context` 5th base tool; `shared-context.jsonl` per-run scratchpad; prompt section injection; `sharedContextTags` filter; `sharedContextPath` in manifest; 24 regression tests; 1017 pass |
| WP71| Generated Plan Cleanup              | Complete | `prune_generated_plans` in `runtime_admin.py` wired into `prune_runs`; default 30-day/20-count eviction; slug collision warning in `wizard_command`; `generatedPlanCollision` payload; 16 regression tests; 1033 pass |
| WP72| Orchestrator Core Coverage          | Planned | Add `test_orchestrator_app.py` covering CLI dispatch, handler wiring, and error propagation; validate OTEL endpoint at startup; document rate limiter as advisory in README |
| WP40| End-To-End Coding Run Reliability    | Planned | Full run quality pass — always last before Release Gate; coding + docs end-to-end proofs, evaluate-run corpus alignment, release-grade proof documentation |
| Release Gate | Release Gate                  | Planned  | Cut only after WP40 and all active WPs complete and release guardrails pass |

Completed implementation detail is intentionally not kept here. Historical
detail stays in `CHANGELOG.md`, `ai/state/recent-validations.md`, and git
history.

Post-WP live-run hardening:
- `2026-05-01`: Coordinator contract hardening landed after the real quickstart coding runs: `validate` now warns about risky reviewer prompt budgets, promotion dedupes exact duplicate reviewer/materialized file ownership, and promoted coding runs stay `awaiting_validation` until repo-scope validation is recorded after promotion.
- `2026-05-02`: WP39 is complete; the orchestrator now has lean docs-oriented worker/reviewer profiles, `outputProfile = lean`, tighter worker JSON caps, retained verbosity visibility, and a tracked `example-cheap-proof-docs.json` plan for repeated low-cost live proofs.

---

## WP27: Orchestrator CLI Productization

**Priority:** High

**Decision:** Complete. `pojolens-agents` package wraps all CLI commands under `scripts/ai/pojo_lens_agents`; stable exit-code contract, global options (`--repo-root`, `--runtime-root`, `--json`, `--dry-run`), help-snapshot tests, and updated `ai/orchestrator/README.md` / `SYSTEM-SPEC.md`.

---

## WP28: Orchestrator Runtime Layering

**Priority:** High

**Decision:** Complete. Monolithic script split into focused package layers: `runtime` (scheduler/batching), `governance` (plan policy), `path_safety` (write-scope/protected-path), `provider` + `provider_worker` (invocation adapter), `run_store` (manifest path resolution); each layer has dedicated tests.

---

## WP29: LangGraph Execution Spike

**Priority:** Medium

**Decision:** Complete. Spike concluded: keep custom scheduler and manifest model as production path; LangGraph remains optional future wrapper candidate for checkpointing/replay only. `langgraph_spike.py` prototype + focused tests added.

---

## WP30: Run Visibility And Operator UX

**Priority:** Medium

**Decision:** Complete. `status` command with compact task/governance/promotion summary; enriched `inventory` with failed/blocked/resumable/costly flags; grouped review summaries for scope violations and validation suggestions; dry-run promotion returns explicit allowed/refused with reasons; operator flow documented in `ai/orchestrator/README.md`.

---

## WP31: Orchestrator Trace And Evaluation

**Priority:** Medium

**Decision:** Complete. Manifest-backed run-event trace (start/ready-batch/task-completion/finish); branch-context ids in task records and dependency handoff; `traceSummary`/`branchSummary` in inventory/status; `evaluate-run` quality checker; `example-trace-multibatch.json` regression fixture.

---

## WP32: Orchestrator Bench And Evals

**Priority:** Medium

**Decision:** Complete. `evaluate-run.scoreSummary` (pass/warn/fail counts, score %); benchmark dimensions for decomposition quality, retry correctness, review/promotion accuracy, parallel efficiency; `evaluate-corpus` aggregates across runtime root; `example-eval-readonly-review.json` regression fixture; benchmark fields documented in README.

---

## WP33: Approval State Machine

**Priority:** Medium

**Decision:** Complete. Lifecycle states `awaiting_review`/`awaiting_validation`/`awaiting_promotion`/`completed`; `coordinatorReview`, `coordinatorValidation`, `coordinatorPromotion` checkpoints persisted in manifest; `approvalSummary`, `lifecycleState`, `lifecycleStateReason` in status/inventory.

---

## WP34: Trace Export

**Priority:** Medium

**Decision:** Complete. `trace_export.py` exports `pojo-lens-orchestrator-trace/v1` JSON via `export-trace`; stable run/batch/task/validation/approval span kinds; branch-context lineage mapped to parent span ids; `--task`/`--out`/`--dry-run`/`--json` flags; regression coverage in `test_otel_spans.py`.

---

## WP35: Orchestrator Command Decomposition

**Priority:** High

**Decision:** Complete. Extracted `run_summary` (lifecycle/branch/approval), `review_ops` (review/promote/patch-export), `validation_ops` (validate-run/checkpoint), `evals` (scoring/corpus); `claude-orchestrator.py` reduced to thin CLI wiring; CLI/manifest/JSON contracts preserved.

---

## WP36: Orchestrator Run And Planner Decomposition

**Priority:** High

**Decision:** Complete. Extracted `run_ops` (run/resume/retry), `planner` (plan prompt/build), `runtime_admin` (cleanup/prune/inventory), manifest-records (record coercion/branch-context), prompt/worker contract modules, execution/manifest IO modules; lazy loading on all layers; `orchestrator_app` under 1000-line target.

---

## WP37: Reviewer Findings And Promotion Governance

**Priority:** High

**Decision:** Complete. Structured reviewer findings with `info`/`warn`/`block` severity; blocking findings refuse promotion by default; severity surfaced in `status`, `inventory`, `approvalSummary`, and `evaluate-run`; focused regression coverage in `test_reviewer_findings.py`.

---

## WP38: Docs And Text Quality Guardrails

**Priority:** High

**Decision:** Complete. Mojibake/encoding-sanity checks in `review_ops.py`; ASCII-safe docs policy encoded; text-quality failures surfaced in review, dry-run promotion, and retained status; regression coverage for detection and false-positive avoidance.

---

## WP39: Low-Cost Worker Profiles And Output Discipline

**Priority:** Medium

**Decision:** Complete. Lean docs-oriented implementer/reviewer profiles; `outputProfile = lean`; tighter worker JSON caps; retained verbosity visibility; tracked `example-cheap-proof-docs.json` plan for repeated low-cost live proofs; regression coverage added.

---

## WP41: Crash-Safe Manifest Flushing

**Priority:** High

**Decision:** Complete. All writes go through `write_text()`/`write_json()` in `orchestrator_utils.py` using write-to-`.tmp`-then-`os.replace()`; Windows `PermissionError` retried; `recover_orphaned_write_temps()` cleans crash-left temps on run load; regression tests in `test_agent_atomic_writes.py`.

---

## WP42: Within-Run Task Retry

**Priority:** High

**Decision:** Complete. `retry_policy.py` classifies transient (429/timeout/5xx) vs permanent (scope/auth/JSON); `execute_task_with_retry` with exponential backoff 1s/2s/4s+jitter capped 30s; `attempt`/`attemptErrors`/`retryDelayMs` in task records; `task-retry-attempt` events; `--max-task-retries` CLI + `maxRetries` JSON field; 44 tests in `test_agent_retry_policy.py`.

---

## WP43: Direct Anthropic SDK Provider

**Priority:** Medium

**Decision:** Complete. Dual-provider model: `POJO_LENS_PROVIDER=sdk` (auto-detect from `ANTHROPIC_API_KEY`) routes through `sdk_provider.py` agentic tool loop (4 workspace tools, streaming, usage accumulation); `claude` subprocess remains fallback; 65 tests in `test_sdk_provider.py`.

---

## WP44: Async Task Execution

**Priority:** Medium

**Decision:** Complete. `ThreadPoolExecutor` replaced by `asyncio.Semaphore` + `asyncio.as_completed`; `execute_task`/`run_loaded_plan` converted to `async def`; `--max-parallel` semantics preserved; all tests updated to async fakes.
**Deferred:** partial stdout streaming to stderr during interactive runs — tracked in WP60.

---

## WP45: OpenTelemetry Observability

**Priority:** Medium

**Decision:** Complete. The orchestrator now reuses the retained
`pojo-lens-orchestrator-trace/v1` graph to emit standard OTEL spans to any
OTLP HTTP collector when enabled through `OTEL_EXPORTER_OTLP_ENDPOINT` or a
`--otel-endpoint` override on `run`, `resume`, `retry`, or `export-trace`.


---

## WP46: Typed Agent Contracts

**Priority:** Low

**Decision:** Complete. Pydantic v2 models in `orchestrator_models.py` cover `TaskPlan`, `AgentDef`, `RunPolicy`, `TaskRecord`, and `RunManifest`; existing dataclasses are Pydantic-backed; `py.typed` published; mypy coverage over typed boundaries; model regression tests added.

---

## WP47: Human-in-the-Loop Approval Gates

**Priority:** High

**Decision:** Complete. `RunPolicy.hitl` with modes `none`/`batch`/`on-failure`/`always`; `--hitl`/`--hitl-mode`/`--hitl-auto-approve` CLI flags; `hitl-gate`/`hitl-approved`/`hitl-aborted` events; manifest written before gate; regression tests in `test_hitl_gates.py`.

---

## WP48: Pre-Flight Cost Estimation

**Priority:** High

**Decision:** Complete. `cost_estimation.py` with per-task/per-batch USD+token estimates from `model-pricing.json`; `--estimate` on `run`; `costEstimate` wired into validate/dry-run/live manifests; budget warnings when estimate exceeds `runBudgetUsd`; regression tests in `test_cost_estimation.py`.

---

## WP49: Dynamic Plan Mutation

**Priority:** Medium

**Decision:** Complete. `RunPolicy.followUpBehavior` (`ignore`/`inject`); structured `followUpTasks` worker output; between-batch injection with dependency wiring and scope re-validation; `task-injected`/`task-injection-rejected` events; `injectedFrom` lineage in records and `selected-plan.json`; resume continues injected tasks.

---

## WP50: Rate-Limit-Aware Proactive Scheduling ✅ 2026-05-03

**Delivered:**
- `rate_limiter.py`: `RateLimitBucket` — sliding-window (60 s default) async acquire/record_completion; TPM + RPM enforcement; empty-window pass-through when single estimate exceeds limit.
- `run_ops.run_loaded_plan`: `rate_limit_bucket` param; pre-computes `_token_budget_by_task` via `estimate_plan_cost`; calls `acquire` before semaphore; calls `record_completion` after; emits `rate-throttle` event; writes `rateLimiting` stats to payload; recomputes budgets for injected follow-up tasks.
- `orchestrator_app.run_loaded_plan`: `tpm_limit` / `rpm_limit` params; env-var fallback `ANTHROPIC_TPM_LIMIT` / `ANTHROPIC_RPM_LIMIT`; creates `RateLimitBucket` and passes to `run_ops`.
- `cli_parser`: `_add_rate_limit_args` helper wired to `run`, `resume`, `retry`, `wizard` subparsers.
- `wizard.py`: propagates `tpm_limit` / `rpm_limit` through all three `_namespace` run/resume/retry branches.
- `test_rate_limiter.py`: 37 tests — bucket enabled/disabled, TPM/RPM windows, prune, throttle events, record_completion, stats, run_ops integration, CLI args, env vars.
- Full suite: 799 tests pass.

**Post-delivery review fixes (same session):**
- **Bug**: `_token_budget_by_task` always zero — `estimatedInputTokens`/`estimatedOutputTokens` keys don't exist; real shape is `totalTokens: {min, max}`; fixed to `totalTokens["max"]`.
- **Bug**: `record_completion` actual count always zero — `usage["totalTokens"]` doesn't exist; real keys are `inputTokens` + `outputTokens`; fixed sum of both.
- **Fix**: `record_completion` now async and holds `_lock` during append — eliminates data race when concurrent workers call it simultaneously.
- **Gap**: wizard `--tpm-limit`/`--rpm-limit` flags added to wizard subparser; `tpm_limit`/`rpm_limit` piped through all three wizard `_namespace` calls so wizard-triggered runs honour the rate limits.
- **Gap**: `_token_budget_by_task` recomputed after follow-up task injection so WP49-injected tasks get token-budget throttling, not just RPM throttling.

---

## WP51: Cross-Run Memory and Pattern Learning ✅ 2026-05-02

**Priority:** Medium

**Decision:** Complete. `RunLedgerEntry` appended to `ai/state/run-ledger.jsonl` after every run; `--ledger-context N` on `plan` injects prior-run evidence into planner prompt; `summarize-ledger` subcommand; 90-day pruning in `cleanup`; regression tests in `test_run_ledger.py`.

---

## WP52: Diff-Aware Incremental Replay ✅ 2026-05-02

**Priority:** Medium

**Decision:** Complete. SHA-256 task fingerprinting in `task_fingerprint.py` (prompt + read-path contents + agent JSON + dep summaries + model); `fingerprint`/`fingerprintInputs` stored in records; `--reuse-unchanged` on run/resume/retry; `--fingerprint-only` on validate; `task-reused` events; regression tests in `test_task_fingerprint.py`.

---

## WP53: CLI Ergonomics ✅ 2026-05-02

**Priority:** Medium

**Decision:** Complete. `config_loader.py` with TOML `[defaults]` (`runtime_root`, `claude_bin`, `max_parallel`, etc.), `POJOLENS_CONFIG` env var, `--config` global flag, `config show` subcommand; `--watch` on run/resume/retry streaming `[HH:MM:SS] task-id status cost summary…` to stderr; 33 regression tests.

---

## WP54: TUI Dashboard

**Priority:** Medium

**Decision:** Complete. `tui_app.py` with queue-driven `OrchestratorApp`; panels: `TaskGrid` (DataTable), `RunSummaryBar`, `LogPane` (stderr tail), `FooterBar` (HITL approve/abort bindings); `--tui` with interactive auto-enable and `--watch` fallback; headless Textual regression tests in `test_tui_app.py`.

---

## WP55: Guided Wizard Mode

**Priority:** Medium

**Decision:** Complete. `wizard.py` with plan-inventory, 7-step guided flow (select → preflight → run → review → promote → validate → receipt); optional natural-language goal routing via haiku; `--resume`/`--retry` entry points; `diff-run` wired at promote gate; generated plans under `.claude-orchestrator/generated-plans/`; tests in `test_wizard.py`.

---

## WP56: Run Completion Notifications ✅ 2026-05-03

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

**Decision:** Complete. `notify.py` with `build_notification_payload`, `notify_desktop` (plyer optional), `notify_webhook` (urllib), `notify_slack` (Block Kit); `load_notifications_config` in `config_loader.py` reading `[notifications]` TOML section with `ALLOWED_NOTIFICATIONS`/`VALID_NOTIFY_ON` validation; `--notify`/`--no-notify` mutually-exclusive flags on run/resume/retry; `_fire_notifications_async` daemon thread (join timeout=15s) in `orchestrator_app.py` wired to all three run entry points; `notifications = ["plyer>=2.0"]` extras in `pyproject.toml`; 49 tests in `test_notify.py`; 895 tests pass.

**After-care (2026-05-03):**
- Fixed: `_fire_notifications_async` now returns early when `payload.get("dryRun") or payload.get("estimatedOnly")` is truthy — `--dry-run` and `--estimate` runs no longer fire misleading "0 completed" notifications.
- Fixed: `notify_on = []` (explicit empty list in config) now correctly means "never notify"; changed `config.get("notify_on") or ["always"]` to `notify_on_raw if notify_on_raw is not None else ["always"]` so only a missing key defaults to "always".
- Added 7 tests: `test_notify_on_empty_list_never_dispatches`, `test_notify_on_none_defaults_to_always`, `TestFireNotificationsAsync` (dry_run suppression, estimate suppression, no_notify suppression, live run dispatches, force_desktop flag propagation); 895 tests pass.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --notify --json`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP57: Human Diff View Before Promote ✅ 2026-05-03

**Priority:** Medium

**Decision:** Complete. `diff_run.py` with `difflib.unified_diff` workspace-vs-repo diffs; `diff-run` subcommand with `--tasks`/`--paths`/`--stat`/`--json`; task+path filtering; structured JSON diff payload with `_consoleText`; wizard promote-gate integration; graceful missing-workspace handling.

---

## WP58: Persistent Operator Console ✅ 2026-05-03

**Priority:** Medium

**Decision:** Complete. Plain `console.py` REPL (`ConsoleSession`/`ConsoleJob`/`dispatch_line`) + Textual `tui_console.py` (`ConsoleApp` with cyberpunk aesthetic, `_ThreadLocalStdout` router, `_ExitConfirmModal`); `run`/`resume`/`retry` as background threads; `/help`,`/jobs`,`/focus`,`/clear`,`/exit`; `console` in `KNOWN_COMMANDS` and `cli_parser`; 58 tests in `test_console.py`; **TUI console tests deferred to WP59**.

---

## WP67: Interactive Surface Consolidation

Decision: Complete. Established clear ownership across all four interactive modules:
`console.py` owns session state (`ConsoleSession`/`ConsoleJob`) and shared pure routing
(`DispatchRoute`/`route_line`); `tui_console.py` owns ALL Textual widget/app classes
(wizard prompt screens `_ChoiceApp`/`_ConfirmApp`/`_InputApp` moved here from wizard.py);
`tui_app.py` owns the run-scoped dashboard and is canonical source for `textual_is_available()`;
`wizard.py` is pure logic with no Textual class definitions and imports `textual_is_available`
from tui_app. Dispatch duplication eliminated: `tui_console.ConsoleApp._dispatch` delegates
routing to shared `route_line` instead of mirroring `dispatch_line`. Module docstrings
document ownership for all four files. Post-WP67 review fixes applied: dead
`_make_execute_record` removed from `orchestrator_app.py`; `wizard` added to
`LONG_RUNNING_COMMANDS` so it runs as a background job (prevents frozen TUI when
wizard runs inline from ConsoleApp); unused `shlex` import removed from `tui_console.py`.
697 tests pass.

---

## WP68: Matrix Console Visual System

**Priority:** High → **Complete** (`2026-05-03`)

**Goal:** Turn the current Textual operator UI into a coherent Matrix-style
console surface with a disciplined cyberpunk visual system that feels native to
the orchestrator rather than a pile of neon overrides.

**Context:**
- `tui_console.py` already attempts a retro cyberpunk look, but the current
  theme is mostly ad hoc hardcoded colors and borders embedded directly in one
  app class.
- `tui_app.py` still uses a separate, much plainer visual treatment, so the
  persistent console, run dashboard, and wizard prompt screens do not read as
  one product.
- The current TUI still contains mojibake-corrupted banner/docstring text in
  multiple places. That undercuts the intended theme and needs to be cleaned up
  as part of the visual-system pass, not left as incidental text debt.
- Now that WP67 settled ownership, the next sensible UI work is a single
  shared theme layer spanning the persistent console, run-monitor widgets, and
  wizard prompt screens.

**Tasks:**
- [x] Define one shared Textual theme/token layer for operator UI colors,
      borders, emphasis states, spacing, titles, and status semantics instead
      of scattering hex values across `tui_console.py` and `tui_app.py`.
      Implemented as `_MTX_VARS` dict (shared in `tui_console.py`, mirrored in
      `tui_app.py`) + `get_css_variables()` override on all App subclasses.
- [x] Rework `ConsoleApp` layout and styling into a polished Matrix-style
      console: restrained black/green base, secondary accent(s), legible
      hierarchy, consistent panel framing, and command/output styling that
      still reads clearly during long sessions.
- [x] Restyle the run dashboard widgets in `tui_app.py` to match the same
      visual language so the run-only TUI and persistent console feel like one
      operator product. Added `OrchestratorApp.get_css_variables()` + full
      Matrix CSS; `RunSummaryBar` and `FooterBar` use Matrix Rich markup;
      `_status_style()` returns Matrix hex colors; title updated to
      `POJOLENS // <PLAN>  RUN MONITOR`.
- [x] Restyle wizard prompt screens in `tui_console.py` so `_ChoiceApp`,
      `_ConfirmApp`, and `_InputApp` use the same theme rather than default
      Textual visuals. All three apps now have Matrix CSS + `get_css_variables()`.
- [x] Remove mojibake-corrupted banner/help/decorator text from the TUI layer.
      No active mojibake found in code — banner and help text already use clean
      box-drawing Unicode. Colour drift (`#a0ffc0`, `#2a4a2a`, `#2a4a3a`)
      normalised to canonical theme tokens across all Python markup strings.
- [x] Review panel copy, badges, labels, and footer bindings so the UI feels
      intentional and domain-specific instead of decorative. Footer non-gate
      state updated to `>> live <<`; summary bar fields labelled with Matrix
      markup; jobs panel and sys-bar use CSS variables throughout.
- [x] Add or update focused Textual tests only where styling or compose
      structure changes require it. `test_tui_app.py`: updated footer
      non-gate assertion to use `assertIn("live", ...)` to accommodate new
      text; no test content was removed. 837 tests pass.
- [x] Document the final operator-UI theme approach in the relevant
      orchestrator docs. Theme approach: `_MTX_VARS` + `get_css_variables()`
      override is the canonical extension point for future theme changes.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

**Decision:** Complete. Shared `_MTX_VARS` token set (`bg`, `bg_panel`,
`bg_input`, `green`, `green_body`, `green_dim`, `cyan`, `amber`, `red`,
`text_dim`, `border_dim`) injected via `get_css_variables()` into
`ConsoleApp`, `OrchestratorApp`, `_ChoiceApp`, `_ConfirmApp`, `_InputApp`.
All surfaces now share one visual language. `_ExitConfirmModal.DEFAULT_CSS`
kept as hardcoded hex (widget-level CSS limitation; values match theme).
837 tests pass.

**Review — scope gaps and follow-up findings (all fixed same session):**

1. **`_ExitConfirmModal` can't use `$varname` CSS variables** — `DEFAULT_CSS`
   on a ModalScreen is parsed before the app's `get_css_variables()` runs.
   Kept as hardcoded hex; values match theme. Deferred — no code change needed
   until Textual adds a screen-level CSS-variable hook.

2. **DataTable column widths not tuned for Matrix theme** — Fixed. `TaskGrid`
   columns now have explicit widths (Task 20, Status 10, Model 22, Cost 11,
   Elapsed 8). Cost and Elapsed cells use `Text(..., justify="right")` for
   right-aligned numeric scanning. Column headers also right-justified.
   Test assertions updated to use `.plain` on the returned `Text` cells.

3. **`_ConfirmApp` / `_InputApp` lack a bordered card container** — Fixed.
   Both now wrap content in `Container(id="card")` with `border: heavy $green`,
   `width: 70`, `background: $bg_panel`. Compose structure matches `_ChoiceApp`
   card framing. No tests were using these compose paths directly.

4. **`OrchestratorApp` has no `Header` widget** — Fixed. `yield Header()` added
   to `OrchestratorApp.compose()`; `Header` added to textual import + fallback
   stub. The Matrix title (`POJOLENS // <PLAN>  RUN MONITOR`) now renders.

---

## WP69: Planner-First Wizard Flow

**Priority:** High → **Complete** (`2026-05-03`)

**Goal:** Make the planner the first-class first stage inside `wizard`, so the
operator talks to the planner first, gets a staged workspace/setup proposal,
reviews or edits that proposal, and only then launches worker execution.

**Tasks:**
- [x] Define planner-first stage sequence: goal intake → clarification loop →
      staged plan summary → approve/revise/stop checkpoint → execution handoff.
- [x] Add `_clarification_output_schema_json()`, `_clarification_prompt()`,
      `clarify_goal_with_claude()`, and `_run_clarification_loop()` to
      `wizard.py`; planner agent (haiku, effort=low) asks up to 3 focused
      questions when goal is underspecified; operator answers fed back into
      `_goal_active` for intent resolution.
- [x] Add `_format_staged_plan_summary()` to render plan name, task count, and
      first 10 task IDs with agent labels after `validate_handler` runs.
- [x] Add `_CHECKPOINT_PROCEED / _REVISE / _STOP` constants and
      `_plan_approval_checkpoint()` which calls `prompter.choose()` (not
      `confirm()`) so existing tests are unaffected.
- [x] Refactor `wizard_command` plan mode into a 3-round revision loop:
      clarification (pre-loop), then per-round: intent resolution → plan
      selection → validate → staged summary → checkpoint. Revise resets
      `_explicit_plan = ""` and re-enters clarification for the new goal.
      Stop returns early before `run_handler`. Proceed breaks to execution.
- [x] Update wizard CLI help text in `cli_parser.py` to describe the
      planner-first flow.
- [x] Add 4 focused regression tests to `test_wizard.py`:
      `test_plan_approval_checkpoint_noninteractive_returns_proceed`,
      `test_format_staged_plan_summary_includes_plan_name_and_tasks`,
      `test_plan_approval_checkpoint_stop_exits_before_run`,
      `test_plan_approval_checkpoint_revise_reruns_validation`.
- [x] All existing wizard tests pass unchanged (approve uses `choose()` default
      → "proceed", no `confirms` consumed).

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

**Decision:** Complete. `wizard.py` now has a planner-first operator flow:
interactive runs with a free-text goal enter a clarification stage (Claude haiku
asks up to 3 focused questions, operator answers refine `_goal_active`). After
intent resolution and preflight validation a staged plan summary is shown and the
operator chooses Proceed / Revise / Stop. Revise resets the goal, re-runs
clarification and resolution up to 3 rounds. Stop exits before `run_handler`.
Non-interactive mode always proceeds immediately. Explicit `--plan` flag skips
clarification (goal already pinned). 841 tests pass.

---

## WP59: TUI Console Test Coverage

**Priority:** Medium

**Decision:** Complete. `test_tui_console.py` added with 65 tests covering all
consolidated surfaces: `_ThreadLocalStdout` routing/install/encoding/flush (9);
`_capture` interception/result/sink-lifecycle (5); `_payload_text` all paths (5);
`ConsoleApp._dispatch` for all 13 route actions via headless `run_test()`;
bg/inline worker routing + failure paths + busy-flag lifecycle (13);
history prev/next/clamp/submit (8); `_ExitConfirmModal` button dismissal via
direct `on_button_pressed` + compose via ConsoleApp host (6); `_exit_flow`/
`action_request_quit` no-jobs, with-jobs, confirm, cancelled, completed-jobs-
excluded (5). `ModalScreen` limitation noted: use direct handler calls for
dismiss-value tests and ConsoleApp host for compose render tests.
762 tests pass total.

---

## WP60: Interactive Streaming During Runs

**Priority:** Low → **Complete** (`2026-05-03`)

**Goal:** After WP67 consolidates the operator surfaces, wire partial SDK
output into the shared interactive output path during TTY runs so operators see
token-level progress instead of a blank wait, closing the deferred WP44 task.

**Context:**
- `sdk_provider.py` already calls `client.messages.stream()` when stderr is a TTY. Output currently accumulates in a buffer and is only written at task completion.
- `_make_watch_append_run_event` in `orchestrator_app.py` formats run events to stderr but has no hook for intra-task partial lines.
- WP67 should leave us with one interactive output ownership model across plain
  console, TUI console, and legacy run-watch paths. This WP should plug partial
  streaming into that shared path instead of inventing a console-only side
  channel.
- The fix is to let `sdk_provider.py` emit partial text lines directly to
  stderr (or via a callback) during the stream, independent of the event
  formatter.
- `--json` stdout must remain clean; partial streaming targets stderr only.

**Tasks:**
- [x] Execute this WP after WP67 so partial streaming lands on the consolidated
      interactive output path.
- [x] Add an optional `on_partial_text: Callable[[str], None] | None` param to `sdk_provider.run_sdk_provider`.
- [x] When streaming and `on_partial_text` is provided, call it for each delta text token; `stream_to_stderr` backward compat preserved (TTY-gated stderr writes if no callback).
- [x] Wire `on_partial_text` in `task_execution.execute_task` via `deps["partial_text_writer_factory"]`; factory called once per task returning a per-task callback.
- [x] `orchestrator_app` injects factory via `_PARTIAL_FACTORY_CTX` contextvar (thread-safe; no public API signature changes): `--watch` → `_make_stderr_partial_factory()`; TUI → `_make_tui_partial_factory(event_queue, loop)` set inside async context.
- [x] TUI: `task-streaming` events routed to `LogPane` via `call_soon_threadsafe`; `_streaming_active` flag suppresses `_refresh_log_tail` tail-poll during active stream.
- [x] `--json` stdout remains clean; no partial text written to stdout.
- [x] 25 regression tests in `test_streaming.py` covering all layers; 824 total pass.

**Review findings (`2026-05-03`) — all fixed same session:**

- **Fixed: tool-loop silence** — `sdk_provider` now emits `\n[tool: <name>]\n`
  via `on_partial_text` for each tool-use block before executing the tool. Operators
  see agentic progress (e.g. `[tool: bash]`) during multi-step tool loops instead
  of silence between text turns. 5 regression tests added.

- **Fixed: watch-mode interleaving** — `_make_stderr_partial_factory` now
  line-prefixes every line of output with `[task-id] ` using a per-writer
  `_at_line_start` flag. Partial tokens within a line accumulate before the next
  newline; the prefix is added exactly once per line. Parallel tasks are
  distinguishable in stderr output. 7 regression tests added.

- **Fixed: subprocess factory waste** — `run_loaded_plan` now gates factory
  construction on `sdk_provider_layer.detect_provider_mode() == "sdk"` for both
  the `--watch` (non-TUI) and TUI paths. No factory object is created or set in
  the contextvar when running in subprocess mode. 2 regression tests added.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP62: Hard Budget Cap Enforcement

**Priority:** High

**Goal:** Make `runPolicy.budgetBehavior = "stop"` production-ready so a run that exceeds `runBudgetUsd` cancels remaining batches gracefully instead of only warning.

**Context:**
- `stop` mode exists in the codebase but lacks event tracking, manifest lifecycle state, and regression coverage. All production orchestrators (Prefect, Temporal, LangSmith) enforce hard resource budgets. Without this, `runBudgetUsd` is advisory only — a runaway parallel run can spend 10× the declared limit.
- The fix: after each batch completion, check cumulative `totalCostUsd` from completed records against `runBudgetUsd`; if exceeded, emit a `budget-exceeded` event, block all pending tasks as `blocked`, write manifest, and exit with a distinct exit code.

**Tasks:**
- [x] In `run_ops.run_loaded_plan`, after each batch, sum `usage.totalCostUsd` from all completed records; if sum > `runBudgetUsd` and `budgetBehavior == "stop"`, block remaining pending tasks and exit run loop with status `budget_exceeded`.
- [x] Emit `budget-exceeded` run event with `{actualCostUsd, limitCostUsd, remainingTaskIds}` so the event trace records where the cap fired.
- [x] Set `lifecycleState = "budget_exceeded"` in retained manifest summary; surface it in `status` and `inventory` output.
- [x] Add distinct exit code for budget-exceeded runs (alongside existing `failed`/`blocked` codes) in `command_dispatch.py`.
- [x] Add regression coverage for: budget not exceeded (no effect), budget exceeded mid-run (remaining blocked), `warn` mode still runs to completion, `--estimate` pre-flight warns when estimate > budget.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP63: Worker Tool Registry

**Priority:** Medium

**Goal:** Replace the 4 hardcoded SDK workspace tools with a plan/agent-declared extensible registry so task plans can expose project-specific tools like `run_tests`, `lint_file`, or `search_docs` to workers.

**Context:**
- CrewAI, AutoGen, and LangGraph all allow agent definitions to declare or inherit tool sets. The PojoLens SDK provider hardcodes `read_file`, `write_file`, `str_replace_based_edit_tool`, and `bash`. Any project-specific tool requires modifying `sdk_provider.py`.
- The right shape: an `extraTools` JSON list in agent or task definitions, each entry declaring `name`, `description`, and either `shellTemplate` (run a shell command with `{args}` interpolation) or `scriptPath` (invoke a repo-local script). The SDK provider constructs tool schemas at task-start from the merged base+extra list.
- Workspace-root path enforcement and traversal rejection still apply to all tools.

**Tasks:**
- [ ] Define an `ExtraToolDef` Pydantic model in `orchestrator_models.py`: `name: str`, `description: str`, `kind: Literal["shell", "script"]`, `template: str` (shell command or script path), `timeoutSec: int = 30`.
- [ ] Add `extraTools: list[ExtraToolDef] = []` to `AgentDefinition` and `TaskDefinition`; merge task-level tools over agent-level in `plan_support.effective_task_tools()`.
- [ ] In `sdk_provider.py`, accept `extra_tools: list[dict]` param; build Anthropic tool schemas from each entry at task-start; route tool calls to shell execution with workspace-root enforcement.
- [ ] Add `effective_task_tools` to the deps dict in `orchestrator_app.execute_task`.
- [ ] Add validation in `validate_command` that `extraTool.template` does not contain path traversal and `extraTool.name` does not collide with base tool names.
- [ ] Add regression coverage for tool merging, schema construction, shell execution routing, and collision validation.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP64: Conditional Task Routing

**Priority:** Medium

**Goal:** Allow a completed task's structured output to gate which follow-up tasks inject next, closing the gap with LangGraph conditional edges for reviewer-to-implementer feedback loops.

**Context:**
- WP49 added follow-up task injection but the injection decision is unconditional: all `followUpTasks` from a completed task are injected when `followUpBehavior = "inject"`. LangGraph's primary differentiator is conditional edges — "if reviewer output.severity contains 'block', route to fix-implementer task; else route to promote-gate task".
- The safe boundary: after a task completes, evaluate `conditionField` dot-path against the task's output JSON. If the condition passes, inject the follow-up; otherwise skip it. No LLM call needed — purely structural JSON evaluation.
- Example: `{"conditionField": "findings[*].severity", "conditionOp": "contains", "conditionValue": "block"}` on a reviewer follow-up task only injects the fix task when blocking findings exist.

**Tasks:**
- [ ] Add `conditionField: str | None`, `conditionOp: Literal["eq","neq","contains","gt","gte","lt","lte","exists"] | None`, and `conditionValue: Any` to `TaskDefinition` follow-up task schema.
- [ ] In `coerce_follow_up_task` (or `run_ops` injection path), evaluate the condition against the emitting task's parsed result JSON using a small `evaluate_condition(record, field, op, value)` helper; skip injection if condition is False.
- [ ] Emit `task-injection-skipped` run event with `{taskId, conditionField, reason}` when a conditional follow-up is skipped.
- [ ] Add regression coverage for: unconditional injection (unchanged), condition passes → injected, condition fails → skipped, missing field → skipped (not error), invalid dot-path → `OrchestratorError` at validate time.
- [ ] Update `validate_command` to type-check `conditionField` + `conditionOp` combinations at plan validation time.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP65: Scheduled and Event-Triggered Runs

**Priority:** Low

**Goal:** Allow a plan to be triggered on a cron schedule or file-watch pattern without the operator manually invoking `pojolens-agents run`, closing the gap with Prefect deployments and Temporal schedules.

**Context:**
- Every serious workflow platform (Prefect, Temporal, Airflow, GitHub Actions) supports scheduled triggers. The operator currently has to be present to launch a run. For nightly code-health or docs-refresh runs this is unnecessary friction.
- Simplest viable shape: a `schedule` subcommand backed by a background daemon (or a one-shot `at`-style invocation) that reads a cron expression or `--on-change <glob>` file-watch pattern and fires `run_plan` when triggered. The daemon writes its PID to `.claude-orchestrator/schedule.pid` and logs to `.claude-orchestrator/schedule.log`.
- This does not require external infrastructure — Python's `schedule` library (or `apscheduler`) for cron; `watchdog` for file change. Both are optional dependencies.

**Tasks:**
- [ ] Add `schedule` subcommand to CLI: `pojolens-agents schedule <plan> --cron "0 2 * * *"` (nightly) or `--on-change "src/**/*.java"` (file watch); `--once` for one-shot deferred run.
- [ ] Add `schedule.py` in `pojo_lens_agents`; implement cron loop (via `apscheduler>=3`) and file-watch loop (via `watchdog>=3`); both trigger `run_plan` and write outcome to `schedule.log`.
- [ ] Add `schedule stop` to kill the daemon via PID file; `schedule status` to report next-run time and last-run outcome.
- [ ] Add `apscheduler` and `watchdog` as optional deps under `[schedule]` extras in `pyproject.toml`; degrade gracefully when not installed.
- [ ] Add regression coverage for cron expression parsing, file-watch pattern matching, and daemon start/stop lifecycle (mock clock and file events).

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP66: Agent Shared Context File

**Priority:** Low

**Goal:** Add a per-run shared scratchpad that any worker can read and append to within a run, enabling agent-to-agent coordination beyond unidirectional dependency summaries.

**Context:**
- AutoGen group chats and CrewAI crew memory both provide a shared context that agents can accumulate observations into. PojoLens workers are currently isolated — they can only see dependency summaries passed by the coordinator at task start. There is no way for an implementer to leave a note for a downstream reviewer that is not part of the formal output schema.
- The safe shape: a per-run `shared-context.jsonl` file under `.claude-orchestrator/runs/<run-id>/shared-context.jsonl`. Workers can append a structured `{taskId, note, tags}` line via a `write_shared_context` tool (added to the SDK tool registry). The coordinator injects a bounded tail of the shared-context file into each downstream task prompt section as an optional `sharedContext` section.
- This is deliberately lightweight — not a vector store, not persistent across runs. Goal is intra-run coordination: "implementer-1 noted that file X has a race condition; reviewer should prioritize that."

**Tasks:**
- [x] Add `write_shared_context(note: str, tags: list[str])` as a 5th base tool in `sdk_provider.py`; appends `{ts, taskId, note, tags}` JSON line to run-dir `shared-context.jsonl`; workspace-root path enforcement not needed (writes to run dir, not workspace).
- [x] Add a `sharedContext` prompt section in `plan_support.worker_prompt` that injects the last N (default 10) lines from `shared-context.jsonl` filtered by optional `tags` declared in the task definition; include only when file exists.
- [x] Add `sharedContextTags: list[str] = []` to `TaskDefinition`; when non-empty, filter shared context lines to matching tags only.
- [x] Persist `shared-context.jsonl` path in the run manifest so `status`, `export-trace`, and `review` can reference it.
- [x] Add regression coverage for: tool call appends line, prompt section injection, tag filtering, missing file is no-op, line count limiting.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP70: HITL Gate Correctness

**Priority:** Medium

**Goal:** Fix two correctness defects in the HITL gate system discovered during the WP69 feature review: `always` mode fires only once instead of every batch, and stale sentinel files from interrupted runs can be acted upon during resume.

**Context:**
- `hitl.py:82`: `if policy.mode == "always": return batch_index == 1` — the condition means "fire once before the first batch". The documented and intuitive meaning of `always` is "fire before every batch". All other modes (`batch`, `on-failure`) behave as named; `always` does not.
- `hitl.py:87`: sentinel path is always `run_dir / "hitl-gate.lock"`. If a run is interrupted between `write_hitl_sentinel()` and `wait_for_hitl_decision()` returning, the file persists on disk. On resume, a new gate context with the same run dir but a different `gate_id` could read the stale file and act on the old decision without the operator being prompted again.

**Tasks:**
- [x] Fix `should_trigger_hitl_gate`: change `always` from `batch_index == 1` to `True` (fire before every batch when enabled).
- [x] Fix stale sentinel: in `wait_for_hitl_decision`, after reading the sentinel file, validate that the `gateId` field in the file matches `context.gate_id`; if mismatched treat the file as absent and wait for a fresh decision.
- [x] Add regression tests for: `always` mode fires on batch 2+; mismatched gate ID in sentinel is ignored and operator is re-prompted; matching gate ID proceeds normally.
- [x] Update `ai/orchestrator/README.md` and `ai/orchestrator/SYSTEM-SPEC.md` to clarify `always` semantics.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP71: Generated Plan Cleanup

**Priority:** Low

**Goal:** Wire pruning of `.claude-orchestrator/generated-plans/` into the `cleanup` command, and add a collision warning when a new generated plan would overwrite an existing one with the same 48-char goal slug.

**Context:**
- `wizard.py:385`: `_generated_plan_path` truncates the goal slug to 48 chars and writes `generated-wizard-{slug}.json`. Files accumulate indefinitely — there is no cleanup path. The `cleanup` command prunes old run directories but ignores `generated-plans/`.
- Goals that differ only after the first 48 characters silently overwrite each other. An operator who runs the wizard twice with similar-but-distinct goals may lose the first generated plan without warning.
- Generated plans are ephemeral (wizard artefacts, not tracked plans) so aggressive pruning (e.g. keep last 20, or older than 30 days) is safe.

**Tasks:**
- [x] Add generated-plans pruning to the `cleanup` command: respect the existing `--keep-last-n` / `--older-than-days` policy applied to run directories; default to pruning generated plans older than 30 days or when count exceeds 20.
- [x] In `_generated_plan_path` (or its caller), warn via `prompter.show_message` when the target path already exists and the new goal slug differs from the stored `goal` field inside the existing file.
- [x] Add regression tests for: cleanup removes old generated plans; cleanup keeps recent ones; collision warning fires when slug matches but goal differs; no warning when slug and goal match (same goal re-resolved).
- [x] Update `ai/orchestrator/README.md` cleanup section.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP72: Orchestrator Core Coverage

**Priority:** Medium

**Goal:** Add direct test coverage for the CLI dispatch and handler-wiring layer in `orchestrator_app.py`, validate the OTEL endpoint at startup, and document the rate limiter's advisory nature in the operator README.

**Context:**
- `orchestrator_app.py` is the main wiring layer (1000+ lines): it maps CLI args to handler calls, injects all deps, and owns the `wizard_command` wrapper. The handler logic is covered by per-handler test files (`test_agent_runtime.py`, `test_agent_governance.py`, etc.) but the dispatch layer itself — argument coercion, dep injection, error propagation to CLI exit codes — has no dedicated tests. A regression in wiring (wrong dep injected, missing field) goes undetected.
- `otel_spans.py`/`run_ops.py`: the `--otel-endpoint` / `OTEL_EXPORTER_OTLP_ENDPOINT` value is accepted as a raw string and only fails at emission time (silently). Validating the URL format at startup gives the operator a clear error before a run starts.
- `rate_limiter.py`: the bucket pre-deducts estimated tokens, not actual usage. If a task uses more tokens than estimated, the overage isn't retroactively applied to the window. This is acceptable (pre-deduction is the only safe option before execution) but is not documented as advisory.

**Tasks:**
- [ ] Add `test_orchestrator_app.py` with focused tests for: `wizard_command` dep injection passes expected keys; `run_command` forwards `tpm_limit`/`rpm_limit` to rate limiter; CLI argument coercion (e.g. `max_parallel` clamped to ≥1); error from handler propagates to non-zero exit; `--json` flag suppresses interactive mode in wizard.
- [ ] Add OTEL endpoint validation: when `otel_endpoint` is non-empty, validate it is a parseable HTTP/HTTPS URL before starting the run; emit a clear `ValueError` with the offending value if malformed.
- [ ] Add a "Rate limiting" section to `ai/orchestrator/README.md` noting that token budgets use pre-flight estimates and actual usage may differ; overruns within a batch are absorbed, future batches will be throttled.
- [ ] Update `ai/orchestrator/SYSTEM-SPEC.md` with the advisory rate-limiter invariant.

**Validate:**
- `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- `scripts/docs/check-doc-consistency.ps1`

---

## WP40: End-To-End Coding Run Reliability

**Priority:** High

**Goal:** Close the remaining gaps between "the orchestrator can run" and "the orchestrator is dependable for real coding work from plan to promoted repo state". Always runs last before Release Gate.

**Context:**
- Real proofs exist for read-only, parallel, coding, selective-promotion, and post-promotion validation runs, but each exposed governance or content-quality gaps.
- This is an end-to-end reliability pass across planning, execution, review, promotion, and validation using tracked real-world examples in this repo.

**Tasks:**
- [ ] Revisit the tracked quickstart coding/doc plans and align them with the stronger reviewer and docs guardrails from WP37-WP39.
- [ ] Add at least one clean end-to-end coding proof where implementer output, reviewer approval, promotion, and post-promotion validation all succeed without manual coordinator patching.
- [ ] Add at least one clean end-to-end docs proof where multiple parallel implementers plus a reviewer succeed without leaving unpromoted fixes.
- [ ] Expand `evaluate-run` and/or corpus evaluation to reflect reviewer-governance and text-quality signals.
- [ ] Decide which retained-run outcomes are release-grade proof points and document them in `ai/orchestrator/README.md`.
- [ ] Update `CHANGELOG.md`, memory state, and tracked validations to reflect the final reliability baseline.

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
