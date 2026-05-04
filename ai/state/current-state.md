# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-04`: Home dashboard pagination + stats complete; `DashboardWidget` now stores all manifests + `_run_index`; `[◀]`/`[▶]` nav buttons flip through all runs oldest↔newest; `#dash-stats-box` shows `Runs: N  ✓N  ✗N  N▸  Total: $X.XX` updated whenever manifest list changes; `[latest]` tag on run 1; delete after paging removes from cache and advances to next; 25 new tests; 1373 pass.
- `2026-05-04`: Plans pagination + stats box complete; `SavedPlansScreen` paginates at `PAGE_SIZE=15` with `[◀]`/`[▶]` buttons and `Page X/Y (A–B of N)` label; stats bar shows `Plans: N  Tasks: N  Runs: N  Total cost: $X.XX`; `_collect_run_stats` scans all run manifests (`usageTotals.totalCostUsd` with `highestCostTasks` fallback); `[left]`/`[right]` keyboard nav; filter resets to page 0; 26 new tests; 1348 pass.
- `2026-05-04`: Dashboard stop/pause/delete controls complete; `DashboardWidget` gets `[STOP]`/`[PAUSE]`/`[DELETE]` buttons; stop writes `stop.flag`, pause toggles `pause.flag`, delete does `shutil.rmtree` (blocked when running); `execute_task` in `orchestrator_app.py` checks both flags before each task (raises `OrchestratorError` on stop, `asyncio.sleep` loop on pause); 26 new tests; 1322 pass.
- `2026-05-04`: WP79 complete; `HomeScreen` cursor-aware [Enter] (reactive `_cursor`, Up/Down bindings, `watch__cursor` CSS class toggle, `action_activate_item` dispatch map); `MemoryToolsScreen` POSIX fallback (`_memory_cmd` platform helper, `--query`/`--check` on POSIX vs `-Query`/`-Check` on win32); `PlanEditorScreen` editor chain (`$VISUAL` → `$EDITOR` → `code` → platform default, fallthrough on FileNotFoundError); `GovernanceScreen` inline validation (`#budget-err`/`#parallel-err` statics, `on_input_changed`, submit disabled on error); 40 tests; 1296 pass.
- `2026-05-04`: Provider config default wired; `load_default_provider_id()` in config_loader; `_CONFIG_DEFAULT_PROVIDER_ID` module-level var set in `_init_provider_registry`; deps use `_DEFAULT_PROVIDER_ID_CTX.get() or _CONFIG_DEFAULT_PROVIDER_ID`; `ProviderSelectScreen` pre-selects config default; `SettingsScreen` shows `[config default]` tag; priority chain: global config → run-level → agent → task; 1256 pass.
- `2026-05-04`: TUI Provider Selector complete; `ProviderSelectScreen` (wizard step 4/6); `AgentsScreen` shows `provider` field; `SettingsScreen` Providers section (id, class, pricing, rate-limit); `--default-provider` CLI flag; `_DEFAULT_PROVIDER_ID_CTX` ContextVar + `run_loaded_plan(default_provider_id=)` + task_execution fallback; 26 tests; 1247 pass.
- `2026-05-04`: WP78 is complete; `provider_plugin.py` (LLMProvider Protocol, ProviderResult, RateLimitMeta, ModelPricing, exception hierarchy); `provider_registry.py` (singleton, auto-registers builtins, load_from_config); `providers/anthropic_sdk.py`, `providers/subprocess_claude.py`, `providers/openai_compat.py`; `provider` field on AgentDefinition/TaskDefinition/models; `load_providers_config()` in config_loader.py; plugin dispatch in task_execution.py; `_init_provider_registry()` in orchestrator_app.py; 30 tests; 1221 pass.
- `2026-05-04`: WP77 is complete; `workspace_manager.py` (new); `codebase_path`/`workspace_strategy` in `TaskPlan`, `TaskPlanModel`, `RunManifestModel`; `load_workspace_config()` in `config_loader.py`; `_add_workspace_args()` in `cli_parser.py` (run/resume/wizard parsers); `_WORKSPACE_DIR_CTX` context var + `run_loaded_plan(workspace_dir=)` + `run_plan()` workspace prep+cleanup; manifest fields `codebasePath`/`workspaceStrategy`; 28 WP77 tests; 1191 pass.
- `2026-05-04`: WP76 is complete; `ClarificationScreen` AI wiring (`clarify_fn` param + `_make_clarify_fn` closure); `SettingsScreen` TPM/RPM + notification display; 5 inspector screens (`ExtraToolsScreen`, `ValidationIntentsScreen`, `OutputProfilesScreen`, `FollowUpTaskScreen`, `PromptAccountingScreen`); wired into `PlanDetailsScreen` [T]/[I]/[O]/[P]; 34 tests; 1167 pass.
- `2026-05-04`: WP75 is complete; `_read_gate_manifest` + `HitlGateScreen` live manifest data; `OrchestratorApp` auto-pushes gate screen via `push_screen`+Future; approve/abort/back+sentinel all wired; 17 regression tests; 1129 pass.
- `2026-05-04`: WP65 is complete; `schedule.py` with cron/file-watch/--once modes; `schedule start/stop/status` subcommands; `schedule` in KNOWN_COMMANDS; 38 regression tests; 1112 pass.
- `2026-05-03`: WP74 is complete; `tui_operator.py` multi-screen Textual operator console fully wired -- 17 screens, AgentsScreen, SkillsScreen, DiffReviewScreen, EstimateScreen, search filter, `operator` subcommand.
- `2026-05-03`: WP73 is complete; wizard now supports saved plans browser, effort selection (low/medium/high → haiku/sonnet/opus), expanded checkpoint options, and `--planner-effort` CLI flag.
- `2026-05-03`: WP72 is complete; OTEL endpoint validated at startup; 21 new orchestrator dispatch tests.
- `2026-05-03`: WP69 is complete; planner is the first wizard stage with clarification, staged setup, approve/edit, and handoff into run.
- `2026-05-03`: WP68 is complete; one shared Matrix theme now covers `tui_console.py`, `tui_app.py`, and wizard prompts.
- `2026-05-03`: WP60, WP50, WP59, WP67, WP58, WP57, WP55 complete.

## Verified
- `2026-05-04`: Full Python suite at `1373` tests after home dashboard pagination + stats.
- `2026-05-04`: Full Python suite at `1348` tests after plans pagination + stats.
- `2026-05-04`: Full Python suite at `1322` tests after dashboard controls.
- `2026-05-04`: Full Python suite at `1296` tests after WP79.
- `2026-05-04`: Full Python suite at `1256` tests after provider config default wiring.
- `2026-05-04`: Full Python suite at `1247` tests after TUI Provider Selector.
- `2026-05-04`: Full Python suite at `1221` tests after WP78.
- `2026-05-04`: Full Python suite at `1191` tests after WP77.
- `2026-05-04`: Full Python suite at `1167` tests after WP76.
- `2026-05-04`: Full Python suite at `1129` tests after WP75.
- `2026-05-04`: Full Python suite at `1112` tests after WP65.
- `2026-05-03`: Full Python suite at `1074` tests after WP74.
- `2026-05-03`: Full Python suite at `1070` tests after WP73.
- `2026-05-03`: `scripts/docs/check-doc-consistency.ps1` passed.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active orchestrator risks beyond the remaining roadmap queue.

## Next
- `2026-05-04`: Roadmap order is WP40 -> Release Gate. Dashboard controls + WP79 done.
