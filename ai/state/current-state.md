# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-10`: CSV is complete through `CSV-WP5`; `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP18`; spike is fully closed.
- `2026-04-13`: grouped/aggregate subquery widening, time-bucket input broadening, bounded aggregate window frames, aggregate `ORDER BY` diagnostic polish, and immutable fluent prepared definitions are done.

## Verified

- `2026-04-10`: CSV load covers multiline records, runtime defaults, coercion policy, report diagnostics, enum binding, and guarded load benchmarks.
- `2026-04-10`: WP18 closed reviewer-visible new-file materialization: WP17 reviewer now uses `apply-reviewed` with `docs/csv.md` in readPaths.
- `2026-04-09`: worker validation hints now mirror approved entrypoints, and retained `WP17` runs proved accepted `tool: mvn ...`.
- `2026-04-11`: Claude subagent definitions now preserve optional `skills`, and tracked orchestrator agents preload repo-local `caveman` through the generated `--agents` payload.
- `2026-04-11`: live `spike-limitations-subquery-widening` passed after aggregate subquery validation and grouped-alias fixes.
- `2026-04-11`: focused `pojo-lens` validation/contract tests and the full `pojo-lens` module test suite passed after the subquery widening fix.
- `2026-04-11`: time-bucket implementation/docs/tests already support `Date`, `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, and `ZonedDateTime`.
- `2026-04-11`: public docs now keep `README.md` as a feature-set map while detailed SQL-like/fluent behavior lives in module docs.
- `2026-04-11`: docs consistency and `SqlLikeDocsExamplesTest` passed after the public docs alignment.
- `2026-04-12`: aggregate SQL-like/fluent windows now support running, `<n> PRECEDING`, and full-partition `ROWS` frames; module tests and docs consistency passed.
- `2026-04-12`: aggregate `ORDER BY` validation now reports known raw source fields as invalid aggregate references while typos keep unknown-field suggestions.
- `2026-04-13`: `PojoLensCore.prepare(...)` now returns immutable `FluentQueryDefinition<T>` for reusable fluent builder recipes with rows/schema/explain and `ReportDefinition` promotion.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- No known orchestration gaps; reviewer materialization of new files is now covered by `apply-reviewed`.

## Next

- Orchestration: spike closed through WP18; revisit only if a new product slice reveals an uncovered gap.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: uncorrelated joined subqueries are the only remaining spike candidate, and only if concrete `JoinBindings` demand appears.
