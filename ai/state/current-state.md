# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- `2026-04-14`: limitation work done: bounded windows, aggregate ORDER BY, prepare, bounded subqueries/EXISTS, natural cleanup, SQL-like lowering.
- `2026-04-15`: `ReflectionUtil` cleanup complete (RU-WP1 through RU-WP5).
- `2026-04-16`: `FastArrayQuerySupport` cleanup complete; FA-WP1, FA-WP2, FA-WP5, FA-WP6 done; FA-WP4 dropped (HashMap intentional for performance).
- `2026-04-17`: `PojoLensTree` complete (TREE-WP1 through TREE-WP3): flat parent-ID row shaping, tests, docs, and public surface updates.
- `2026-04-17`: ecosystem-positioning follow-up complete: binary-compat guardrails cover tree contracts; README, benchmarking, SQL-like, natural, and release docs now state usage boundaries and input-safety guidance.

## Verified

- `2026-04-10`: CSV WP1-WP5 validated including guarded load benchmarks.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-14`: grouped fluent `QueryRule` subqueries done; SQL-like OR/DNF subqueries lower onto fluent/core.
- `2026-04-15`: scatter spot check: fluent `259,400 B/op`, direct SQL-like `284,273 B/op`, bound SQL-like `283,737 B/op`.
- `2026-04-15`: warmed `10k`/`100k` scatter GC baselines recorded; scatter allocation concern retired.
- `2026-04-15`: core guardrail suite passed clean after all cleanup changes.
- `2026-04-16`: `FastArrayQuerySupport` FA-WP5/WP6 - `mvn test` + core guardrail suite + threshold checker all passed clean.
- `2026-04-17`: `PojoLensTree` validated with `mvn -B -ntp -pl pojo-lens test`, `scripts/check-doc-consistency.ps1`, and full `mvn -B -ntp test`.
- `2026-04-17`: positioning/release-readiness docs validated with `mvn -B -ntp test`, `scripts/check-doc-consistency.ps1`, `git diff --check`, and binary compatibility smoke; lint baseline gate remains blocked by stale repo-wide baseline drift.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Fluent/core should lead capability; SQL-like and natural are facades.
- Bounded subquery/existence parity user-facing complete; SQL-like binding uses shared fluent/core path.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only; it must not grow parser syntax, graph algorithms, ORM behavior, or a second query engine.
- User-authored SQL-like/natural text should use params, approved field/source exposure, lint mode, strict typing, and separate authorization before execution.

## Next

- Release: cut next version to ship FA/RU cleanup, `PojoLensTree`, positioning docs, and all April features; first resolve or intentionally refresh the stale Checkstyle baseline.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
- `TODO.md` currently has no active TODOs.
