# Query Telemetry Hooks

`PojoLens` can emit low-overhead telemetry events for key query stages without introducing a logging dependency.

Telemetry is an optional advanced diagnostics surface.
Start with the core query guides first, then add telemetry when you need
operational visibility.

Supported stages:

- `PARSE`
- `BIND`
- `PUSHDOWN`
- `FILTER`
- `AGGREGATE`
- `ORDER`
- `CHART`

## Runtime-Level Hook

Use `PojoLensRuntime` when you want parse-time telemetry as well as execution-stage telemetry:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setTelemetryListener(event -> {
    System.out.println(event.stage() + " " + event.durationNanos());
});

List<DepartmentCount> rows = runtime
    .parse("select department, count(*) as total group by department order by department asc")
    .filter(snapshot, DepartmentCount.class);
```

Runtime-level hooks are the cleanest way to capture SQL-like `PARSE` events because parsing happens before a `SqlLikeQuery` instance is returned.

You can inspect or clear the current runtime listener:

```java
QueryTelemetryListener current = runtime.getTelemetryListener();
runtime.setTelemetryListener(null); // disables telemetry callbacks
```

## SQL-like Hook

Attach a listener directly to a SQL-like query when you want bind/execution telemetry:

```java
SqlLikeQuery query = PojoLensSql
    .parse("where salary >= 100000 order by salary desc")
    .telemetry(listener);

List<Employee> rows = query.filter(snapshot, Employee.class);
```

## Event Shape

Each `QueryTelemetryEvent` contains:

- `stage()`
- `queryType()` such as `sql-like` or `natural`
- `source()` for the originating query/source label
- `durationNanos()`
- `rowCountBefore()`
- `rowCountAfter()`
- `metadata()` for deterministic stage-specific details

Examples of metadata:

- `projectionClass`
- `joinSourceCount`
- `applyJoin`
- `pushdownMode`
- `pushdownPushableStages`
- `pushdownInMemoryStages`
- `pushdownFallbackReasons`
- `requestedStages`
- `pushedStages`
- `materializedRowCount`
- `adapterMetadata`
- `orderFieldCount`
- `chartType`
- `labelCount`
- `datasetCount`

SQL-like `BIND` events include pushdown-readiness metadata. This is advisory
host-adapter planning data only; it does not mean PojoLens executed a pushed
query outside the in-memory engine.

`filterWithPushdown(...)` emits a `PUSHDOWN` event after the host adapter
returns materialized rows. The event describes what PojoLens requested and what
the adapter reported, while database execution, authorization, and SQL rendering
remain host-owned.

## Low-Overhead Behavior

When no listener is configured, telemetry is effectively disabled:

- no listener callbacks
- no event allocations
- no stage timing calls beyond null checks


