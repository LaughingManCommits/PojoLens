# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Run the final release guardrails from `RELEASE.md`; cut the release if green, otherwise fix the blocker before WP18.

## Focus

- `2026-04-26`: WP17 landed. Internal Java 25 cleanup replaced repetitive utility/cursor dispatch with non-preview switch expressions and refreshed targeted regressions.
- `2026-04-26`: Release Gate is down to final guardrails.
- `2026-04-26`: WP6/WP8/WP9 benchmark-backfill tasks remain deferred until after the release cut.
- `2026-04-26`: WP11-WP17 are complete.
- `2026-04-26`: `TODO.md` now leaves WP18 as the remaining post-release Java 25 follow-up.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Facts

- `2026-04-26`: `SqlLikeCursor`, `TimeBucketUtil`, `ChartValidation`, `ChartResultMapper`, `ReportComparisons`, and `ObjectUtil.DateFormatPlan` now use non-preview switch-based internal dispatch; selected `FilterQueryBuilder` helpers now share one `QueryRow` source check path.
- `2026-04-26`: WP17 targeted `mvn -B -ntp -pl pojo-lens "-Dtest=SqlLikeKeysetCursorTest,ChartResultMapper*Test,TimeBucket*Test,*Comparison*Test,ObjectUtilTest,FilterQueryBuilderSelectiveMaterializationTest" test` passed at 96/96.
- `2026-04-26`: `mvn -B -ntp test` passed at 1074/1074 after WP17, and `mvn -B -ntp -Plint verify -DskipTests` also passed.
- `2026-04-26`: `scripts/check-lint-baseline.ps1` still passes with `scripts/checkstyle-baseline.txt` at `18383` entries.
- `2026-04-26`: Release benchmark core thresholds, chart thresholds, plot generation, and chart parity all passed on the latest rerun. The passing `SCATTER size=100000` parity row was fluent `4.887 ms/op`, SQL-like `7.454 ms/op`, ratio `1.526`.
- `2026-04-26`: The strict no-warmup chart parity row is locally noisy; one rerun measured `1.790` before the next passed at `1.526`. Use the new `ChartScatterProfileMain` harness plus rerun before treating a lone failure as a real regression.
- `2026-04-26`: Local JFR on this Windows host does not expose `jdk.CPUTimeSample`, and the practical recipe falls back to `ExecutionSample` / allocation views after checking `jfr summary`.
- `2026-04-26`: Use `$env:JAVA_HOME\bin\java.exe` for local benchmark commands; `java` on `PATH` is JDK 17.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` WP17 targeted pojo-lens dispatch tests (96/96), the full reactor (1074/1074), and `mvn -B -ntp -Plint verify -DskipTests` all passed.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
