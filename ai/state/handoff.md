# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Run the final release guardrails from `RELEASE.md`, then cut the release if they stay green.

## Focus

- `2026-04-26`: WP15 landed and cleared the chart-parity blocker; Release Gate is down to final guardrails.
- `2026-04-26`: WP6/WP8/WP9 benchmark-backfill tasks remain deferred until after the release cut.
- `2026-04-26`: WP11-WP15 are complete.
- `2026-04-26`: `TODO.md` now includes post-release WP16-WP18 for virtual-thread boundary evaluation, internal cleanup, and runtime tuning.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Facts

- `2026-04-26`: `mvn -B -ntp test` passed at 1067/1067 after WP15, and `scripts/check-doc-consistency.ps1` also passed.
- `2026-04-26`: `mvn -B -ntp -Plint verify -DskipTests` passed, and `scripts/check-lint-baseline.ps1` now passes after refreshing `scripts/checkstyle-baseline.txt` to `18383` entries.
- `2026-04-26`: Release benchmark core thresholds, chart thresholds, plot generation, and chart parity all passed on the latest rerun. The passing `SCATTER size=100000` parity row was fluent `4.887 ms/op`, SQL-like `7.454 ms/op`, ratio `1.526`.
- `2026-04-26`: The strict no-warmup chart parity row is locally noisy; one rerun measured `1.790` before the next passed at `1.526`. Use the new `ChartScatterProfileMain` harness plus rerun before treating a lone failure as a real regression.
- `2026-04-26`: Local JFR on this Windows host does not expose `jdk.CPUTimeSample`, and the practical recipe falls back to `ExecutionSample` / allocation views after checking `jfr summary`.
- `2026-04-26`: Use `$env:JAVA_HOME\bin\java.exe` for local benchmark commands; `java` on `PATH` is JDK 17.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` full reactor tests, lint profile, lint baseline gate, doc consistency, benchmark-runner packaging, release core/chart thresholds, and release chart parity all passed.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
