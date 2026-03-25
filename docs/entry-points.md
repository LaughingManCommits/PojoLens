# Entry Point Guide

For new code, prefer the explicit entry points over the compatibility facade.
`PojoLens` remains available for migration-friendly call sites and for facade-only helpers that are not split into a narrower type yet.
For the active pre-adoption simplification path, the target state is even
stricter: `PojoLens` stays as a helper-only facade, while the pure overlap
aliases `newQueryBuilder(...)`, `parse(...)`, `template(...)`, and
`toChartData(...)` are scheduled for removal before wider adoption.

## Recommended Defaults

| Scenario | Recommended entry point | Why |
| --- | --- | --- |
| Service-owned fluent query | `PojoLensCore.newQueryBuilder(rows)` | Makes the fluent path explicit and keeps new code on the core engine surface. |
| Dynamic or config-driven SQL-like query | `PojoLensSql.parse(queryText)` | Makes the SQL-like path explicit and avoids routing new code through the compatibility facade. |
| Reusable SQL-like template | `PojoLensSql.template(queryText, params...)` | Keeps parameter-schema-driven SQL-like flows on the explicit SQL entry point. |
| Runtime-scoped policy, DI, or multi-tenant execution | `PojoLens.newRuntime(...)` -> `runtime.newQueryBuilder(...)` / `runtime.parse(...)` | Keeps lint mode, strict parameter typing, telemetry, caches, and computed fields instance-scoped. |
| Chart mapping from already-produced rows | `PojoLensChart.toChartData(rows, spec)` | Uses the chart helper directly when query execution is already done. |
| Compatibility or migration path | `PojoLens.*` | Preserves older call sites and hosts helper methods that still live on the facade. |

## Decision Rules

- Use `PojoLensCore` when the query shape is owned by application code and you are composing it through fluent builder calls.
- Use `PojoLensSql` when the query is stored in config, assembled dynamically, or otherwise represented as SQL-like text.
- Use `PojoLensRuntime` when query behavior should follow instance-scoped policy instead of global defaults, especially for injected app runtimes, tenant-specific settings, or test/runtime presets.
- Use `PojoLensChart` when you already have rows and only need deterministic chart payload mapping.
- Use `PojoLens` mainly as a compatibility facade and helper namespace for `newRuntime(...)`, keyset cursor helpers, `report(...)`, and `bundle(...)`.

## Runtime Choice

`PojoLensRuntime` is the preferred model when any of these need to vary by environment, tenant, request path, or test harness:

- lint mode
- strict parameter typing
- telemetry listener registration
- computed field registry
- SQL-like parse cache and fluent execution-plan cache behavior

Two equivalent creation styles are public:

```java
PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
PojoLensRuntime sameRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
```

The facade-based creation path is the simpler default for app code; the direct `PojoLensRuntime` factory is useful when you want the type to own construction explicitly.

## What Still Lives On `PojoLens`

Some helpers intentionally remain on the facade because they span multiple product-surface families:

- `PojoLens.newRuntime(...)`
- `PojoLens.newKeysetCursorBuilder()`
- `PojoLens.parseKeysetCursor(...)`
- `PojoLens.report(...)`
- `PojoLens.bundle(...)`

That is a surface-organization choice, not a separate execution engine.

Under the pre-adoption simplification decision recorded in
[consolidation-review.md](consolidation-review.md), the following methods are
not part of that helper-only target state and should be migrated away from now:

- `PojoLens.newQueryBuilder(...)` -> `PojoLensCore.newQueryBuilder(...)`
- `PojoLens.parse(...)` -> `PojoLensSql.parse(...)`
- `PojoLens.template(...)` -> `PojoLensSql.template(...)`
- `PojoLens.toChartData(...)` -> `PojoLensChart.toChartData(...)`
