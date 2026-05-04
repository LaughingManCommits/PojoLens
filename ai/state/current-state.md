# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
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
- `2026-05-04`: Roadmap order is WP77 -> WP78 -> WP79 -> WP40 -> Release Gate.
