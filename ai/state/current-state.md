# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-03`: WP58 is complete; `pojolens-agents console` now opens a persistent operator session with `/help`, `/jobs`, `/focus`, `/clear`, `/exit`; all orchestrator commands route inline; `run`/`resume`/`retry` run as managed background jobs; waits for jobs on exit; `console` added to `KNOWN_COMMANDS` and `cli_parser`.
- `2026-05-03`: WP57 is complete; `diff-run` now renders retained workspace-vs-repo diffs with task/path filters, `--stat`, structured JSON payloads, graceful missing-workspace handling, and wizard promote-gate diff preview plus optional full diff display.
- `2026-05-02`: WP55 is complete; `pojolens-agents` now defaults to a guided wizard with tracked-plan inventory, optional natural-language goal routing, inline preflight/run/review/promote/validate flow, and `--resume` / `--retry` wizard entry points.
- `2026-05-02`: Preserve the current orchestrator base: guided wizard entry, TUI fallback, async `--max-parallel`, follow-up injection, cost estimation, HITL gates, low-cost profiles, and typed manifest/task-plan validation.

## Verified
- `2026-05-03`: WP58 validations passed: 58 focused console tests (session lifecycle, /exit, /jobs, /focus, /clear, inline dispatch, background jobs, parse errors, handler exceptions, subcommand parsing, KNOWN_COMMANDS), full Python suite (`697` tests).
- `2026-05-03`: WP57 validations passed: focused diff-run/wizard/CLI tests, full Python suite (`639` tests), `diff-run --stat --json` against a synthetic retained run, and docs consistency checks.
- `2026-05-02`: WP55 validations passed: focused wizard/CLI/run/config tests, `wizard --dry-run --json`, and the full Python suite after fixing the wrapped-run TUI gating and moving generated natural-language plans under runtime state.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-03`: Roadmap order is WP50 -> WP56 -> WP40 -> deferred WP18 -> Release Gate.
