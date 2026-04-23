# Current State

## Repo

- Java 17 multi-module library build with runtime, Spring Boot, and JMH modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-23`: `STRAT-WP5` is complete: reflection plans reuse cached read/write paths, repeated joins reuse prepared fast join state for stable snapshots, window execution trims warmed allocation overhead, and batch/columnar evaluation closed with a row-oriented decision.
- `2026-04-23`: `STRAT-WP4` complete: pushdown preview, host adapter bridge, JDBC `ResultSet` materialization, split completion, `PUSHDOWN` telemetry, and benchmark coverage shipped.
- `2026-04-23`: `STRAT-WP3` complete: stable typed DSL for projection, filtering, ordering, offset/limit, explain/schema, and guard interop; grouping/joins/windows remain deferred.
- `2026-04-23`: `STRAT-WP2` complete: guards cover lazy/bound execution, include join-source scan budgets, and expose cooperative cancellation plus deterministic aborted-query metadata.
- `2026-04-22`: Product direction is embedded reporting plus governed in-memory query execution.
- `2026-04-22`: `STRAT-WP1` shipped the saved-report contract.
- `2026-04-20`: `QOL-WP1` through `QOL-WP5` are complete.

## Verified

- `2026-04-23`: WP5 reflection slice passed `mvn -B -ntp -pl pojo-lens "-Dtest=ReflectionUtilTest,ReflectionUtilEnumFieldTest" test`: 14 tests, 0 failures.
- `2026-04-23`: warmed forked reflection hotspot guardrails passed via `BenchmarkThresholdChecker` against `benchmarks/hotspot-thresholds.json`.
- `2026-04-23`: WP5 join/window slice passed focused tests for `FilterImpl` fast-path reuse plus fluent/SQL-like window behavior: 40 tests, 0 failures.
- `2026-04-23`: warmed forked join spot checks and warmed window diagnostics passed after the WP5 join/window slice.
- `2026-04-23`: `mvn -B -ntp -pl pojo-lens-benchmarks -am test` passed after completing WP5: 1018 core tests and 16 benchmark-module tests, 0 failures.
- `2026-04-23`: Full Maven suite after completing WP5 passed: 1041 tests across all modules, 0 failures.
- `2026-04-23`: `scripts/check-doc-consistency.ps1`, `git diff --check`, `scripts/refresh-ai-memory.ps1`, and `scripts/refresh-ai-memory.ps1 -Check` passed after WP5 docs/memory alignment.

## Release

- Latest cut is `2026.04.17.1834`.

## Risks

- SQL-like is the public default; natural remains controlled grammar; fluent stays internal.
- Pushdown stays host-owned beyond planning/materialization; PojoLens does not own SQL rendering, DB execution, or authorization.

## Next

- Start release preparation.
- Keep `CSV-WP6`, correlated/scalar subqueries, and broad window-frame parity out of scope.
