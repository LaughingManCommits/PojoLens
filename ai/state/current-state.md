# Current State

## Repo

- Java 17 multi-module library build with runtime, Spring Boot, and JMH modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-23`: `STRAT-WP3` typed DSL foundation complete: `TypedField<T,V>`, `TypedPredicate<T>`, `TypedQuery<T>`, and `FieldMetamodelGenerator.generateTyped(...)`.
- `2026-04-23`: WP3 review fixes preserve nested mixed `AND`/`OR` semantics and compile generated typed metamodel source for primitive/nested-model fields.
- `2026-04-23`: Typed DSL scope covers projection, filters, ordering, offset, limit, explain/schema, and guard interop; typed grouping/joins remain deferred.
- `2026-04-22`: Product direction is embedded reporting plus governed in-memory query execution.
- `2026-04-22`: `STRAT-WP1` shipped the saved-report contract.
- `2026-04-23`: `STRAT-WP2` hardening enforces guards on lazy/bound execution and counts join-source rows in scan budgets.
- `2026-04-23`: `STRAT-WP2` now complete: cooperative cancellation and deterministic aborted-query metadata shipped; senior review fixes close bound eager cancellation, typed empty-input cancellation, stable API coverage, and public docs.
- `2026-04-20`: `QOL-WP1` through `QOL-WP5` are complete.

## Verified

- `2026-04-23`: WP2 cancellation review fixes passed targeted guard/public API slice: 74 tests, 0 failures.
- `2026-04-23`: Full Maven suite: 1024 tests across all modules, 0 failures.
- `2026-04-23`: `scripts/check-doc-consistency.ps1` passed after cancellation docs updates.
- `2026-04-23`: WP3 review fixes passed full Maven suite: 972 tests, 0 failures.
- `2026-04-23`: WP2 hardening passed targeted guard tests, the broad `*Policy*/*Exposure*/*Telemetry*/*Natural*/*SqlLike*` Maven slice, and `scripts/check-doc-consistency.ps1`.

## Release

- `2026.04.17.1834` is the latest cut.

## Risks

- SQL-like is the public default; natural remains controlled grammar; fluent stays internal.
- WP2 governance is now complete.

## Next

- Release preparation or WP4/WP5.
- Keep `CSV-WP6`, correlated/scalar subqueries, and broad window-frame parity out of scope.
