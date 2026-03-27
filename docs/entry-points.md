# Entry Point Guide

For new code, prefer the explicit entry points over the compatibility facade.
`PojoLens` is now a helper-only facade for migration-friendly helpers that do
not belong on a narrower type.

## Recommended Defaults

| Scenario | Recommended entry point | Why |
| --- | --- | --- |
| Service-owned fluent query | `PojoLensCore.newQueryBuilder(rows)` | Makes the fluent path explicit and keeps new code on the core engine surface. |
| Dynamic or config-driven SQL-like query | `PojoLensSql.parse(queryText)` | Makes the SQL-like path explicit and avoids routing new code through the compatibility facade. |
| Reusable SQL-like template | `PojoLensSql.template(queryText, params...)` | Keeps parameter-schema-driven SQL-like flows on the explicit SQL entry point. |
| Runtime-scoped policy, DI, or multi-tenant execution | `PojoLens.newRuntime(...)` -> `runtime.newQueryBuilder(...)` / `runtime.parse(...)` | Keeps lint mode, strict parameter typing, telemetry, caches, and computed fields instance-scoped. |
| Chart mapping from already-produced rows | `PojoLensChart.toChartData(rows, spec)` | Uses the chart helper directly when query execution is already done. |
| Compatibility or helper namespace | `PojoLens.*` | Hosts the remaining helper surface such as runtime creation, keyset cursor helpers, bundles, reports, and snapshot comparison. |

## Decision Rules

- Use `PojoLensCore` when the query shape is owned by application code and you are composing it through fluent builder calls.
- Use `PojoLensSql` when the query is stored in config, assembled dynamically, or otherwise represented as SQL-like text.
- Use `PojoLensRuntime` when query behavior should follow instance-scoped policy instead of global defaults, especially for injected app runtimes, tenant-specific settings, or test/runtime presets.
- Use `PojoLensChart` when you already have rows and only need deterministic chart payload mapping.
- Use `PojoLens` mainly as a helper-only compatibility facade for `newRuntime(...)`, keyset cursor helpers, `report(...)`, `bundle(...)`, and `compareSnapshots(...)`.

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
- `PojoLens.compareSnapshots(...)`

That is a surface-organization choice, not a separate execution engine.

`PojoLens` no longer carries the overlap aliases below. Use the explicit entry
points directly:

- `PojoLens.newQueryBuilder(...)` -> `PojoLensCore.newQueryBuilder(...)`
- `PojoLens.parse(...)` -> `PojoLensSql.parse(...)`
- `PojoLens.template(...)` -> `PojoLensSql.template(...)`
- `PojoLens.toChartData(...)` -> `PojoLensChart.toChartData(...)`
