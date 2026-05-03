# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP59 -> WP50 -> WP56 -> WP61 -> WP60 -> WP62 -> WP63 -> WP64 -> WP65 -> WP66 -> WP40 (always last) -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-03`: WP67 is complete; ownership model across all four interactive modules settled; `route_line`/`DispatchRoute` shared; all Textual classes in `tui_console.py`; wizard pure logic; 697 tests passed.
- `2026-05-03`: WP59 is next; add `test_tui_console.py` headless Textual tests now that ownership is settled.
- `2026-05-03`: WP58 is complete; persistent `console` ships `/help`, `/jobs`, `/focus`, `/clear`, `/exit`, inline command routing, and background `run`/`resume`/`retry`; 697 tests passed.
- `2026-05-03`: WP57 is complete; `diff-run` now covers retained workspace-vs-repo diff/stat output and the wizard promote gate.
- `2026-05-02`: WP55 is complete; the default operator flow is the guided wizard with plan inventory, goal routing, and inline review/promote/validate plus `--resume` / `--retry`.
- `2026-05-03`: Queue is WP67 -> WP59 -> WP50 -> WP56 -> WP61 -> WP60 -> WP62 -> WP63 -> WP64 -> WP65 -> WP66 -> WP40 (always last) -> Release Gate. WP18 removed.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `wizard.py` owns the no-args flow and keeps generated goal plans under `.claude-orchestrator/generated-plans/`.
- `2026-05-03`: `console.py` owns the plain persistent console; `dispatch_line` routes commands and `run_console_session` owns the REPL loop.
- `2026-05-03`: `console.py` = session state + shared routing (`DispatchRoute`/`route_line`); `tui_console.py` = ALL Textual UI; `tui_app.py` = run-scoped dashboard + canonical `textual_is_available`; `wizard.py` = pure logic.
- `2026-05-03`: `tui_console.ConsoleApp._dispatch` calls `route_line` from `console.py`; no routing duplication between plain and TUI surface.
- `2026-05-03`: `diff_run.py` owns retained-run human diff rendering; `command_dispatch.print_payload(...)` honors `_consoleText` without polluting `--json`.
- `2026-05-02`: `hitl.py` owns HITL policy resolution; provider mode still auto-detects SDK when `anthropic` and `ANTHROPIC_API_KEY` are present, otherwise subprocess.
- `2026-05-03`: WP67 complete; WP59 and WP60 unblocked; both depend on the consolidated ownership model now in place.
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
