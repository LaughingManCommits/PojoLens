# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. WP6+WP7+WP8 done — release gate: draft release notes, run `scripts/check-doc-consistency.ps1`, run `mvn -B -ntp test`, then cut release.

## Focus

- `2026-04-25`: Java 25 upgrade complete — `maven.compiler.release=25`, CI matrix `[25]`, all workflows updated.
- `2026-04-25`: Full pojo-lens scan (208 files). Six new performance/quality WPs in TODO.md (WP6–WP11).
- `2026-04-24`: Core helpers, docs continuity, risk-console tab nav all shipped.

## Facts

- `2026-04-25`: Critical finding: `SqlExpressionEvaluator` lines 20–33 uses `Collections.synchronizedMap(LinkedHashMap)` as LRU for token and compiled-expression caches — global lock on hot per-row path.
- `2026-04-25`: `ReflectionUtil` lines 37–45: 9 unbounded global `ConcurrentHashMap` caches, no eviction, no size limit.
- `2026-04-25`: `FilterExecutionPlanCacheStore.rebuildCache()` lines 169–175: `cache = newCache()` before `putAll()` creates transient empty cache visible to concurrent readers.
- `2026-04-25`: `QueryFieldLookupUtil.findFieldIndex()` is O(n) linear scan called per-row from join/group/aggregation hot paths.
- `2026-04-24`: `PojoLensJdbc` wraps `JdbcTemplate` + `SqlLikeResultSetAdapter` in `pojo-lens-spring-boot-autoconfigure`.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-24` core 1036 green; risk-console 36 green.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
