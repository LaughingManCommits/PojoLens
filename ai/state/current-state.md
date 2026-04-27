# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-27`: `-Pstatic-analysis` now uses SpotBugs `check` with `failThreshold=High`; the four high-severity findings were fixed and the remaining 77 warnings stay advisory in the XML report.
- `2026-04-27`: Redundancy second pass complete; `FastPojoRuleSupport` extracted, 5-site `isNull(x.trim())` policy clone fixed with `isNullOrBlank`.
- `2026-04-27`: WP20 landed; WP18 remains the next post-release backlog.
- `2026-04-26`: WP11-WP17 are complete; WP6/WP8/WP9 benchmark backfill stays deferred until after release.

## Verified

- `2026-04-27`: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`, `mvn -B -ntp test`, and `mvn -B -ntp -Plint verify -DskipTests` passed after enabling SpotBugs `check` at `failThreshold=High` and fixing the high-severity findings in `NaturalQuery`, `ObjectUtil`, and `FilterQueryBuilder`.
- `2026-04-27`: `mvn -B -ntp verify`, `-Pstatic-analysis verify -DskipTests`, `-Plint verify -DskipTests`, `-Pbenchmark-runner -DskipTests package`, and the core/chart benchmark guardrails all passed on Java 25.0.2.
- `2026-04-27`: Benchmark evidence now exists under `target/benchmarks/**` after the core suite, chart suite, chart parity, and plot generation passed.
- `2026-04-27`: `-Plint verify -DskipTests`, the refreshed baseline check, and `mvn -B -ntp test` all passed after switching `-Plint` to `config/checkstyle/checkstyle.xml` and fixing the zero-violation baseline write path.
- `2026-04-27`: WP20 targeted message-contract tests passed at 126/126.
- `2026-04-26`: WP17 targeted dispatch regressions passed at 96/96.

## Release

- Latest cut is `2026.04.17.1834`.
- Release notes now cover Java 25 toolchain alignment, WP5, and the WP11-WP17 follow-ups.
- Remaining release work: cut the release from `RELEASE.md` when user timing is set.

## Risks

- `2026-04-26`: Virtual threads remain boundary-only. No long-lived JDBC `synchronized` blocks were found, but real-MySQL virtual-profile verification is still pending.
- The strict `-wi 0 -i 1 -r 100ms` chart parity row is locally noisy; one rerun missed at `1.790`, while the next passed at `1.526`. Use the new `ChartScatterProfileMain` harness plus rerun before treating a lone miss as a regression.
- Local JFR on this Windows host does not expose `jdk.CPUTimeSample`; verify available events with `jfr summary` and expect to fall back to `ExecutionSample` / allocation views.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Release cut remains user-controlled; after release, move to WP18 runtime-knob evaluation.
