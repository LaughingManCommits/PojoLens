# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: cut next date-based release.

## Focus

- `PojoLensTree` complete: TREE-WP1 through TREE-WP3 implemented and documented; `TODO.md` has no active TODOs.
- Ecosystem-positioning follow-up complete: README says when not to use PojoLens, benchmarking docs define honest comparison boundaries, SQL-like/natural docs include input-safety guidance, and binary-compat includes tree contracts.
- Release is next: ship April feature/docs work; Checkstyle baseline is refreshed and gate-clean.

## Facts

- `2026-04-17`: `PojoLensTree` root facade added with `fromFlat(...)` and `subtreeOf(...)`.
- `2026-04-17`: `TreeTraversalBuilder` supports subtree, maxDepth, prune, leavesOnly, toList, and toEntries.
- `2026-04-17`: Tree semantics are deterministic: non-null unique IDs, orphan roots, cycle failure, source-order roots/siblings, BFS output, and optional `TreeEntry<T>` metadata.
- `2026-04-17`: `pojo-lens/pom.xml` binary-compat includes `PojoLensTree`, `TreeTraversalBuilder`, and `TreeEntry`.
- `2026-04-17`: SQL-like/natural user-authored query guidance is params first, approved field/source exposure, lint mode, strict typing, and external authorization.
- `2026-04-15`: `ReflectionUtil` cleanup complete; direct field reads include final fields and field-graph traversal allocates less.
- `2026-04-16`: `FastArrayQuerySupport` cleanup complete; hot-path iterator/allocation cleanup and dead overload deletion done.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- Correlated/scalar subqueries and broad SQL planning stay opt-in only.

## Validate

- After code changes: `mvn -B -ntp test`, then core guardrail suite + threshold checker (see `docs/benchmarking.md`)
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-17` `mvn -B -ntp -Plint verify -DskipTests` and `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .` passed after baseline refresh (`15691` entries, `new=0 fixed=0`).

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- tree: `docs/tree.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensTree.java`, `pojo-lens/src/main/java/laughing/man/commits/tree/*`
- CSV: `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
