# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-10`: CSV is complete through `CSV-WP5`; `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP18`.
- `2026-04-14`: limitation work is done for time buckets, aggregate windows,
  prepare, bounded subqueries/`EXISTS`, natural cleanup, and SQL-like lowering.
- `2026-04-15`: scatter allocation follow-up added
  `ReflectionUtil.DirectFieldReadPlan` for direct POJO chart reads and reran a
  `size=1000` warmed scatter GC spot check.

## Verified

- `2026-04-10`: CSV WP1-WP5 is validated, including guarded load benchmarks.
- `2026-04-11`: orchestration WP18 and skill propagation are validated.
- `2026-04-14`: bounded subquery/existence parity is closed across fluent,
  SQL-like, and natural.
- `2026-04-14`: grouped fluent `QueryRule` subqueries are done; SQL-like
  boolean `OR`/DNF subqueries lower onto fluent/core.
- `2026-04-15`: direct POJO chart mapping now avoids primitive x-value boxing
  for direct fields and reuses boxed y-values where possible; `size=1000`
  scatter spot check measured fluent `259,400 B/op`, direct SQL-like
  `284,273 B/op`, and bound SQL-like `283,737 B/op`.

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
- Benchmarks: rerun warmed `10k`/`100k` scatter GC checks only if continuing
  the residual chart allocation thread.
