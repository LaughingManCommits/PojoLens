# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- `2026-04-14`: limitation work done: bounded windows, aggregate ORDER BY, prepare, bounded subqueries/EXISTS, natural cleanup, SQL-like lowering.
- `2026-04-15`: `ReflectionUtil` cleanup complete (RU-WP1 through RU-WP5).
- `2026-04-16`: `FastArrayQuerySupport` cleanup complete; FA-WP1, FA-WP2, FA-WP5, FA-WP6 done; FA-WP4 dropped (HashMap intentional for performance — LinkedHashMap adds linked-list overhead on a hot lookup index).

## Verified

- `2026-04-10`: CSV WP1-WP5 validated including guarded load benchmarks.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-14`: grouped fluent `QueryRule` subqueries done; SQL-like OR/DNF subqueries lower onto fluent/core.
- `2026-04-15`: scatter spot check: fluent `259,400 B/op`, direct SQL-like `284,273 B/op`, bound SQL-like `283,737 B/op`.
- `2026-04-15`: warmed `10k`/`100k` scatter GC baselines recorded; scatter allocation concern retired.
- `2026-04-15`: core guardrail suite passed clean after all cleanup changes.
- `2026-04-16`: `FastArrayQuerySupport` FA-WP5/WP6 — `mvn test` + core guardrail suite + threshold checker all passed clean.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Fluent/core should lead capability; SQL-like and natural are facades.
- Bounded subquery/existence parity user-facing complete; SQL-like binding uses shared fluent/core path.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.

## Next

- `FastArrayQuerySupport` cleanup complete; `TODO.md` cleared.
- Orchestration: revisit only if a new product slice reveals an uncovered gap.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
