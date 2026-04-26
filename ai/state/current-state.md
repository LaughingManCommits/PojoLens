# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-26`: Release Gate is now blocked only by chart parity failure for `SCATTER size=100000`.
- `2026-04-26`: Defer the unimplemented WP6/WP8/WP9 benchmark-backfill tasks until after the next release cut.
- `2026-04-26`: Defer WP11 until after the next release cut.
- `2026-04-26`: WP12-WP14 are complete and green.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Verified

- `2026-04-26`: `mvn -B -ntp test` passed: 1066 tests, 0 failures across all modules.
- `2026-04-26`: `mvn -B -ntp -Plint verify -DskipTests` passed.
- `2026-04-26`: `scripts/check-lint-baseline.ps1` passed after refreshing `scripts/checkstyle-baseline.txt` to `18328` entries.
- `2026-04-26`: `scripts/check-doc-consistency.ps1` passed.
- `2026-04-26`: Release benchmark core thresholds passed.
- `2026-04-26`: Release benchmark chart thresholds passed.
- `2026-04-26`: Release benchmark plot generation passed.

## Release

- Latest cut is `2026.04.17.1834`.
- Release notes now cover Java 25 toolchain alignment, the WP5 performance work, and the WP12-WP14 correctness fixes.
- Remaining blocker: chart parity failure for `SCATTER size=100000`.

## Risks

- `ChartParityChecker` fails for `SCATTER size=100000` with SQL-like/fluent ratio `2.411 > 1.750`.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Investigate or rebaseline the `SCATTER size=100000` parity failure, then rerun the chart parity gate.
- Re-run the remaining release guardrails after both blockers clear.
