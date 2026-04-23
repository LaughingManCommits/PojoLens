# Entry Point Guide

For new code, use the owning type directly.
The old `PojoLens` facade is gone; the repo now documents only the intended stable dated-release
entry surface.

Core execution model:
`query string -> tokens/AST -> validated execution plan -> in-memory row processing -> typed rows/chart/table output`

## Recommended Defaults

| Scenario | Recommended entry point | Why |
| --- | --- | --- |
| Default query authoring over in-memory rows | `PojoLensSql.parse(queryText)` | Keeps the primary public query path on familiar SQL-like text while executing against existing POJOs. |
| Reusable SQL-like query shape | `PojoLensSql.template(queryText, params...)` | Keeps repeated query shapes on a fixed parameter schema. |
| Guided plain-English query text | `PojoLensNatural.parse(queryText)` | Gives non-SQL users a deterministic text surface that still lowers into the same engine; see [docs/natural.md](natural.md). |
| Reusable natural template | `PojoLensNatural.template(queryText, params...)` | Keeps parameter-schema-driven guided-text flows on the natural surface; use `runtime.natural().template(...)` when runtime vocabulary or computed fields should apply. |
| Code-owned typed query composition | `TypedQuery.from(rowType)` with generated `TypedField<T,V>` constants | Keeps field references and literal values type-checked for projection, filters, ordering, offset, and limit. |
| Typed CSV onboarding from a file boundary | `PojoLensCsv.read(path, rowType)` | Keeps CSV loading as a bounded adapter that produces typed rows for the same engine; use `CsvOptions` only for narrow delimiter/header/trim/coercion needs. |
| Flat parent-ID rows need subtree selection | `PojoLensTree.subtreeOf(rows, idFn, parentIdFn, rootId)` | Keeps hierarchy traversal as row shaping before normal SQL-like or natural execution; use `fromFlat(...)` when depth, pruning, or leaves-only options are needed. |
| CSV load diagnostics and troubleshooting | `PojoLensCsv.readWithReport(path, rowType)` | Keeps row-loading diagnostics at the file boundary, including parsed/load counts and split header diagnostics; use `runtime.csv().readWithReport(...)` when the runtime owns CSV defaults. |
| Runtime-scoped CSV onboarding defaults | `runtime.csv().read(path, rowType)` | Uses the same bounded adapter while letting delimiter/header/trim/coercion defaults live on `PojoLensRuntime`. |
| Runtime-scoped policy, DI, or multi-tenant execution | `new PojoLensRuntime()` or `PojoLensRuntime.ofPreset(...)` | Keeps lint mode, strict typing, telemetry, caches, computed fields, and natural-query vocabulary instance-scoped. |
| Chart mapping from already-produced rows | `PojoLensChart.toChartData(rows, spec)` | Uses the chart helper directly when query execution is already done. |
| Reusable business query contract | `ReportDefinition.sql(...)` or `ReportDefinition.natural(...)` | Makes reusable row/chart workflows explicit without exposing mutable engine builders. |
| One-off named secondary sources | `JoinBindings.of(...)` or `JoinBindings.builder()` | Makes multi-source SQL-like execution explicit and typed. |
| Reused multi-source snapshot | `DatasetBundle.of(primaryRows, joinBindings)` | Packages primary rows plus named secondary sources for repeated execution. |
| Snapshot diffing | `SnapshotComparison.builder(currentRows, previousRows)` | Keeps snapshot comparison on its own workflow type. |
| Keyset cursor encode/decode | `SqlLikeCursor.builder()` and `SqlLikeCursor.fromToken(token)` | Keeps cursor mechanics on the cursor type itself. |

## Decision Rules

- Use `PojoLensSql` as the default public query entry point for in-memory rows,
  including service-owned query text, config-driven queries, templates, joins,
  pagination, charts, schemas, and explain payloads.
- Use `PojoLensSql.template(...)` when a SQL-like query is reused with a fixed
  named-parameter schema.
- Use `PojoLensNatural` when the query should stay text-driven but the author
  should not have to learn SQL-like clause syntax, including grouped aggregate
  phrases such as `count of ...`, `group by`, `having`, deterministic window
  phrases with `qualify`, explicit `from ... join ... on ...` wording,
  bounded subquery/existence phrases, time-bucket phrases, and terminal chart
  phrases.
- Use `PojoLensNatural.template(...)` when that guided-text query is reused
  with a fixed named-parameter schema.
- Use `TypedQuery` when query logic is owned by Java code and should use
  generated `TypedField<T,V>` constants instead of string field names. The
  stable typed foundation covers projection, filters, ordering, offset, limit,
  explain/schema, and execution guards; use SQL-like or natural queries for
  grouping, aggregation, joins, windows, and subqueries.
- Use `PojoLensCsv` only at the file boundary when a CSV needs to become typed
  in-memory rows before normal SQL-like or natural execution;
  see [docs/csv.md](csv.md) for options, runtime defaults, type mapping, and
  error model.
- Use `PojoLensTree` when rows are already in memory but need subtree selection
  from flat ID/parent-ID fields before entering `PojoLensSql` or
  `PojoLensNatural`; see [docs/tree.md](tree.md) for traversal options,
  validation, and boundaries.
- Use `PojoLensRuntime` when query behavior should follow instance-scoped
  policy instead of the default direct-entry behavior.
- Use `PojoLensChart` when you already have rows and only need deterministic
  chart payload mapping.
- Use `ReportDefinition` when the reusable contract is the query itself, not
  just one chart/table view of it, including when that contract starts from a
  parsed natural query.
- Use `JoinBindings` for ad-hoc named secondary sources and
  `DatasetBundle` once the same multi-source snapshot will be reused.

## Runtime Choice

`PojoLensRuntime` is the preferred model when any of these need to vary by
environment, tenant, request path, or test harness:

- lint mode
- strict parameter typing
- telemetry listener registration
- computed field registry
- natural vocabulary for plain-English field aliases
- CSV adapter defaults for repeated file-boundary loads
- SQL-like parse cache and engine execution-plan cache behavior

Two public construction patterns remain:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
PojoLensRuntime devRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
runtime.setNaturalVocabulary(NaturalVocabulary.builder()
    .field("salary", "annual pay", "pay")
    .field("department", "team")
    .build());
NaturalQuery naturalQuery = runtime.natural().parse("show employees where active is true limit 10");
List<Employee> csvRows = runtime.csv().read(Path.of("employees.csv"), Employee.class);
```

Use the constructor when you want neutral defaults and explicit setup.
Use `ofPreset(...)` when the preset itself is the starting point.

## Cross-Cutting Helpers

Some helpers span multiple surface families, but they now live on their owning
types instead of on a facade:

- `SqlLikeCursor.builder()` / `SqlLikeCursor.fromToken(...)`
- `ReportDefinition.sql(...)`
- `ReportDefinition.natural(...)`
- `DatasetBundle.of(...)`
- `SnapshotComparison.builder(...)`
- `PojoLensTree.fromFlat(...)` / `PojoLensTree.subtreeOf(...)`

This keeps the public story narrower: direct engine entry points first, helper
types only where the use case actually needs them.
