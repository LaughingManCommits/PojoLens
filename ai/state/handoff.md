# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Run the final release guardrails from `RELEASE.md`; if they fail, continue with WP17.

## Focus

- `2026-04-26`: WP16 landed. Spring examples now have opt-in `virtual` profiles and dedicated virtual-thread smoke coverage.
- `2026-04-26`: Release Gate is down to final guardrails.
- `2026-04-26`: WP6/WP8/WP9 benchmark-backfill tasks remain deferred until after the release cut.
- `2026-04-26`: WP11-WP16 are complete.
- `2026-04-26`: `TODO.md` now includes follow-up WP17-WP18 for internal cleanup and runtime tuning.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Facts

- `2026-04-26`: Starter/basic/quickstart runtime endpoints now expose `virtualThreadsEnabled` and `requestThreadVirtual`.
- `2026-04-26`: Dedicated virtual-mode tests passed under Spring Boot 4.0.4.
- `2026-04-26`: `QueryCancellationToken.ofThread(...)` now has virtual-thread coverage, and docs state that it binds to one specific thread.
- `2026-04-26`: The Spring/JDBC pinning audit found no long-lived `synchronized` sections around blocking JDBC calls.
- `2026-04-26`: The remaining synchronized code in the risk-console example is the in-memory telemetry buffer only.
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
- Last validation: `2026-04-26` WP16 targeted core/starter/quickstart/basic tests plus `scripts/check-doc-consistency.ps1` all passed. Earlier that day the full reactor, lint, benchmark-runner, and release guardrails also passed.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
