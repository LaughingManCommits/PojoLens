# PojoLens!
From `List<T>` to query-ready results, without a database.

`PojoLens` is a POJO-first in-memory query library for Java. Start with one
authoring mode, run queries over already-loaded objects, and add reusable
reports, file loaders, charts, or runtime policy only when the workflow needs
them.

## Installation

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>2026.04.29.1809</version>
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
  <version>2026.04.29.1809</version>
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

## Quick Integration

- AI agent or automation: read [AGENTS.md](AGENTS.md) first. It defines the
  repo workflow, AI memory rules, and required validation steps.
- New application code: start with one primary authoring mode only:
  `PojoLensSql`, `PojoLensNatural`, or `TypedQuery`.
- Reusable business queries: add `ReportDefinition` or `SavedReport` after the
  authoring path is clear.
- File-boundary onboarding: use `PojoLensFiles` for CSV, TSV, JSON, or JSONL
  before normal query execution.
- Scoped policy or DI integration: add `PojoLensRuntime` only if the host
  needs runtime defaults, tenant policy, or Spring wiring.
- Default validation: run `mvn -B -ntp test`.

## Why PojoLens

- Query existing domain classes directly (no ORM model rewrite).
- Use SQL-like strings as the default public query surface.
- Use controlled plain-English queries when authors need guided non-SQL text.
- Use typed DSL field constants when query logic is owned by Java code.
- Reuse the same engine for filtering, ordering, grouping, joins, bounded
  subqueries, windows, `QUALIFY`, and chart/table output mapping.
- Add reusable reports, runtime policy, or boundary loaders only when the core
  query path is already clear.

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

## Choose A Path

For new code, choose one primary authoring mode first.

| If you need...                                            | Choose...                                        | Read next                                                                                              |
|-----------------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Default query authoring over in-memory rows               | `PojoLensSql` (`parse(...)`, `template(...)`)    | [docs/entry-points.md](docs/entry-points.md), [docs/sql-like.md](docs/sql-like.md)                     |
| Guided text queries for non-SQL users                     | `PojoLensNatural` (`parse(...)`, `template(...)`) | [docs/entry-points.md](docs/entry-points.md), [docs/natural.md](docs/natural.md)                       |
| Code-owned typed filters, joins, grouped aggregates, `HAVING`, windows, bounded subqueries, aggregate window frames, and ordering | `TypedQuery`                               | [docs/entry-points.md](docs/entry-points.md), [docs/typed.md](docs/typed.md), [docs/metamodel.md](docs/metamodel.md) |

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

## Quick Start

Short examples for the primary paths. Broader recipes for joins, grouping,
windows, templates, charts, and reusable contracts live in the linked docs.

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
- Typed query joins, grouped aggregates, `HAVING`, windows, `QUALIFY`, and
  bounded subqueries: [docs/typed.md](docs/typed.md)
- Tree traversal from flat parent-ID rows: [docs/tree.md](docs/tree.md)
- Charts, reports, and presets: [docs/output-helpers.md](docs/output-helpers.md),
  [docs/reports.md](docs/reports.md), [docs/stats-presets.md](docs/stats-presets.md)

## Limits And Non-Goals

- PojoLens is not a database query engine. Use database-native tools when
  filtering, joining, paging, locking, or aggregating should happen before rows
  are loaded into memory.
- SQL-like and natural surfaces are intentionally bounded, not full SQL or
  free-form language. See
  [docs/sql-like.md#current-limitations](docs/sql-like.md#current-limitations)
  and [docs/natural.md#current-limitations](docs/natural.md#current-limitations).
- Correlated and scalar subqueries remain text-only; the typed path stays
  focused on readable code-owned query composition. See
  [docs/typed.md](docs/typed.md).

## Docs

- Choosing a path: [docs/entry-points.md](docs/entry-points.md),
  [docs/usecases.md](docs/usecases.md)
- Authoring guides: [docs/sql-like.md](docs/sql-like.md),
  [docs/natural.md](docs/natural.md), [docs/typed.md](docs/typed.md)
- Reusable contracts and output helpers:
  [docs/reusable-wrappers.md](docs/reusable-wrappers.md),
  [docs/reports.md](docs/reports.md), [docs/output-helpers.md](docs/output-helpers.md)
- File and tree boundaries: [docs/files.md](docs/files.md),
  [docs/csv.md](docs/csv.md), [docs/tree.md](docs/tree.md)
- Tooling and policy: [docs/build-tooling.md](docs/build-tooling.md),
  [docs/metamodel.md](docs/metamodel.md),
  [docs/advanced-features.md](docs/advanced-features.md),
  [docs/public-api-stability.md](docs/public-api-stability.md)
- Reference: [docs/modules.md](docs/modules.md),
  [docs/product-surface.md](docs/product-surface.md), [MIGRATION.md](MIGRATION.md),
  [CHANGELOG.md](CHANGELOG.md)

## Development

Local validation:

```bash
mvn -B -ntp test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for lint, benchmarks, and release-quality guardrail commands.

