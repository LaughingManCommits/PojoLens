# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP65 -> WP71 -> WP72 -> WP40 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-03`: WP66 complete; `write_shared_context` 5th base tool; `execute_shared_context_tool` in `sdk_provider.py`; `_read_shared_context_tail` + `shared_context_notes` section in `prompt_contracts.py`; `sharedContextTags` on `TaskDefinition`/`TaskDefinitionModel`; `sharedContextPath` in manifest; 24 new tests; 1017 pass.
- `2026-05-03`: WP64 complete; `conditionField`/`conditionValue` optional predicate on followUpTask proposals; `_check_follow_up_condition` in `run_ops.py`; case-insensitive substring match against emitter `TaskRunRecord` fields; skipped tasks emit `task-injection-skipped` event with field/value/actual details; Pydantic mutual-requirement validator (both or neither); 17 new tests; 993 pass.
- `2026-05-03`: WP63 complete; `ExtraToolDef` dataclass + Pydantic `ExtraToolDefModel`; `extraTools` in agent/task JSON; `effective_task_tools` task-overrides-agent merge; shell/script execution via `execute_extra_tool` with collision + traversal guards; validate payload exposes `agentExtraTools` + per-task `extraTools`; 27 new tests; 976 pass.
- `2026-05-03`: WP70 complete; `always` fires every batch (`True` not `batch_index==1`); stale sentinel gateId validation; 18 new tests; 949 pass.
- `2026-05-03`: WP62 complete + after-care; `orchestrator_app` wrapper now derives `budgetExceeded` from manifest; 931 tests pass.
- `2026-05-03`: Queue is WP64 -> WP65 -> WP66 -> WP71 -> WP72 -> WP40 -> Release Gate.

## Facts
- `2026-05-03`: WP66: `SHARED_CONTEXT_FILENAME="shared-context.jsonl"`, `SHARED_CONTEXT_TAIL_LINES=10`, `MAX_SHARED_CONTEXT_NOTE_CHARS=500` in `orchestrator_contracts.py`; `"write_shared_context"` added to `BASE_TOOL_NAMES`; `shared_context_tags` on `TaskDefinition`/`TaskDefinitionModel`; `execute_shared_context_tool` in `sdk_provider.py`; `_read_shared_context_tail` + `shared_context_notes` prompt section in `prompt_contracts.py`; `shared_context_path=run_dir/SHARED_CONTEXT_FILENAME` computed and threaded through `task_execution.py` → `worker_prompt` + `run_sdk_provider`; `sharedContextPath` in `manifest_io.py` payload.
- `2026-05-03`: WP64: `conditionField`/`conditionValue` added to `TaskDefinition`, `TaskDefinitionModel`, `WORKER_RESULT_SCHEMA` followUpTask item; `_check_follow_up_condition(injected_task, record)` in `run_ops.py`; `_inject_follow_up_tasks` split into 3 phases: coerce → condition check → topology/scope; skipped tasks emit `task-injection-skipped` with `conditionField`, `conditionValue`, `actualValue` in details; `task_plan_ops.load_task_definition` passes `condition_field`/`condition_value` through factory call.
- `2026-05-03`: WP63: `ExtraToolDef(name,description,kind,template,timeout_sec)` in `orchestrator_contracts.py`; `ExtraToolDefModel` Pydantic validator rejects base-tool name collision + `..` traversal; `effective_task_tools(task,agent)` returns task-level when non-empty else agent-level; `execute_extra_tool` runs shell/script template via `subprocess.run(shell=True, cwd=workspace_root)`, caps output at `MAX_TOOL_OUTPUT_CHARS`; `run_sdk_provider` accepts `extra_tools: list[dict]|None`; validate payload adds `agentExtraTools` dict + per-task `extraTools` list.
- `2026-05-03`: WP70: `hitl.py` `should_trigger_hitl_gate` `always` branch = `True` (was `batch_index==1`); `_sentinel_action(path, expected_gate_id)` tries JSON first, rejects mismatched gateId; polling loop passes `context.gate_id`; plain-text fallback retained for backwards compat.
- `2026-05-03`: WP62: `budget_exceeded_stop` flag in `run_loaded_plan`; `budget-exceeded` event has `{actualCostUsd, limitCostUsd, remainingTaskIds}`; `budgetExceeded` in payload; `derive_run_lifecycle_state` checks `summary_base["budgetExceeded"]` before `hasBlocked`; `EXIT_BUDGET_EXCEEDED=8`; wrapper in `orchestrator_app.py` derives from manifest if not in summary_base.
- `2026-05-03`: `notify.py` owns notification channels; `dispatch_notifications` injects `_desktop_fn/_webhook_fn/_slack_fn`; `_fire_notifications_async` daemon thread (join 15s) wired to run_plan/resume_run/retry_run; skips on `dryRun`/`estimatedOnly` payloads.
- `2026-05-03`: `config_loader.py` has `load_notifications_config` ([notifications] TOML, ALLOWED_NOTIFICATIONS, VALID_NOTIFY_ON); `_find_config_path` shared by both load functions.
- `2026-05-03`: `console.py` owns session state and shared routing (`DispatchRoute`/`route_line`).
- `2026-05-03`: `tui_console.py` owns all Textual UI; `tui_app.py` owns run dashboard and `textual_is_available`; `wizard.py` is pure logic.
- `2026-05-03`: Planner inside wizard: `clarify_goal_with_claude` + `_run_clarification_loop`; workers start only after `_plan_approval_checkpoint` returns "proceed".
- `2026-05-03`: Shared Matrix theme in `_MTX_VARS` dict in `tui_console.py`; App subclasses inject via `get_css_variables()` override (Textual 0.89.1).
- `2026-05-03`: `rate_limiter.py` owns `RateLimitBucket`; `run_ops.run_loaded_plan` integrates it and recomputes token budgets after follow-up injection.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`
- `README.md`, `RELEASE.md`, `.github/workflows/*`
