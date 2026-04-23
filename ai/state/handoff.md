# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Continue release preparation.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- `STRAT-WP5` complete: reflection hot-path caching, repeated join fast-state reuse, lower warmed window allocation overhead, and documented batch/columnar evaluation outcome.
- `STRAT-WP1` complete: saved-report contract.
- `STRAT-WP2` complete: guards, join-row scan budgets, cancellation token support, and deterministic aborted-query metadata.
- `STRAT-WP3` complete: typed DSL; grouping/joins/windows remain deferred.
- `STRAT-WP4` complete: pushdown preview, host-owned bridge, `ResultSet` ingestion, split completion, `PUSHDOWN` telemetry, and benchmarks.

## Facts

- `2026-04-23`: `ReflectionUtil` caches direct-field read plans and nested-path writes for repeated projection materialization.
- `2026-04-23`: reflection hotspot budgets live in `benchmarks/hotspot-thresholds.json` and `scripts/benchmark-suite-hotspot-reflection.args`.
- `2026-04-23`: repeated computed-field joins now reuse prepared fast join state for stable filter snapshots instead of rebuilding the fast join structure on every `.join()` call.
- `2026-04-23`: window execution now writes directly into final row buffers and uses cheaper partition-key shapes for common partition layouts.
- `2026-04-23`: batch/columnar evaluation closed without adding a second execution engine; existing row-array fast paths remain the preferred bounded acceleration strategy.
- `2026-04-23`: guard preflight includes join-source row scans; `TypedQuery.executionGuard` checks scan plus post-execution rows/duration.
- `2026-04-23`: pushdown bridge is materialized-row completion only; host apps still own SQL rendering, database execution, and authorization.
- `2026-04-23`: `QueryCancellationToken` and cancellation outcome APIs are public.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and host-owned authorization.

## Validate

- After code changes: `mvn -B -ntp test`; benchmark guardrails when perf changes.
- After docs/process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`
- Last validation: `2026-04-23` WP5 join/window slice passed focused fast-path/window tests (40 green), warmed join/window spot checks, reflection threshold checks, `mvn -B -ntp -pl pojo-lens-benchmarks -am test` (1018 core + 16 benchmark tests), full `mvn -B -ntp test` (1041 tests), doc consistency, diff check, and AI memory refresh/check. The strict core benchmark suite still has local failures on SQL-like HAVING/window/explainExecution benchmarks and remains a follow-up.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
