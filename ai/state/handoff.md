# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: cut next date-based release.

## Focus

- Java 25 CI support and DOC-WP1 through DOC-WP10 are complete.
- `PojoLensTree`, ecosystem-positioning docs, CI warnings, and Checkstyle baseline are complete.
- Release is next; `TODO.md` has no active TODOs.

## Facts

- `2026-04-18`: CI runs full Maven tests on Temurin Java `17`, `21`, and `25`.
- `2026-04-18`: Benchmark docs use dynamic `BENCHMARK_JAR` resolution and doc consistency scripts forbid hardcoded versioned benchmark jar paths.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with optional `TreeEntry<T>` metadata.
- `2026-04-17`: binary compatibility includes `PojoLensTree`, `TreeTraversalBuilder`, and `TreeEntry`.
- `2026-04-17`: SQL-like/natural user-authored query guidance is params first, approved field/source exposure, lint mode, strict typing, and external authorization.
- `2026-04-16`: `ReflectionUtil` and `FastArrayQuerySupport` cleanup complete.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- Correlated/scalar subqueries and broad SQL planning stay opt-in only.

## Validate

- After code changes: `mvn -B -ntp test`, then core guardrail suite + threshold checker (see `docs/benchmarking.md`)
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-18` `git diff --check`, `scripts/check-doc-consistency.ps1`, `py -3 scripts/check-doc-consistency.py`, `py -3 -m py_compile scripts/check-doc-consistency.py`, and `mvn -B -ntp test` passed.

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- CI: `.github/workflows/ci.yml`, `CHANGELOG.md`
- docs backlog: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- tree: `docs/tree.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensTree.java`, `pojo-lens/src/main/java/laughing/man/commits/tree/*`
- CSV: `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
