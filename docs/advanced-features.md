# Advanced Features Guide

This page collects the public PojoLens features that are useful after the core
query path is already in place.

Start with these first:

- [README.md](../README.md)
- [entry-points.md](entry-points.md)
- [usecases.md](usecases.md)
- [reusable-wrappers.md](reusable-wrappers.md)

Those documents cover the default adoption path.
The features below are optional follow-on surfaces for runtime policy,
diagnostics, testing, integration, and build-time tooling.

## Runtime Integration And Policy

Use these when query behavior or wiring needs to vary by environment, tenant, or
application framework:

- `PojoLensRuntime` and runtime presets:
  [entry-points.md](entry-points.md), [sql-like.md](sql-like.md)
- Spring Boot starter/autoconfigure modules:
  [modules.md](modules.md)
- Spring/JDBC bridge helper:
  [jdbc.md](jdbc.md)
- Cache policy tuning:
  [caching.md](caching.md)
- Telemetry hooks:
  [telemetry.md](telemetry.md)

## Workflow And UI Helpers

Use these when you already have an in-memory snapshot and need lightweight
dashboard-oriented helpers beyond the main query/report/chart path:

- Facet option helpers for filter bars and navigation:
  [facets.md](facets.md)

## Diagnostics And Guardrails

Use these when you need operational visibility or stricter query hygiene:

- `explain()` and SQL-like diagnostics:
  [sql-like.md](sql-like.md)
- Natural query `explain()`:
  [natural.md](natural.md)
- Lint mode and strict parameter typing:
  [sql-like.md](sql-like.md)
- Benchmark and threshold tooling:
  [benchmarking.md](benchmarking.md)

## Execution Governance

Use `QueryExecutionGuard` when user-authored queries need bounded execution.
The guard enforces complexity, row-scan, row-return, duration, and cooperative
cancellation limits and blocks non-compliant queries with a machine-readable code
and human-readable reason. It is attached to a query with the fluent
`.executionGuard(guard)` call and applies to SQL-like and natural query paths;
`TypedQuery` supports row limits, duration limits, and pre-execution
cancellation.

```java
AtomicBoolean cancel = new AtomicBoolean(false);
QueryExecutionGuard guard = QueryExecutionGuard.builder()
    .maxRowsScanned(10_000)
    .maxRowsReturned(500)
    .maxComplexityScore(8)
    .maxDurationMillis(2_000)
    .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
    .build();

try {
    List<Row> rows = PojoLensSql.parse(userQuery)
        .executionGuard(guard)
        .filter(snapshot, Row.class);
} catch (QueryExecutionGuardException ex) {
    QueryGuardOutcome outcome = ex.outcome();
    log.warn("Query blocked [{}]: {}", outcome.blockCode(), outcome.blockReason());
    Integer rowsReturnedBeforeAbort = outcome.rowsReturnedBeforeAbort();
    // emit outcome.auditMetadata() to telemetry
}
```

**Block codes:**

| Code | When |
|------|------|
| `GUARD_ROWS_SCANNED_EXCEEDED` | Input rows exceed `maxRowsScanned` (pre-execution) |
| `GUARD_COMPLEXITY_EXCEEDED` | Complexity score exceeds `maxComplexityScore` (pre-execution) |
| `GUARD_ROWS_RETURNED_EXCEEDED` | Result rows exceed `maxRowsReturned` (post-execution) |
| `GUARD_DURATION_EXCEEDED` | Wall-clock time exceeds `maxDurationMillis` (post-execution) |
| `GUARD_CANCELLED` | Attached `QueryCancellationToken` fired before or during execution |

**Complexity scoring:** `QueryComplexitySummary` derives an additive integer score
from the parsed query shape (1 per filter, 3 per join, +2 grouping, +2 aggregation,
+4 windows, +3 subqueries). Inspect it via `QueryExecutionGuard.checkPreExecution()`
or retrieve from `QueryGuardOutcome.complexitySummary()`.

**Cancellation:** Attach a `QueryCancellationToken` with
`QueryExecutionGuard.Builder#cancellationToken(...)`. Tokens can be backed by an
`AtomicBoolean` (`QueryCancellationToken.ofAtomic(...)`) or a thread interrupt
state (`QueryCancellationToken.ofThread(...)`). Cancellation is cooperative:
eager paths check at execution start, and lazy stream/iterator paths also check
between returned rows. Cancellation outcomes include
`rowsReturnedBeforeAbort`, the exact number of rows yielded before the abort.
Thread-backed cancellation works for platform and virtual request threads, but
it only tracks the specific thread you bind. If a host hands work across
threads or cancels via a separate request signal, prefer an
`AtomicBoolean`-backed token instead of `ofThread(...)`.

**Telemetry:** When a guard blocks, `QueryTelemetryStage.GUARD_REJECTED` is emitted
via `QueryTelemetryListener` before throwing, carrying `auditMetadata()` fields.

**Security boundary:** The guard provides bounded execution governance. Authentication,
RBAC, and tenant-level authorization remain host-application responsibilities. Pair
with `QueryExposurePolicy` to restrict which fields and sources are visible.

## Regression And Snapshot Tooling

Use these when you need safety rails around changing query behavior:

- Query regression fixtures and fluent/SQL parity helpers:
  [regression-fixtures.md](regression-fixtures.md)
- Snapshot comparison and delta-row analysis:
  [snapshot-comparison.md](snapshot-comparison.md)

## Authoring And Build-Time Tooling

Use these when you want stronger typed authoring support or build-time
verification:

- Batch metamodel generation and saved-report/catalog validation:
  [build-tooling.md](build-tooling.md)
- Field metamodel generation:
  [metamodel.md](metamodel.md)
- Benchmark runner and threshold checks:
  [benchmarking.md](benchmarking.md)

## Stability Note

Advanced does not mean internal.
Some of these surfaces are public and stable, but they are still not the default
first-read product story.

Compatibility expectations for stable versus advanced APIs are defined in
[public-api-stability.md](public-api-stability.md).

