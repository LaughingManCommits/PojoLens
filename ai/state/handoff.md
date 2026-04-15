# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- `ReflectionUtil` cleanup complete: RU-WP1 through RU-WP5 done.
- `FastArrayQuerySupport` cleanup in progress: FA-WP1 and FA-WP2 done; FA-WP3 dropped (child values must be stored — buffer reuse adds a copy); FA-WP4 through FA-WP6 remain.
- Scatter allocation concern retired: warmed `10k`/`100k` GC checks passed clean; baselines recorded.

## Facts

- `2026-04-15`: `ReflectionUtil` — `isPlatformType` → `isUserDefinedType`; dead `extractQueryFields`/`buildSchema` removed; `final` fields included in `DirectFieldReadPlan` via `READABLE_FIELD_BY_NAME_CACHE`; `collectFieldGraph` uses array-backed path stack; `HashMap` → `LinkedHashMap` in `buildMutableFieldByNameMap`.
- `2026-04-15`: `FastArrayQuerySupport` — stream `findFirst()` → direct iterator; `visitingComputedNames` allocated once per `compileJoinPlan` call.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- No default bounded subquery parity slice remains; correlated/scalar subqueries and broad SQL planning stay opt-in only.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- CSV: `TODO.md`, `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
