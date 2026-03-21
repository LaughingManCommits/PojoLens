# Module Boundaries (Staged)

This project still ships as one compatibility artifact (`PojoLens`), but
runtime boundaries are explicitly separated by entry point:

- `PojoLensCore`: fluent query builder + filter execution + stats-plan cache controls
- `PojoLensSql`: SQL-like parser + SQL-like cache controls
- `PojoLensChart`: chart payload mapping helpers
- `PojoLens`: compatibility facade that delegates to the three entry points above

Compatibility tiers for these entry points and related contracts are defined in
[public-api-stability.md](public-api-stability.md).

## Dependency Footprint

- `xchart` is optional and used only by benchmark plotting utilities.
- `jmh-core` is optional and used only for benchmark execution.

Core-only consumers can keep using `PojoLensCore` and avoid pulling optional
benchmark plotting runtime dependencies transitively.

## Migration Guidance

Existing code using `PojoLens` remains valid. New code can opt into explicit
entry points incrementally:

1. Replace `PojoLens.newQueryBuilder(...)` with `PojoLensCore.newQueryBuilder(...)`.
2. Replace `PojoLens.parse(...)` with `PojoLensSql.parse(...)` in SQL-like paths.
3. Replace `PojoLens.toChartData(...)` with `PojoLensChart.toChartData(...)` where desired.

