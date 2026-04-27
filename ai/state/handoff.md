# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Cut the release from `RELEASE.md` when the user wants it; otherwise continue with WP18.

## Focus

- `2026-04-27`: Redundancy passes complete; `FastPojoRuleSupport` extracted, `isNullOrBlank` consolidation applied at 5 sites, TODO.md cleaned.
- `2026-04-27`: Release cut remains user-controlled; WP18 is next after release.
- `2026-04-26`: WP11-WP17 are complete; WP6/WP8/WP9 benchmark backfill stays deferred until after release.

## Facts

- `2026-04-27`: `pojo-lens/pom.xml` now points `-Plint` at `config/checkstyle/checkstyle.xml`, and the repo-local rule set is clean.
- `2026-04-27`: `scripts/checkstyle-baseline.txt` is now intentionally empty because the current `-Plint` report is empty (`report=0 baseline=0 new=0 fixed=0`).
- `2026-04-27`: `scripts/check-lint-baseline.ps1` now handles zero-violation reports when writing the staged baseline.
- `2026-04-27`: Benchmark guardrail evidence now exists from core/chart suites, chart parity, and plot generation.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` now passes on Java 25 after SpotBugs `4.9.8.3`.
- `2026-04-27`: `SqlLikeFieldMessages` now owns shared SQL-like unknown-field messages, and `NaturalQueryResolutionSupport` reuses the shared helper pair.
- `2026-04-27`: WP20 targeted message-contract tests passed at 126/126.
- `2026-04-26`: The strict no-warmup chart parity row is locally noisy; rerun before treating a lone miss as a regression.
- `2026-04-26`: Local JFR lacks `jdk.CPUTimeSample` on this Windows host, and benchmark commands should use `$env:JAVA_HOME\bin\java.exe`.

## Validate

- After code changes: `mvn -B -ntp test`.
- After lint-baseline refreshes: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`.
- After static-analysis build changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After benchmark guardrail changes: `mvn -B -ntp -Pbenchmark-runner -DskipTests package`, the core suite and threshold check, the chart suite, the chart parity check, and `BenchmarkMetricsPlotGenerator`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-27` `mvn -B -ntp verify`, `-Pstatic-analysis verify -DskipTests`, `-Plint verify -DskipTests`, `-Pbenchmark-runner -DskipTests package`, and the core/chart benchmark guardrails all passed on Java 25.0.2.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
