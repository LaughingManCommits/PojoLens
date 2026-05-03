# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP70 -> WP63 -> WP64 -> WP65 -> WP66 -> WP71 -> WP72 -> WP40 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-03`: WP62 complete; `budget-exceeded` event, `budgetExceeded` flag, `budget_exceeded` lifecycleState, `EXIT_BUDGET_EXCEEDED=8`, `estimateBudgetWarning`; 925 tests pass.
- `2026-05-03`: WP56 complete + after-care; dry-run/estimate suppression, `notify_on=[]` fix; 895 tests pass.
- `2026-05-03`: Queue is WP70 -> WP63 -> WP64 -> WP65 -> WP66 -> WP71 -> WP72 -> WP40 -> Release Gate.

## Facts
- `2026-05-03`: WP62: `budget_exceeded_stop` flag in `run_loaded_plan`; fires `budget-exceeded` event when `kind=="budget"` blocking alert fires; `budgetExceeded` in payload; `derive_run_lifecycle_state` checks `summary_base["budgetExceeded"]` before `hasBlocked`; `EXIT_BUDGET_EXCEEDED=8` in contracts; `_worker_run_exit_code(status_counts, budget_exceeded=False)`.
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
