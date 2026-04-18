# Internal Fluent Engine DSL

This page is maintainer documentation, not user-facing API guidance.

The fluent builder surface remains useful inside PojoLens as the structured
engine DSL for execution planning, parity tests, benchmarks, and lower-level
feature work. It is not the public product story for new users.

Public docs should lead with:

- `PojoLensSql` for the primary query surface
- `PojoLensNatural` for guided plain-English text
- `ReportDefinition.sql(...)` and `ReportDefinition.natural(...)` for reusable
  query contracts
- `PojoLensCsv`, `PojoLensTree`, `PojoLensChart`, and runtime policy helpers
  where those workflows apply

## Internal Role

Use the fluent builder internally when a test, benchmark, or implementation
needs a direct structured query shape without SQL-like parsing.

Useful internal cases:

- verifying SQL-like and natural lowering against a known execution shape
- testing grouped predicates, windows, joins, time buckets, streaming, and
  subqueries at the engine layer
- measuring engine execution paths separately from parse/bind overhead
- building maintainer-only examples for engine behavior

## Boundary Rules

- Do not add fluent examples to README quick starts or public path-selection
  tables.
- Do not document `QueryBuilder`, `FilterQueryBuilder`, `Filter`, `QueryRule`,
  or `FluentQueryDefinition` as stable public API.
- Prefer "shared execution engine" in public docs instead of "fluent
  pipeline".
- Keep public reusable-query examples on SQL-like or natural report
  definitions.
- Add a future Java-native query API only if a real public use case requires a
  narrow immutable contract; do not expose the current mutable builder as that
  API.

## Current Bridge

`PojoLensCore`, `PojoLensRuntime.newQueryBuilder(...)`,
`FluentQueryDefinition`, and `ReportDefinition.fluent(...)` may still exist
while the codebase is reset. Treat them as compatibility-reset candidates until
`SURFACE-WP4` and `SURFACE-WP5` finish the guard and package work.
