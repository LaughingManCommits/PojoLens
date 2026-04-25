# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `PojoLensJdbc` for new JDBC-backed read paths in the risk-console example.
4. Use `ChartType.SCATTER` normally with numeric `xField`; `ChartJsAdapter` now emits `{x,y}` payloads.
5. Run one real MySQL reviewer pass for `examples/spring-boot-starter-risk-console` when Docker or local MySQL is available.
6. Continue release preparation; docs/CHANGELOG continuity is already patched.

## Focus

- `2026-04-24`: README/docs continuity patch landed for navigation, surface maps, and example inventory.
- `2026-04-24`: core helper work shipped - scatter bridge, facet helper, JDBC bridge, and period comparison.
- `2026-04-24`: risk-console dashboard now uses tabbed top-level navigation plus PojoLens/Reports sub-tabs with hash restore and keyboard navigation.
- `2026-04-24`: risk-console dashboard behavior now lives in focused services behind the existing facade.

## Facts

- `2026-04-24`: `README.md`, `docs/README.md`, `docs/advanced-features.md`, `docs/product-surface.md`, and `docs/public-api-stability.md` now surface `facets.md` and `jdbc.md`.
- `2026-04-24`: `docs/facets.md` now matches runtime behavior: non-public declared fields and readable dotted paths work; `static` and `@Exclude` fields do not.
- `2026-04-24`: `FacetPresets.distinctCounts("field").options(rows)` returns `List<FacetOption>` sorted by count desc.
- `2026-04-24`: `PojoLensJdbc` in `pojo-lens-spring-boot-autoconfigure` wraps `JdbcTemplate` and `SqlLikeResultSetAdapter`.
- `2026-04-24`: `/api/transactions` returns `loadedRows`, `totalRows`, and `pageSize` from app-owned cursor history over the PojoLens-sorted snapshot.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-24` core suite 1036 green; risk-console suite 36 green; docs continuity check plus AI memory refresh/check green.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
