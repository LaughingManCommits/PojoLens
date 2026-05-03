# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP50 -> WP56 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-03`: WP58 is complete; `console.py` adds persistent operator session; `run`/`resume`/`retry` are background jobs; `/help`,`/jobs`,`/focus`,`/clear`,`/exit` wired; all orchestrator commands route inline; `console` in KNOWN_COMMANDS and cli_parser; 697 tests pass.
- `2026-05-03`: WP57 is complete; `diff-run` now provides retained workspace-vs-repo diff/stat output with task/path filters and is wired into the wizard promote gate.
- `2026-05-02`: WP55 is complete; `pojolens-agents` now defaults to a guided wizard with plan inventory, optional natural-language routing, and inline review/promote/validate plus `--resume` / `--retry`.
- `2026-05-02`: Keep the current orchestrator stack intact: TUI/watch modes, follow-up injection, cost estimation, HITL gates, retry, and typed retained-run contracts.
- `2026-05-03`: Next queue is WP58, then WP50, then WP56, then WP40, deferred WP18, and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `wizard.py` owns the no-args operator flow; it preprocesses argv before argparse, wraps validate/run/review/promote/validate-run handlers directly, and keeps generated goal plans under `.claude-orchestrator/generated-plans/`.
- `2026-05-03`: `console.py` owns the persistent operator console; `run`/`resume`/`retry` are background threads via `ConsoleJob`; `dispatch_line` is the per-line router; `run_console_session` owns the REPL loop; `orchestrator_app._build_handlers()` owns the shared handler dict and bypasses `dispatch_main` for `console`.
- `2026-05-03`: `diff_run.py` owns retained-run human diff rendering plus task/path filter parsing; `command_dispatch.print_payload(...)` now honors a private `_consoleText` field for human text mode without polluting `--json` output.
- `2026-05-02`: `hitl.py` owns HITL policy resolution; provider mode still auto-detects SDK when `anthropic` and `ANTHROPIC_API_KEY` are present, otherwise subprocess.
- `2026-05-03`: `TODO.md` order is WP50, WP56, WP40, deferred WP18, Release Gate.
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
