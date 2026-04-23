# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Continue the remaining WP5 items or switch to release preparation.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- `STRAT-WP5` first slice landed: reflection hot-path caching plus warmed reflection hotspot guardrails.
- `STRAT-WP1` complete: saved-report contract.
- `STRAT-WP2` complete: guards, join-row scan budgets, cancellation token support, and deterministic aborted-query metadata.
- `STRAT-WP3` complete: typed DSL; grouping/joins/windows remain deferred.
- `STRAT-WP4` complete: pushdown preview, host-owned bridge, JDBC `ResultSet` ingestion, split completion, `PUSHDOWN` telemetry, and benchmark paths.
- Active roadmap: start WP5 or release gate.

## Facts

- `2026-04-23`: `ReflectionUtil` caches direct-field read plans across equivalent selections and uses cached nested-path writes during projection materialization.
- `2026-04-23`: stable warmed reflection hotspot budgets now live in `benchmarks/hotspot-thresholds.json` and run through `scripts/benchmark-suite-hotspot-reflection.args`.
- `2026-04-19`: `QueryDiagnostics`, `QueryExposurePolicy`, and `SqlLikePlanPreview` are public.
- `2026-04-20`: `PageResult<T>` and name suggestions are public.
- `2026-04-23`: guard preflight includes join-source row scans; `TypedQuery.executionGuard` checks scan plus post-execution rows/duration.
- `2026-04-23`: pushdown bridge is materialized-row completion only; host apps still own SQL rendering, database execution, and authorization.
- `2026-04-23`: `QueryExecutionGuardException.of(outcome)` is public static factory.
- `2026-04-23`: `QueryExecutionGuard#hasPreExecutionLimits()` skips plan-preview for cancel-only guards.
- `2026-04-23`: `QueryCancellationToken` and cancellation outcome APIs are stable public surface.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and host-owned authorization.

## Validate

- After code changes: `mvn -B -ntp test`; benchmark guardrails when perf changes.
- After docs/process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`
- Last validation: `2026-04-23` WP5 reflection slice passed focused `ReflectionUtil` tests (14 green), warmed forked reflection threshold checks, `mvn -B -ntp -pl pojo-lens-benchmarks -am test` (1017 core + 16 benchmark tests), full `mvn -B -ntp test` (1040 tests), doc consistency, diff check, and AI memory refresh/check.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
