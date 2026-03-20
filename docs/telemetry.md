# Query Telemetry Hooks

`PojoLens` can emit low-overhead telemetry events for key query stages without introducing a logging dependency.

Supported stages:

- `PARSE`
- `BIND`
- `FILTER`
- `AGGREGATE`
- `ORDER`
- `CHART`

## Runtime-Level Hook

Use `PojoLensRuntime` when you want parse-time telemetry as well as execution-stage telemetry:

```java
PojoLensRuntime runtime = PojoLens.newRuntime();
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

## Fluent Hook

Attach a listener directly to a fluent builder:

```java
List<DepartmentCount> rows = PojoLens.newQueryBuilder(snapshot)
    .telemetry(listener)
    .addRule("active", true, Clauses.EQUAL)
    .addGroup("department")
    .addCount("total")
    .initFilter()
    .filter(DepartmentCount.class);
```

## SQL-like Hook

Attach a listener directly to a SQL-like query when you want bind/execution telemetry:

```java
SqlLikeQuery query = PojoLens
    .parse("where salary >= 100000 order by salary desc")
    .telemetry(listener);

List<Employee> rows = query.filter(snapshot, Employee.class);
```

## Event Shape

Each `QueryTelemetryEvent` contains:

- `stage()`
- `queryType()` such as `fluent` or `sql-like`
- `source()` for the originating query/source label
- `durationNanos()`
- `rowCountBefore()`
- `rowCountAfter()`
- `metadata()` for deterministic stage-specific details

Examples of metadata:

- `projectionClass`
- `joinSourceCount`
- `applyJoin`
- `orderFieldCount`
- `chartType`
- `labelCount`
- `datasetCount`

## Low-Overhead Behavior

When no listener is configured, telemetry is effectively disabled:

- no listener callbacks
- no event allocations
- no stage timing calls beyond null checks

