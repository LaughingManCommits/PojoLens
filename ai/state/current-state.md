# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-27`: SpotBugs Java 25 support and the staged Checkstyle baseline are both restored; release guardrails remain next.
- `2026-04-27`: WP20 landed; WP18 and WP19 remain the next post-release backlog.
- `2026-04-26`: WP11-WP17 are complete; WP6/WP8/WP9 benchmark backfill stays deferred until after release.

## Verified

- `2026-04-27`: Checkstyle baseline refresh plus follow-up check passed at `report=18454 baseline=18454 new=0 fixed=0`.
- `2026-04-27`: `mvn -B -ntp -Plint verify -DskipTests`, `-Pstatic-analysis verify -DskipTests`, and `mvn -B -ntp test` all passed.
- `2026-04-27`: WP20 targeted message-contract tests passed at 126/126.
- `2026-04-26`: WP17 targeted dispatch regressions passed at 96/96.
- `2026-04-26`: Release benchmark guardrails and the `SCATTER size=100000` parity rerun passed.

## Release

- Latest cut is `2026.04.17.1834`.
- Release notes now cover Java 25 toolchain alignment, WP5, and the WP11-WP17 follow-ups.
- Remaining release work: run the final release guardrails from `RELEASE.md`.

## Risks

- `2026-04-26`: Virtual threads remain boundary-only. No long-lived JDBC `synchronized` blocks were found, but real-MySQL virtual-profile verification is still pending.
- The Checkstyle profile still reports a large repo-wide public-style backlog; only the staged baseline was refreshed here.
- The strict `-wi 0 -i 1 -r 100ms` chart parity row is locally noisy; one rerun missed at `1.790`, while the next passed at `1.526`. Use the new `ChartScatterProfileMain` harness plus rerun before treating a lone miss as a regression.
- Local JFR on this Windows host does not expose `jdk.CPUTimeSample`; verify available events with `jfr summary` and expect to fall back to `ExecutionSample` / allocation views.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Run the final release guardrails from `RELEASE.md`, then cut the release if they stay green.
- After the release cut, work WP18 runtime-knob evaluation, then WP19 wrapper consolidation.
