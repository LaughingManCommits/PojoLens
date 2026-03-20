# PojoLens
From `List<T>` to query and chart-ready results, without a database.

`PojoLens` is a POJO-first in-memory query engine for Java. It supports both a fluent API and SQL-like query strings for filtering, ordering, grouping, joins, aggregates, HAVING, time buckets, and chart payload mapping.

## Installation

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requirements:
- JDK `17+`

## Why PojoLens

- Query existing domain classes directly (no ORM model rewrite).
- Choose query style per use case:
  - fluent API for type-safe Java composition
  - SQL-like strings for dynamic/user-authored queries
- Keep query and chart mapping in one pipeline (`ChartData`).
- Reuse query shapes with report definitions, presets, dataset bundles, and typed bindings.

## Quick Start

### Fluent query

```java
List<Employee> rows = PojoLens.newQueryBuilder(source)
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addOrder("salary", 1)
    .limit(10)
    .initFilter()
    .filter(Sort.DESC, Employee.class);
```

### SQL-like query

```java
List<Employee> rows = PojoLens
    .parse("select name, salary "
            + "where department = :dept and salary >= :minSalary "
            + "order by salary desc limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

### Chart payload in one call

```java
ChartData chart = PojoLens
    .parse("select department, count(*) as total "
            + "group by department order by total desc")
    .chart(source, DepartmentCount.class, ChartSpec.of(ChartType.BAR, "department", "total"));
```

### SQL-like join with typed bindings

```java
JoinBindings joinBindings = JoinBindings.of("employees", employees);

List<Company> rows = PojoLens
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(companies, joinBindings, Company.class);
```

## Capability Snapshot

- Filtering and ordering (`WHERE`, fluent rules, `ORDER BY`, `LIMIT`)
- Aggregation and grouped queries (`GROUP BY`, metrics, `HAVING`)
- Time buckets (`day`, `week`, `month`, `quarter`, `year`)
- SQL-like named parameters and typed bind-first execution
- Chained SQL-like joins with typed join bindings
- Computed field registry for derived expressions
- Chart payload mapping (`BAR`, `LINE`, `PIE`, `AREA`, `SCATTER`)
- Query telemetry hooks and explain output
- Snapshot comparison helpers and regression fixtures
- Field metamodel generation for typed field constants

## API Entry Points

- `PojoLens`: compatibility facade
- `PojoLensCore`: fluent query entry point
- `PojoLensSql`: SQL-like entry point
- `PojoLensChart`: chart mapping entry point

## Runtime Presets

```java
PojoLensRuntime devRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
PojoLensRuntime prodRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);
PojoLensRuntime testRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.TEST);
```

Preset intent:
- `DEV`: strict + diagnostics friendly
- `PROD`: lower overhead defaults
- `TEST`: stricter deterministic behavior, caches disabled

## Current Limitations

- SQL-like subqueries are currently limited to `WHERE <field> IN (select <oneField> ...)`.
- Aggregate/grouped/joined subquery plans are not supported.
- SQL-like aggregate `ORDER BY` must reference grouped fields or aggregate outputs.
- Time bucket source fields must be `java.util.Date`.
- Builders are mutable and not safe for concurrent mutation; use `copyOnBuild(true)` for reusable templates.

## Documentation Map

- SQL-like guide: [docs/sql-like.md](docs/sql-like.md)
- Charts: [docs/charts.md](docs/charts.md)
- Benchmarking and guardrails: [docs/benchmarking.md](docs/benchmarking.md)
- Module boundaries: [docs/modules.md](docs/modules.md)
- Cache behavior: [docs/caching.md](docs/caching.md)
- Time buckets: [docs/time-buckets.md](docs/time-buckets.md)
- Telemetry: [docs/telemetry.md](docs/telemetry.md)
- Reports and presets: [docs/reports.md](docs/reports.md)
- Computed fields: [docs/computed-fields.md](docs/computed-fields.md)
- Tabular schema: [docs/tabular-schema.md](docs/tabular-schema.md)
- Snapshot comparison: [docs/snapshot-comparison.md](docs/snapshot-comparison.md)
- Regression fixtures: [docs/regression-fixtures.md](docs/regression-fixtures.md)
- Field metamodel generation: [docs/metamodel.md](docs/metamodel.md)
- Migration notes: [MIGRATION.md](MIGRATION.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Release workflow: [RELEASE.md](RELEASE.md)

## Development

Local validation:

```bash
mvn -B -ntp test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for lint, benchmarks, and release-quality guardrail commands.
