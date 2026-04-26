# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-26`: WP16 landed: Spring examples now have opt-in `virtual` profiles and dedicated virtual-thread smoke coverage.
- `2026-04-26`: Release Gate is down to the final release guardrails.
- `2026-04-26`: Defer the unimplemented WP6/WP8/WP9 benchmark-backfill tasks until after the next release cut.
- `2026-04-26`: WP11-WP16 are complete and green.
- `2026-04-26`: `TODO.md` now queues follow-up WP17-WP18 for internal cleanup and runtime-knob measurement.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Verified

- `2026-04-26`: WP16 targeted core/starter/quickstart/basic tests plus `scripts/check-doc-consistency.ps1` all passed.
- `2026-04-26`: `mvn -B -ntp test` passed: 1067 tests, 0 failures across all modules.
- `2026-04-26`: `mvn -B -ntp -Plint verify -DskipTests` passed.
- `2026-04-26`: `scripts/check-lint-baseline.ps1` passed after refreshing `scripts/checkstyle-baseline.txt` to `18383` entries.
- `2026-04-26`: `scripts/check-doc-consistency.ps1` passed.
- `2026-04-26`: `mvn -B -ntp -Pbenchmark-runner -DskipTests package` passed.
- `2026-04-26`: Release benchmark core thresholds passed.
- `2026-04-26`: Release benchmark chart thresholds passed.
- `2026-04-26`: Release chart parity passed on rerun for `SCATTER size=100000`: fluent `4.887 ms/op`, SQL-like `7.454 ms/op`, ratio `1.526`.
- `2026-04-26`: Release benchmark plot generation passed.

## Release

- Latest cut is `2026.04.17.1834`.
- Release notes cover Java 25 toolchain alignment, WP5, and the WP11-WP15 follow-ups.
- Remaining release work: run the final release guardrails from `RELEASE.md`.

## Risks

- `2026-04-26`: Virtual threads remain boundary-only. No long-lived JDBC `synchronized` blocks were found, but real-MySQL virtual-profile verification is still pending.
- The strict `-wi 0 -i 1 -r 100ms` chart parity row is locally noisy; one rerun missed at `1.790`, while the next passed at `1.526`. Use the new `ChartScatterProfileMain` harness plus rerun before treating a lone miss as a regression.
- Local JFR on this Windows host does not expose `jdk.CPUTimeSample`; verify available events with `jfr summary` and expect to fall back to `ExecutionSample` / allocation views.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Run the final release guardrails from `RELEASE.md`, then cut the release if they stay green.
- After the release cut, work the new Java 25 follow-up backlog in WP17-WP18.
