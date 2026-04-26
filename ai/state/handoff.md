# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Resolve the remaining release blocker: chart parity failure for `SCATTER size=100000`, then rerun the final guardrails.

## Focus

- `2026-04-26`: Release Gate is blocked only by chart parity failure for `SCATTER size=100000`.
- `2026-04-26`: WP6/WP8/WP9 benchmark-backfill tasks and WP11 are both deferred until after the release cut.
- `2026-04-26`: WP12-WP14 are complete.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Facts

- `2026-04-26`: `mvn -B -ntp test` passed at 1066/1066, and `scripts/check-doc-consistency.ps1` passed.
- `2026-04-26`: `mvn -B -ntp -Plint verify -DskipTests` passed, and `scripts/check-lint-baseline.ps1` now passes after refreshing `scripts/checkstyle-baseline.txt` to `18328` entries.
- `2026-04-26`: Release benchmark core thresholds, chart thresholds, and plot generation all passed.
- `2026-04-26`: `ChartParityChecker` failed for `SCATTER size=100000` with ratio `2.411 > 1.750`.
- `2026-04-26`: Use `$env:JAVA_HOME\bin\java.exe` for local benchmark commands; `java` on `PATH` is JDK 17.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` full reactor tests, lint profile, lint baseline gate, and doc consistency passed; release core/chart thresholds and plot generation passed; chart parity still failed as noted above.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
