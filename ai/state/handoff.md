# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: cut next date-based release.

## Focus

- `ReflectionUtil` cleanup complete: RU-WP1 through RU-WP5 done.
- `FastArrayQuerySupport` cleanup complete: FA-WP1, FA-WP2, FA-WP5, FA-WP6 done; FA-WP3 dropped (child values must be stored); FA-WP4 dropped (HashMap intentional on hot lookup index).
- Scatter allocation concern retired: warmed `10k`/`100k` GC checks passed clean; baselines recorded.
- `PojoLensTree` complete: TREE-WP1 through TREE-WP3 implemented and documented; `TODO.md` has no active TODOs.

## Facts

- `2026-04-17`: `PojoLensTree` root facade added with `fromFlat(...)` and `subtreeOf(...)`.
- `2026-04-17`: `TreeTraversalBuilder` supports subtree, maxDepth, prune, leavesOnly, toList, and toEntries.
- `2026-04-17`: `TreeEntry<T>` exposes node/depth/parent traversal metadata.
- `2026-04-17`: Tree semantics are ID-based and deterministic: non-null unique IDs, orphan roots, parent-ID cycle failure, source-order roots/siblings, BFS output.
- `2026-04-15`: `ReflectionUtil` - `isPlatformType` renamed to `isUserDefinedType`; dead `extractQueryFields`/`buildSchema` removed; final fields included in `DirectFieldReadPlan`; `collectFieldGraph` uses array-backed path stack; `buildMutableFieldByNameMap` uses `LinkedHashMap`.
- `2026-04-15`: `FastArrayQuerySupport` - stream `findFirst()` replaced with direct iterator; `visitingComputedNames` allocated once per `compileJoinPlan` call.
- `2026-04-16`: `FastArrayQuerySupport` - dead 3-arg `orderRows` deleted; `andMatched`/`andFailed` renamed to `andAnyPassed`/`andAnyFailed`.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- Correlated/scalar subqueries and broad SQL planning stay opt-in only.

## Validate

- After code changes: `mvn -B -ntp test`, then core guardrail suite + threshold checker (see `docs/benchmarking.md`)
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-17` full `mvn -B -ntp test` and `scripts/check-doc-consistency.ps1` passed.

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- tree: `docs/tree.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensTree.java`, `pojo-lens/src/main/java/laughing/man/commits/tree/*`
- CSV: `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
