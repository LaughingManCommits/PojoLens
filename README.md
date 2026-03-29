# PojoLens!
From `List<T>` to query and chart-ready results, without a database.

`PojoLens` is a POJO-first in-memory query engine for Java. It supports both a fluent API and SQL-like query strings for filtering, ordering, grouping, joins, aggregates, HAVING, time buckets, and chart payload mapping.

## Installation

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>2026.03.28.1919</version>
</dependency>
```

Requirements:
- JDK `17+`

Build layout:
- `pojo-lens` is the consumer runtime artifact.
- `pojo-lens-spring-boot-starter` provides optional Spring Boot integration.
- Benchmark/JMH tooling is isolated in the `pojo-lens-benchmarks` module.

Central distribution:
- Published: `pojo-lens`, `pojo-lens-spring-boot-autoconfigure`, `pojo-lens-spring-boot-starter`
- Not published: benchmark and example modules

### Spring Boot starter

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens-spring-boot-starter</artifactId>
  <version>2026.03.28.1919</version>
</dependency>
```

Example application properties:

```yaml
pojo-lens:
  preset: PROD
  strict-parameter-types: false
  lint-mode: false
  telemetry:
    micrometer:
      enabled: true
```

Runnable example project:
- `examples/spring-boot-starter-quickstart` (minimal starter onboarding: one query flow + runtime flags)
- `examples/spring-boot-starter-basic` (advanced dashboard: charts, presets, and richer API surface)

## Why PojoLens

- Query existing domain classes directly (no ORM model rewrite).
- Choose query style per use case:
  - fluent API for type-safe Java composition
  - SQL-like strings for dynamic/user-authored queries
- Keep query definition and execution in one in-memory engine.
- Add chart/table/report helpers only when the use case needs them.
- Keep runtime wiring and tooling optional instead of making them part of the
  first-read adoption path.
- Use [docs/advanced-features.md](docs/advanced-features.md) only after the
  core query path is already in place.

## Start Here

- Need to choose the default query/runtime entry path:
  [docs/entry-points.md](docs/entry-points.md)
- Need to choose between reusable report/chart/table wrappers:
  [docs/reusable-wrappers.md](docs/reusable-wrappers.md)
- Need a scenario-driven selection guide:
  [docs/usecases.md](docs/usecases.md)
- Need optional runtime tuning, testing, or tooling:
  [docs/advanced-features.md](docs/advanced-features.md)

## Pick A Path

For new code, prefer one default path per job:
`PojoLensCore`, `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, or
`ReportDefinition<T>`.

| If you need... | Choose... | Read next |
| --- | --- | --- |
| Service-owned query logic in code | `PojoLensCore` | `docs/entry-points.md`, `docs/usecases.md` |
| Config-driven or dynamic query strings | `PojoLensSql` | `docs/entry-points.md`, `docs/sql-like.md` |
| Runtime-scoped policy, DI, or multi-tenant query behavior | `PojoLensRuntime` | `docs/entry-points.md`, `docs/advanced-features.md` |
| Rows already exist and only chart mapping remains | `PojoLensChart` | `docs/entry-points.md`, `docs/charts.md` |
| A reusable business query contract | `ReportDefinition` | `docs/reusable-wrappers.md`, `docs/reports.md` |
| A reusable chart-first preset | `ChartQueryPreset` | `docs/reusable-wrappers.md`, `docs/charts.md` |
| A reusable table payload with totals/schema | `StatsViewPreset` / `StatsTable` | `docs/reusable-wrappers.md`, `docs/stats-presets.md` |
| Joined multi-source execution | `JoinBindings`, then `DatasetBundle` when reused | `docs/sql-like.md`, `docs/reports.md` |

## Product Shape

- `Core query engine`:
  fluent and SQL-like querying over existing Java objects.
- `Workflow helpers`:
  chart mapping, reusable report/preset wrappers, dataset composition, and
  schema metadata.
- `Runtime integration`:
  runtime-scoped configuration and optional Spring Boot wiring.
- `Advanced and tooling`:
  diagnostics, policy tuning, regression helpers, metamodel generation, and
  benchmarks.

Canonical surface classification:
- [docs/product-surface.md](docs/product-surface.md)

Advanced/optional follow-on guide:
- [docs/advanced-features.md](docs/advanced-features.md)

## Quick Start

### Fluent query

```java
List<Employee> rows = PojoLensCore.newQueryBuilder(source)
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addOrder("salary", 1)
    .limit(10)
    .initFilter()
    .filter(Sort.DESC, Employee.class);
```

### SQL-like query

```java
List<Employee> rows = PojoLensSql
    .parse("select name, salary "
        + "where department = :dept and salary >= :minSalary "
        + "order by salary desc limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

### Chart payload in one call

```java
ChartData chart = PojoLensSql
    .parse("select department, count(*) as total "
        + "group by department order by total desc")
    .chart(source, DepartmentCount.class, ChartSpec.of(ChartType.BAR, "department", "total"));
```

### Predefined stats table in one call

```java
StatsTable<DepartmentPayrollRow> table = StatsViewPresets
    .topNBy("department", Metric.SUM, "salary", "payroll", 3, DepartmentPayrollRow.class)
    .table(source);
```

## Capability Snapshot

### Core query engine

- Filtering, ordering, and pagination (`WHERE`, fluent rules, `ORDER BY`, `LIMIT`, `OFFSET`)
- First-class keyset/cursor pagination primitives with token support
- Streaming execution output (`iterator` / `stream`) for low-allocation simple query scans
- Optional in-memory index hints for repeated fluent equality filters
- Aggregation and grouped queries (`GROUP BY`, metrics, `HAVING`)
- Time buckets (`day`, `week`, `month`, `quarter`, `year`)
- SQL-like named parameters and typed bind-first execution
- SQL-like window analytics (`ROW_NUMBER`, `RANK`, `DENSE_RANK`, running aggregates with `OVER(...)`) and `QUALIFY`
- Chained SQL-like joins with typed join bindings
- Computed field registry for derived expressions

### Workflow helpers

- Chart payload mapping (`BAR`, `LINE`, `PIE`, `AREA`, `SCATTER`)
- Reusable report, chart-preset, and stats-preset wrappers
- Dataset-bundle execution reuse and tabular schema metadata

### Runtime integration

- Runtime-scoped presets and policy controls via `PojoLensRuntime`
- Optional Spring Boot starter/autoconfigure modules

### Advanced and tooling

- Query telemetry hooks, lint mode, and cache tuning controls
- Snapshot comparison helpers and regression fixtures
- Field metamodel generation for typed field constants
- Benchmark/JMH tooling in a separate benchmark module

These public follow-on features are collected in
[docs/advanced-features.md](docs/advanced-features.md).

## API Entry Points

- `PojoLensCore`: default for new service-owned fluent queries
- `PojoLensSql`: default for new SQL-like and template-driven queries
- `PojoLensRuntime`: default when query policy or configuration should be instance-scoped
- `PojoLensChart`: chart-only helper when rows already exist
- `ReportDefinition`: reusable business-query wrapper when the contract itself should be carried around

Recommended defaults for new code are documented in
[docs/entry-points.md](docs/entry-points.md).

## Public API Stability

PojoLens uses three API tiers:
- `Stable`: intended dated-release surface, enforced from the first
  `release-*` tag onward
- `Advanced`: public but faster-evolving (best-effort compatibility)
- `Internal`: no compatibility guarantee (`*.internal.*`)

These tiers are orthogonal to the product-surface families in
[docs/product-surface.md](docs/product-surface.md):
- core query engine
- workflow helpers
- integration
- compatibility adapters
- tooling

The explicit stable-surface contract and deprecation policy are documented in
[docs/public-api-stability.md](docs/public-api-stability.md).

## Runtime Presets

```java
PojoLensRuntime devRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
PojoLensRuntime prodRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.PROD);
PojoLensRuntime testRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.TEST);
```

Preset intent:
- `DEV`: strict + diagnostics friendly
- `PROD`: lower overhead defaults
- `TEST`: stricter deterministic behavior, caches disabled

## Current Limitations

- SQL-like subqueries are currently limited to `WHERE <field> IN (select <oneField> ...)`.
- Aggregate/grouped/joined subquery plans are not supported.
- SQL-like aggregate `ORDER BY` must reference grouped fields or aggregate outputs.
- SQL-like aggregate windows currently support only `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
- Time bucket source fields must be `java.util.Date`.
- Builders are mutable and not safe for concurrent mutation; use `copyOnBuild(true)` for reusable templates.

## Documentation Map

### Start Here

- Path selection guide: [docs/usecases.md](docs/usecases.md)
- Entry point guide: [docs/entry-points.md](docs/entry-points.md)
- Reusable wrapper guide: [docs/reusable-wrappers.md](docs/reusable-wrappers.md)
- Advanced features guide: [docs/advanced-features.md](docs/advanced-features.md)

### Core Guides

- SQL-like guide: [docs/sql-like.md](docs/sql-like.md)
- Charts: [docs/charts.md](docs/charts.md)
- Reports and presets: [docs/reports.md](docs/reports.md)
- Stats view presets: [docs/stats-presets.md](docs/stats-presets.md)
- Time buckets: [docs/time-buckets.md](docs/time-buckets.md)
- Computed fields: [docs/computed-fields.md](docs/computed-fields.md)

### Reference

- Product surface map: [docs/product-surface.md](docs/product-surface.md)
- Module boundaries: [docs/modules.md](docs/modules.md)
- Public API stability policy: [docs/public-api-stability.md](docs/public-api-stability.md)
- Tabular schema: [docs/tabular-schema.md](docs/tabular-schema.md)
- Migration notes: [MIGRATION.md](MIGRATION.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Release workflow: [RELEASE.md](RELEASE.md)

### Advanced Follow-On

- Cache behavior: [docs/caching.md](docs/caching.md)
- Telemetry: [docs/telemetry.md](docs/telemetry.md)
- Regression fixtures: [docs/regression-fixtures.md](docs/regression-fixtures.md)
- Snapshot comparison: [docs/snapshot-comparison.md](docs/snapshot-comparison.md)
- Field metamodel generation: [docs/metamodel.md](docs/metamodel.md)
- Benchmarking and guardrails: [docs/benchmarking.md](docs/benchmarking.md)

## Development

Local validation:

```bash
mvn -B -ntp test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for lint, benchmarks, and release-quality guardrail commands.

