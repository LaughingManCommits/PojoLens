# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP75 -> WP76 -> WP77 -> WP78 -> WP79 -> WP40 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-04`: WP65 is complete; `schedule.py` (cron/watchdog/--once); `schedule start/stop/status`; PID/log/status-file lifecycle; `schedule` in KNOWN_COMMANDS; 38+7 regression tests; 1112 pass.
- `2026-05-03`: WP74 is complete; `tui_operator.py` multi-screen Textual operator console — 17 screens, AgentsScreen, SkillsScreen, DiffReviewScreen, EstimateScreen, search filter in SavedPlansScreen, `operator` subcommand; 1074 pass.
- `2026-05-03`: WP73 is complete; wizard saved plans browser, effort selection (low/medium/high → haiku/sonnet/opus), expanded checkpoint, `--planner-effort` flag, 16 regression tests; 1070 pass.
- `2026-05-03`: WP72 is complete; OTEL endpoint validated at startup; 21 new orchestrator dispatch tests; 1054 pass.
- `2026-05-03`: WP69 is complete; planner is first wizard stage with clarification, staged setup, approve/edit, handoff into run.
- `2026-05-03`: WP68 is complete; one shared Matrix theme now covers the persistent console, run dashboard, and wizard prompts.
- `2026-05-03`: WP60, WP50, WP59, WP67, WP58, WP57, WP55 complete.
- `2026-05-04`: WP79 added — TUI cross-platform & UX polish; `HomeScreen` cursor-aware [Enter]; `MemoryToolsScreen` POSIX fallback; `PlanEditorScreen` cross-platform editor; `GovernanceScreen` inline validation.
- `2026-05-04`: WP78 added — LLM provider plugin system; `LLMProvider` Protocol; `ProviderRegistry`; OpenAI-compatible reference impl; per-agent/task `provider` field; multi-provider rate-limit buckets; TUI provider selector.
- `2026-05-04`: WP77 added — multi-workspace codebase targeting; per-plan `codebasePath`/`workspaceStrategy`; global `workspace.root`; scratch/copy/repo modes; TUI workspace picker; prune integration.
- `2026-05-04`: Queue is WP75 -> WP76 -> WP77 -> WP78 -> WP79 -> WP40 -> Release Gate.

## Facts
- `2026-05-03`: `tui_operator.py` is the multi-screen operator TUI; `operator` subcommand launches it; `"operator"` in `KNOWN_COMMANDS`; DiffReviewScreen wired to promote flow; AgentsScreen/SkillsScreen on [A]/[K]; EstimateScreen on [D].
- `2026-05-03`: `console.py` owns session state and shared routing (`DispatchRoute`/`route_line`).
- `2026-05-03`: `tui_console.py` owns all Textual UI; `tui_app.py` owns the run dashboard and `textual_is_available`; `wizard.py` is pure logic.
- `2026-05-03`: Planner is first wizard stage inside `wizard`; clarification loop → staged setup proposal → approve/edit checkpoint → execution handoff.
- `2026-05-03`: Saved plans live in `runtime_root/saved-plans/*.json`; `discover_saved_plans`/`save_plan_to`/`_saved_plans_flow` own the browser flow.
- `2026-05-03`: Effort → model: `low=haiku-4-5-20251001`, `medium=sonnet-4-6`, `high=opus-4-7`; stored in `_EFFORT_MODEL_MAP`.
- `2026-05-03`: Empty wizard goal triggers saved-plans browser; `--planner-effort` CLI flag skips interactive prompt.
- `2026-05-03`: Shared Matrix theme lives in `_MTX_VARS` and `get_css_variables()` on the Textual apps.
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
