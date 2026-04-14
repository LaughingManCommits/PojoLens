# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-10`: CSV is complete through `CSV-WP5`; `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP18`; spike is fully closed.
- `2026-04-13`: time buckets, aggregate windows/ordering, immutable fluent
  prepare, SQL-like subqueries/`EXISTS`, and bounded natural cleanup are done.
- `2026-04-14`: fluent/core bounded subquery predicates are done for
  `addInSubquery(...)`, `addExists(...)`, and `addNotExists(...)`.

## Verified

- `2026-04-10`: CSV WP1-WP5 is validated, including multiline records, runtime defaults, coercion policy, load reports, enum binding, and guarded load benchmarks.
- `2026-04-11`: orchestration WP18 and `caveman` skill propagation are validated.
- `2026-04-11`: SQL-like subqueries support grouped and aggregate outputs.
- `2026-04-12`: bounded aggregate windows and aggregate `ORDER BY` diagnostic polish passed module tests and docs consistency.
- `2026-04-13`: `PojoLensCore.prepare(...)` now returns immutable `FluentQueryDefinition<T>` for reusable fluent builder recipes with rows/schema/explain and `ReportDefinition` promotion.
- `2026-04-13`: SQL-like `WHERE ... IN (select ...)` subqueries now allow uncorrelated `JOIN` clauses over `JoinBindings`; focused SQL-like tests, full `pojo-lens` tests, Checkstyle, and doc consistency passed.
- `2026-04-13`: SQL-like `WHERE [NOT] EXISTS (select ...)` works for bounded
  self/named/joined sources and passed focused/full validations.
- `2026-04-13`: natural cleanup passed: schema vocabulary resolves for non-join and join-aware overloads, `qualify` accepts controlled inline window phrases, window phrasing supports multiple partitions and supported aggregate frames, and resolved delegates are cached by execution shape.
- `2026-04-14`: fluent/core bounded subquery predicates passed focused public
  API/fluent tests and the full `pojo-lens` module suite (`714` tests).

## Release

- `2026.03.28.1919` is complete.

## Risks

- Fluent/core should lead capability; SQL-like and natural are facades.
- Natural still lacks bounded subquery/existence grammar.
- Natural remains controlled grammar; direct static parse/template entry points intentionally stay runtime-vocabulary-free.

## Next

- Orchestration: spike closed through WP18; revisit only if a new product slice reveals an uncovered gap.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: next useful slice is natural lowering for bounded subqueries;
  correlated/scalar subqueries and broad window-frame parity stay opt-in only.
