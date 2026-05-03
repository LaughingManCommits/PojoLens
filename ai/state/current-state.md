# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-03`: WP60 is complete; SDK provider streaming active via `on_partial_text` callback; `orchestrator_app` uses `_PARTIAL_FACTORY_CTX` contextvar (thread-safe); `--watch` streams tokens to stderr; TUI streams tokens to `LogPane` via `call_soon_threadsafe`; `_streaming_active` flag suppresses tail-poll during streaming; `stream_to_stderr` backward compat preserved; 25 streaming tests; 824 total pass.
- `2026-05-03`: WP50 is complete (+ review fixes): `rate_limiter.py` ships `RateLimitBucket`; two key-name bugs fixed (`totalTokens["max"]` for budget, `inputTokens+outputTokens` for actual); `record_completion` made async+locked; `--tpm-limit`/`--rpm-limit` wired to wizard subparser and piped through all wizard `_namespace` branches; follow-up task budget recomputed after injection; 37 tests; 799 total pass.
- `2026-05-03`: WP59 is complete; `test_tui_console.py` added with 65 tests covering `_ThreadLocalStdout`, `_capture`, `_payload_text`, all `_dispatch` routes, bg/inline worker paths, history navigation, `_ExitConfirmModal`, and exit flow; 762 tests pass.
- `2026-05-03`: WP67 is complete; ownership is clear across all four interactive modules; `route_line`/`DispatchRoute` shared routing eliminates dispatch duplication; all Textual classes unified in `tui_console.py`; wizard.py is pure logic; 697 tests pass.
- `2026-05-03`: WP58 is complete; `pojolens-agents console` now opens a persistent operator session with `/help`, `/jobs`, `/focus`, `/clear`, `/exit`; all orchestrator commands route inline; `run`/`resume`/`retry` run as managed background jobs; waits for jobs on exit; `console` added to `KNOWN_COMMANDS` and `cli_parser`.
- `2026-05-03`: WP57 is complete; `diff-run` now renders retained workspace-vs-repo diffs with task/path filters, `--stat`, structured JSON payloads, graceful missing-workspace handling, and wizard promote-gate diff preview plus optional full diff display.
- `2026-05-02`: WP55 is complete; `pojolens-agents` now defaults to a guided wizard with tracked-plan inventory, optional natural-language goal routing, inline preflight/run/review/promote/validate flow, and `--resume` / `--retry` wizard entry points.
- `2026-05-02`: Preserve the current orchestrator base: guided wizard entry, TUI fallback, async `--max-parallel`, follow-up injection, cost estimation, HITL gates, low-cost profiles, and typed manifest/task-plan validation.

## Verified
- `2026-05-03`: WP60 validated: 25 focused streaming tests (sdk_provider, task_execution factory injection, orchestrator_app factory fns, tui streaming events), full Python suite (`824` tests).
- `2026-05-03`: WP50 + review fixes validated: two key-name bugs fixed, record_completion async+locked, wizard wired, follow-up recompute added; 37 focused tests + full suite (`799` tests).
- `2026-05-03`: WP59 validations passed: 65 focused `tui_console` tests (routing, workers, history, modal, exit flow), full Python suite (`762` tests).
- `2026-05-03`: WP67 validations passed: ownership model established, `route_line` shared routing, wizard Textual classes moved to `tui_console.py`, `textual_is_available` consolidated to `tui_app`, full Python suite (`697` tests).
- `2026-05-03`: WP58 validations passed: 58 focused console tests (session lifecycle, `/exit`, `/jobs`, `/focus`, `/clear`, inline dispatch, background jobs, parse errors, handler exceptions, subcommand parsing, `KNOWN_COMMANDS`), full Python suite (`697` tests).
- `2026-05-03`: WP57 validations passed: focused diff-run/wizard/CLI tests, full Python suite (`639` tests), `diff-run --stat --json` against a synthetic retained run, and docs consistency checks.
- `2026-05-02`: WP55 validations passed: focused wizard/CLI/run/config tests, `wizard --dry-run --json`, and the full Python suite after fixing the wrapped-run TUI gating and moving generated natural-language plans under runtime state.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-03`: Roadmap order is WP56 -> WP61 -> WP62-WP66 -> WP40 (always last before Release Gate) -> Release Gate.
