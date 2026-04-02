# Entry Point Guide

For new code, use the owning type directly.
The old `PojoLens` facade is gone; the repo now documents only the intended stable dated-release
entry surface.

## Recommended Defaults

| Scenario | Recommended entry point | Why |
| --- | --- | --- |
| Service-owned fluent query | `PojoLensCore.newQueryBuilder(rows)` | Makes the fluent path explicit and keeps new code on the core engine surface. |
| Guided plain-English query text | `PojoLensNatural.parse(queryText)` | Gives non-SQL users a deterministic text surface that still lowers into the same engine; see [docs/natural.md](natural.md). |
| Dynamic or config-driven SQL-like query | `PojoLensSql.parse(queryText)` | Keeps dynamic query text on the explicit SQL-like surface. |
| Reusable SQL-like template | `PojoLensSql.template(queryText, params...)` | Keeps parameter-schema-driven SQL flows on the SQL-like surface. |
| Runtime-scoped policy, DI, or multi-tenant execution | `new PojoLensRuntime()` or `PojoLensRuntime.ofPreset(...)` | Keeps lint mode, strict typing, telemetry, caches, computed fields, and natural-query vocabulary instance-scoped. |
| Chart mapping from already-produced rows | `PojoLensChart.toChartData(rows, spec)` | Uses the chart helper directly when query execution is already done. |
| Reusable business query contract | `ReportDefinition.sql(...)` or `ReportDefinition.fluent(...)` | Makes reusable row/chart workflows explicit without hiding the underlying engine choice. |
| One-off named secondary sources | `JoinBindings.of(...)` or `JoinBindings.builder()` | Makes multi-source SQL-like execution explicit and typed. |
| Reused multi-source snapshot | `DatasetBundle.of(primaryRows, joinBindings)` | Packages primary rows plus named secondary sources for repeated execution. |
| Snapshot diffing | `SnapshotComparison.builder(currentRows, previousRows)` | Keeps snapshot comparison on its own workflow type. |
| Keyset cursor encode/decode | `SqlLikeCursor.builder()` and `SqlLikeCursor.fromToken(token)` | Keeps cursor mechanics on the cursor type itself. |

## Decision Rules

- Use `PojoLensCore` when the query shape is owned by application code and you
  are composing it through fluent builder calls.
- Use `PojoLensNatural` when the query should stay text-driven but the author
  should not have to learn SQL-like clause syntax, including grouped aggregate
  phrases such as `count of ...`, `group by`, `having`, deterministic window
  phrases with `qualify`, explicit `from ... join ... on ...` wording,
  time-bucket phrases, and terminal chart phrases.
- Use `PojoLensSql` when the query is stored in config, assembled dynamically,
  or otherwise represented as SQL-like text.
- Use `PojoLensRuntime` when query behavior should follow instance-scoped
  policy instead of the default direct-entry behavior.
- Use `PojoLensChart` when you already have rows and only need deterministic
  chart payload mapping.
- Use `ReportDefinition` when the reusable contract is the query itself, not
  just one chart/table view of it.
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
- SQL-like parse cache and fluent execution-plan cache behavior

Two public construction patterns remain:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
PojoLensRuntime devRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
runtime.setNaturalVocabulary(NaturalVocabulary.builder()
    .field("salary", "annual pay", "pay")
    .field("department", "team")
    .build());
NaturalQuery naturalQuery = runtime.natural().parse("show employees where active is true limit 10");
```

Use the constructor when you want neutral defaults and explicit setup.
Use `ofPreset(...)` when the preset itself is the starting point.

## Cross-Cutting Helpers

Some helpers span multiple surface families, but they now live on their owning
types instead of on a facade:

- `SqlLikeCursor.builder()` / `SqlLikeCursor.fromToken(...)`
- `ReportDefinition.sql(...)`
- `ReportDefinition.fluent(...)`
- `DatasetBundle.of(...)`
- `SnapshotComparison.builder(...)`

This keeps the public story narrower: direct engine entry points first, helper
types only where the use case actually needs them.

