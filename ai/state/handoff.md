# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP79 -> WP40 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-04`: TUI Provider Selector complete (pre-WP79 insert); `ProviderSelectScreen` wizard step 4/6; AgentsScreen + SettingsScreen show provider info; `--default-provider` CLI; `_DEFAULT_PROVIDER_ID_CTX`; task_execution fallback; 26 tests; 1247 pass.
- `2026-05-04`: WP78 is complete; `provider_plugin.py` (LLMProvider Protocol + data types + exception hierarchy); `provider_registry.py` (singleton, auto-builtins, load_from_config); `providers/` package (anthropic_sdk, subprocess_claude, openai_compat); `provider` field on AgentDefinition+TaskDefinition+models; `load_providers_config()` in config_loader; plugin dispatch in task_execution.py; 30 tests; 1221 pass.
- `2026-05-04`: WP77 is complete; `workspace_manager.py` (new: `prepare_workspace`/`cleanup_workspace`/helper fns); `codebase_path`/`workspace_strategy` in `TaskPlan`+`TaskPlanModel`+`RunManifestModel`; `load_workspace_config()` in `config_loader.py`; `_add_workspace_args()` in `cli_parser.py` (run/resume/wizard parsers); `_WORKSPACE_DIR_CTX` context var in `orchestrator_app.py`; `run_loaded_plan(workspace_dir=)` + `run_plan()` workspace prep+cleanup; manifest `codebasePath`/`workspaceStrategy` fields; 28 new tests; 1191 pass.
- `2026-05-04`: WP76 is complete; `ClarificationScreen` AI backend wired (`clarify_fn` param + `_make_clarify_fn` closure in `OperatorApp`); `SettingsScreen` TPM/RPM + notifications; 5 inspector screens in `_tui_inspect.py`; `PlanDetailsScreen` [T]/[I]/[O]/[P] bindings; 34 tests; 1167 pass.
- `2026-05-04`: WP75 is complete; `_read_gate_manifest` + gate screen live data; `OrchestratorApp` auto-pushes `HitlGateScreen` via `push_screen`+Future; approve/abort/back+sentinel wired; 17 tests; 1129 pass.
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
- `2026-05-04`: Queue is WP79 -> WP40 -> Release Gate. TUI Provider Selector done.

## Facts
- `2026-05-04`: WP78 provider plugin system: `LLMProvider` Protocol (complete/rate_limit_meta/model_pricing/map_usage); registry singleton via `get_registry()`/`reset_registry()`; builtins auto-register at import; `anthropic-sdk` wraps run_sdk_provider; `subprocess-claude` raises NotImplementedError (dispatch stays in task_execution); openai-compat needs `openai` package; `provider` field on agent/task overrides dispatch when registry.has(id) and id not in builtins.
- `2026-05-04`: `workspace_manager.py` strategies: `repo` (return codebasePath or cwd), `copy` (shutil.copytree → workspace_root/slug/run_id/), `scratch` (empty dir). `_WORKSPACE_DIR_CTX` ContextVar set inside `_run_inner` coroutine so it's scoped per-run; `execute_task` reads it via `_WORKSPACE_DIR_CTX.get() or ROOT`.
- `2026-05-04`: `ClarificationScreen` takes optional `clarify_fn: Callable[[str], dict] | None`; `OperatorApp._make_clarify_fn()` builds the closure; `_ai_questions`/`_ai_refined_goal` fields; static `_CLARIF_QUESTIONS` fallback when no AI.
- `2026-05-04`: Inspector screens in `_tui_inspect.py`: `ExtraToolsScreen`, `ValidationIntentsScreen`, `OutputProfilesScreen`, `FollowUpTaskScreen`, `PromptAccountingScreen`; all wired into `PlanDetailsScreen` [T]/[I]/[O]/[P]; re-exported from `tui_operator.py`.
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
