# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP56 -> WP61 -> WP62 -> WP63 -> WP64 -> WP65 -> WP66 -> WP40 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-03`: WP68 and all 4 follow-up findings are complete; shared Matrix theme, `Header` in OrchestratorApp, DataTable column widths/right-align, wizard card containers; 837 tests pass.
- `2026-05-03`: WP60 is complete; shared partial streaming reaches watch and TUI surfaces; 836 tests passed.
- `2026-05-03`: WP50 is complete; proactive rate limiting, wizard propagation, and follow-up-budget recompute landed; 799 tests passed.
- `2026-05-03`: WP59 and WP67 are complete; Textual ownership is consolidated and covered by focused tests.
- `2026-05-03`: Queue is WP56 -> WP61 -> WP62 -> WP63 -> WP64 -> WP65 -> WP66 -> WP40 -> Release Gate.

## Facts
- `2026-05-03`: `console.py` owns session state and shared routing (`DispatchRoute`/`route_line`).
- `2026-05-03`: `tui_console.py` owns all Textual UI; `tui_app.py` owns the run dashboard and `textual_is_available`; `wizard.py` is pure logic.
- `2026-05-03`: Shared Matrix theme lives in `_MTX_VARS` dict in `tui_console.py`; all App subclasses inject it via `get_css_variables()` override (Textual 0.89.1 API). `_ExitConfirmModal.DEFAULT_CSS` keeps hardcoded hex (widget-level CSS limitation; values match theme).
- `2026-05-03`: `rate_limiter.py` owns `RateLimitBucket`; `run_ops.run_loaded_plan` integrates it and recomputes token budgets after follow-up injection.
- `2026-05-03`: WP60 uses `_PARTIAL_FACTORY_CTX` in `orchestrator_app.py` to inject shared partial-text writers into `task_execution` and `sdk_provider`.
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
