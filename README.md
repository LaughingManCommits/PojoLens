# PojoLens!
From `List<T>` to query and chart-ready results, without a database.

`PojoLens` is a POJO-first in-memory query engine for Java. Its primary public query surface is SQL-like text over already-loaded Java objects, with a controlled plain-English surface for guided non-SQL authoring. Across those paths it covers filtering, ordering, grouping, joins, bounded subquery/existence predicates, aggregates, window analytics, `QUALIFY`, time buckets, and chart payload mapping. It also includes bounded helpers for loading UTF-8 CSV, TSV, JSON, and JSONL files into typed rows and shaping flat parent-ID lists before they enter the same engine.

Core execution model:
`authoring mode -> validated execution plan -> in-memory row processing -> typed rows/chart/table output`

## Installation

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>2026.04.17.1834</version>
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
  <version>2026.04.17.1834</version>
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

Runnable example projects:
- `examples/spring-boot-starter-quickstart` (minimal starter onboarding: one query flow + runtime flags)
- `examples/spring-boot-starter-basic` (advanced dashboard: charts, presets, and richer API surface)
- `examples/spring-boot-starter-risk-console` (JDBC-backed reviewer/demo app with dashboard, reports, and Query Studio showcase)

## Why PojoLens

- Query existing domain classes directly (no ORM model rewrite).
- Use SQL-like strings as the default public query surface.
- Use controlled plain-English queries when authors need guided non-SQL text.
- Use typed DSL field constants when query logic is owned by Java code.
- Inspect pushdown-readiness metadata when a host-owned adapter may prefilter
  or page before materializing rows; PojoLens can finish split execution over
  those returned rows.
- Keep query definition and execution in one in-memory engine.
- Add chart/table/report helpers only when the use case needs them.
- Keep runtime wiring and tooling optional instead of making them part of the
  first-read adoption path.
- Use [docs/advanced-features.md](docs/advanced-features.md) only after the
  core query path is already in place.

## When Not To Use PojoLens

- Use a database query layer such as jOOQ, Spring Data, JPA Criteria, or raw
  SQL when the data should be filtered, joined, paged, locked, or aggregated by
  the database before it is loaded into memory.
  `pushdownPreview()` can help host adapters classify simple stages, but
  PojoLens still does not execute database queries or render vendor SQL.
- Use an indexed collection/search library when the main problem is repeated
  large-scale lookup over a mutable indexed store.
- Use plain Java Streams when the query is a tiny code-owned transformation
  that does not need reusable query contracts, text queries, diagnostics,
  grouping/window helpers, or report/chart output.
- Use object-mapping libraries when the goal is DTO/entity conversion rather
  than querying already-loaded rows.

## Start Here

- Need to choose the default query authoring mode:
  [docs/entry-points.md](docs/entry-points.md)
- Need SQL-like grammar, templates, joins, windows, and bounded subqueries:
  [docs/sql-like.md](docs/sql-like.md)
- Need the controlled plain-English grammar and recipes:
  [docs/natural.md](docs/natural.md)
- Need reusable-contract guidance after the authoring mode is clear:
  [docs/reusable-wrappers.md](docs/reusable-wrappers.md)
- Need file-boundary loader guidance for CSV/TSV/JSON/JSONL onboarding:
  [docs/files.md](docs/files.md)
- Need build-time metamodel generation or saved-report validation:
  [docs/build-tooling.md](docs/build-tooling.md)
- Need chart/table/schema output-helper guidance after the query or reusable
  contract is clear:
  [docs/output-helpers.md](docs/output-helpers.md)
- Need a scenario-driven selection guide:
  [docs/usecases.md](docs/usecases.md)
- Need optional runtime tuning, testing, or tooling:
  [docs/advanced-features.md](docs/advanced-features.md)
- See what has changed: [CHANGELOG.md](CHANGELOG.md)

## Choose Authoring Mode First

For new code, choose one primary authoring mode first.

| If you need...                                            | Choose...                                        | Read next                                                                                              |
|-----------------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Default query authoring over in-memory rows               | `PojoLensSql` (`parse(...)`, `template(...)`)    | [docs/entry-points.md](docs/entry-points.md), [docs/sql-like.md](docs/sql-like.md)                     |
| Guided text queries for non-SQL users                     | `PojoLensNatural` (`parse(...)`, `template(...)`) | [docs/entry-points.md](docs/entry-points.md), [docs/natural.md](docs/natural.md)                       |
| Code-owned typed filters, joins, grouped aggregates, `HAVING`, windows, bounded subqueries, aggregate window frames, and ordering | `TypedQuery`                               | [docs/entry-points.md](docs/entry-points.md), [docs/metamodel.md](docs/metamodel.md)                   |

Then add only the layer the workflow actually needs:

| If you need...                                            | Choose...                                        | Read next                                                                                              |
|-----------------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| A reusable business query contract                        | `ReportDefinition`                               | [docs/reusable-wrappers.md](docs/reusable-wrappers.md), [docs/reports.md](docs/reports.md)             |
| A saved/versioned report for storage, admin review, or replay | `SavedReport`                              | [docs/reusable-wrappers.md](docs/reusable-wrappers.md), [docs/reports.md](docs/reports.md)             |
| Runtime-scoped policy, DI, or multi-tenant query behavior | `PojoLensRuntime`                                | [docs/entry-points.md](docs/entry-points.md), [docs/advanced-features.md](docs/advanced-features.md)   |
| Typed file-boundary onboarding for CSV, TSV, JSON, or JSONL | `PojoLensFiles`                               | [docs/entry-points.md](docs/entry-points.md), [docs/files.md](docs/files.md), [docs/csv.md](docs/csv.md) |
| Flat parent-ID rows need subtree selection                | `PojoLensTree`                                   | [docs/entry-points.md](docs/entry-points.md), [docs/tree.md](docs/tree.md)                             |
| Rows already exist and only chart mapping remains         | `PojoLensChart`                                  | [docs/entry-points.md](docs/entry-points.md), [docs/charts.md](docs/charts.md)                         |
| Advanced chart-first convenience after the reusable contract is clear | `ChartQueryPresets` / `ChartQueryPreset` | [docs/reusable-wrappers.md](docs/reusable-wrappers.md), [docs/charts.md](docs/charts.md)               |
| Advanced table-first convenience after the reusable contract is clear | `StatsViewPresets` / `StatsViewPreset` / `StatsTablePayload` / `StatsTable<T>` | [docs/reusable-wrappers.md](docs/reusable-wrappers.md), [docs/stats-presets.md](docs/stats-presets.md) |
| Joined multi-source execution                             | `JoinBindings`, then `DatasetBundle` when reused | [docs/sql-like.md](docs/sql-like.md), [docs/natural.md](docs/natural.md), [docs/reports.md](docs/reports.md) |

For stats tables, `StatsTablePayload` is the projection-free dashboard payload;
`StatsTable<T>` keeps typed rows.
For reusable workflows, the defaults are `ReportDefinition` and `SavedReport`;
chart/table presets remain advanced convenience sugar.

## Product Shape

- `Core query engine`:
  SQL-like, typed DSL, and controlled plain-English querying over existing Java objects.
- `Workflow helper`:
  chart mapping, tree row shaping, reusable report/preset wrappers, dataset
  composition, and schema metadata.
- `Compatibility adapter`:
  boundary-only file loading into typed rows via `PojoLensFiles`.
- `Integration`:
  runtime-scoped configuration and optional Spring Boot wiring.
- `Tooling`:
  diagnostics, regression helpers, build-time metamodel/query tooling, and
  benchmarks.
- `Advanced`:
  optional policy tuning and faster-evolving public helper surfaces.

Canonical surface classification:
- [docs/product-surface.md](docs/product-surface.md)

Advanced/optional follow-on guide:
- [docs/advanced-features.md](docs/advanced-features.md)

## Quick Start

Short examples for the primary public query paths.
Broader recipes for joins, grouping, windows, templates, charts, and presets live in the docs linked below.

### SQL-like query

```java
List<Employee> rows = PojoLensSql
    .parse("select name, salary "
        + "where department = :dept and salary >= :minSalary "
        + "order by salary desc limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

### Controlled plain-English query

```java
List<Employee> rows = PojoLensNatural
    .parse("show employees where department is :dept and salary is at least :minSalary "
        + "sort by salary descending limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

### Typed Java-owned query

```java
List<Employee> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.DEPARTMENT.eq("Engineering")
        .and(EmployeeTypedFields.SALARY.gte(120000)))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .limit(10)
    .filter(source);
```

### File-boundary load

```java
List<Employee> rows = PojoLensFiles.csv(Path.of("employees.csv"), Employee.class);

List<Employee> filtered = PojoLensSql
    .parse("where department = 'Engineering' order by salary desc")
    .filter(rows, Employee.class);
```

### Tree row shaping

```java
List<Employee> subtree = PojoLensTree.subtreeOf(
    employees,
    Employee::getId,
    Employee::getManagerId,
    ceoId
);

List<Employee> rows = PojoLensSql
    .parse("order by salary desc limit 10")
    .filter(subtree, Employee.class);
```

More examples:
- SQL-like queries, templates, joins, windows, bounded subqueries, and charts:
  [docs/sql-like.md](docs/sql-like.md)
- Natural queries, joins, bounded subqueries, windows, and templates:
  [docs/natural.md](docs/natural.md)
- Tree traversal from flat parent-ID rows: [docs/tree.md](docs/tree.md)
- Charts, reports, and presets: [docs/charts.md](docs/charts.md), [docs/reports.md](docs/reports.md), [docs/stats-presets.md](docs/stats-presets.md)

## Capability Snapshot

This README is the feature-set map. Clause grammar, recipes, and edge-case
rules live in the module docs linked beside each surface.

### Core query engine

- SQL-like text queries for public query authoring. See
  [docs/sql-like.md](docs/sql-like.md).
- Controlled natural query text for guided non-SQL authoring. See
  [docs/natural.md](docs/natural.md).
- Derived values, time buckets, pagination, streaming, and repeated-snapshot
  helpers. See [docs/computed-fields.md](docs/computed-fields.md),
  [docs/time-buckets.md](docs/time-buckets.md), and
  [docs/usecases.md](docs/usecases.md).
- Multi-source execution with typed join bindings and reusable dataset bundles.
  See [docs/sql-like.md](docs/sql-like.md),
  [docs/natural.md](docs/natural.md), and [docs/reports.md](docs/reports.md).

### Workflow Helper

- Chart payload mapping, report definitions, chart presets, stats presets,
  tree row shaping, and tabular schema metadata. See [docs/charts.md](docs/charts.md),
  [docs/tree.md](docs/tree.md),
  [docs/reports.md](docs/reports.md),
  [docs/stats-presets.md](docs/stats-presets.md), and
  [docs/tabular-schema.md](docs/tabular-schema.md).

### Integration

- Runtime-scoped policy, file-loader defaults, computed fields, and natural
  vocabulary. See [docs/entry-points.md](docs/entry-points.md),
  [docs/advanced-features.md](docs/advanced-features.md),
  and [docs/telemetry.md](docs/telemetry.md).
- Optional Spring Boot starter and autoconfigure modules. See
  [docs/modules.md](docs/modules.md).

### Compatibility adapter

- File-boundary loading into typed rows before normal query execution. See
  [docs/files.md](docs/files.md) and [docs/csv.md](docs/csv.md).

### Tooling

- Snapshot comparison, regression fixtures, metamodel generation, benchmarks,
  pushdown-readiness metadata, and diagnostics. See [docs/advanced-features.md](docs/advanced-features.md),
  [docs/snapshot-comparison.md](docs/snapshot-comparison.md),
  [docs/regression-fixtures.md](docs/regression-fixtures.md),
  [docs/metamodel.md](docs/metamodel.md), and
  [docs/benchmarking.md](docs/benchmarking.md).

### Advanced

- Optional policy tuning and faster-evolving public helper surfaces. See
  [docs/advanced-features.md](docs/advanced-features.md),
  [docs/caching.md](docs/caching.md), and
  [docs/reusable-wrappers.md](docs/reusable-wrappers.md).

## Public Surface Layers

- Primary authoring modes:
  `PojoLensSql`, `PojoLensNatural`, `TypedQuery`
- Reusable contracts:
  `ReportDefinition`, `SavedReport`
- Scoped runtime and integration:
  `PojoLensRuntime`
- Boundary and workflow helpers:
  `PojoLensFiles`, `PojoLensCsv`, `PojoLensTree`, `PojoLensChart`, `JoinBindings`,
  `DatasetBundle`, `SqlLikeCursor`, `SnapshotComparison`

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
- workflow helper
- integration
- compatibility adapters
- tooling
- advanced

The explicit stable-surface contract and deprecation policy are documented in
[docs/public-api-stability.md](docs/public-api-stability.md).

## Runtime Presets

`PojoLensRuntime` includes `DEV`, `PROD`, and `TEST` presets for scoped policy
defaults. Detailed behavior and override examples live in
[docs/entry-points.md](docs/entry-points.md) and
[docs/sql-like.md#recipe-runtime-policy-presets](docs/sql-like.md#recipe-runtime-policy-presets).

## Current Limitations

The README keeps limits at the product-surface level. Detailed limitations live
with the owning guide.

- SQL-like does not try to be a full SQL engine; subqueries, aggregate ordering,
  and window frames are intentionally bounded. See
  [docs/sql-like.md#current-limitations](docs/sql-like.md#current-limitations).
- Natural queries use controlled grammar, not free-form language; bounded
  subquery phrases are documented with that grammar. See
  [docs/natural.md#current-limitations](docs/natural.md#current-limitations).
- Java builder internals are not the first-read public API; use SQL-like
  templates or report definitions for reusable public query contracts.

## Documentation Map

### Start Here

- Path selection guide: [docs/usecases.md](docs/usecases.md)
- Entry point guide: [docs/entry-points.md](docs/entry-points.md)
- Reusable wrapper guide: [docs/reusable-wrappers.md](docs/reusable-wrappers.md)
- Advanced features guide: [docs/advanced-features.md](docs/advanced-features.md)

### Core Guides

- File-boundary loader guide: [docs/files.md](docs/files.md)
- CSV adapter guide: [docs/csv.md](docs/csv.md)
- Tree traversal guide: [docs/tree.md](docs/tree.md)
- SQL-like guide: [docs/sql-like.md](docs/sql-like.md)
- Natural query guide: [docs/natural.md](docs/natural.md)
- Charts: [docs/charts.md](docs/charts.md)
- Reports and presets: [docs/reports.md](docs/reports.md)
- Stats view presets: [docs/stats-presets.md](docs/stats-presets.md)
- Time buckets: [docs/time-buckets.md](docs/time-buckets.md)
- Computed fields: [docs/computed-fields.md](docs/computed-fields.md)

### Reference

- Product surface map: [docs/product-surface.md](docs/product-surface.md)
- Module boundaries: [docs/modules.md](docs/modules.md)
- Spring/JDBC bridge: [docs/jdbc.md](docs/jdbc.md)
- Public API stability policy: [docs/public-api-stability.md](docs/public-api-stability.md)
- Tabular schema: [docs/tabular-schema.md](docs/tabular-schema.md)
- Migration notes: [MIGRATION.md](MIGRATION.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Release workflow: [RELEASE.md](RELEASE.md)

### Advanced Follow-On

- Cache behavior: [docs/caching.md](docs/caching.md)
- Facet options: [docs/facets.md](docs/facets.md)
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

