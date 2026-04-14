# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-10`: CSV is complete through `CSV-WP5`; `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP18`; spike is fully closed.
- `2026-04-14`: limitation work is done for time buckets, aggregate windows,
  prepare, bounded subqueries/`EXISTS`, natural cleanup, and SQL-like lowering.

## Verified

- `2026-04-10`: CSV WP1-WP5 is validated, including multiline records, runtime defaults, coercion policy, load reports, enum binding, and guarded load benchmarks.
- `2026-04-11`: orchestration WP18 and `caveman` skill propagation are validated.
- `2026-04-13`: `PojoLensCore.prepare(...)` now returns immutable `FluentQueryDefinition<T>` for reusable fluent builder recipes with rows/schema/explain and `ReportDefinition` promotion.
- `2026-04-13`: SQL-like `IN`/`EXISTS` subqueries work for bounded
  self/named/joined sources and passed focused/full validations.
- `2026-04-13`: bounded natural cleanup passed for schema vocabulary,
  `qualify` window phrases, window phrase breadth/frames, and delegate caching.
- `2026-04-14`: bounded subquery/existence parity passed focused fluent/public
  API and natural/docs-example tests plus the full `pojo-lens` suite
  (`721` tests), docs, diff whitespace, Checkstyle goal, and script tests.
- `2026-04-14`: SQL-like simple bounded `IN`/`EXISTS` predicates now bind as
  fluent/core subquery predicates.
- `2026-04-14`: grouped fluent `QueryRule` subqueries are done, SQL-like
  boolean `OR`/DNF subqueries lower onto fluent/core, and docs are aligned.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Fluent/core should lead capability; SQL-like and natural are facades.
- Bounded subquery/existence parity is user-facing complete across fluent,
  SQL-like, and natural; SQL-like subquery binding now uses the shared
  fluent/core path for direct and grouped boolean shapes.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.

## Next

- Orchestration: spike closed through WP18; revisit only if a new product slice reveals an uncovered gap.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: no default bounded subquery parity slice remains;
  correlated/scalar subqueries and broad window-frame parity stay opt-in only.
