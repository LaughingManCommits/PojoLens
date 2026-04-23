# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Decide next: continue WP4 bridge path/benchmarks, WP5, or release preparation.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- `STRAT-WP1` complete: saved-report contract.
- `STRAT-WP2` complete: guard enforcement, join-row scan budgets, cooperative cancellation, deterministic aborted-query metadata.
  - `QueryCancellationToken` (@FunctionalInterface, `ofAtomic`/`ofThread`); block code `GUARD_CANCELLED`; `rowsReturnedBeforeAbort` on cancelled outcomes.
  - Polled in `GuardedIterator#hasNext` (lazy), `prepareExecution`, bound eager `filter`/`chart`, and TypedQuery preflight.
  - 30 cancellation tests; targeted guard/public API slice 74 green; full reactor 1024 green.
- `STRAT-WP3` complete: typed DSL — `TypedField<T,V>`, `TypedPredicate<T>`, `TypedQuery<T>`, typed metamodel. Nested AND/OR preserved; deferred: grouping/joins/windows.
- `STRAT-WP4` first slice complete: advisory pushdown-readiness preview for host-owned adapters, with explain/BIND telemetry metadata; no database execution or SQL rendering is owned.
- Active roadmap: continue WP4 bridge path/benchmarks, WP5, or release gate.

## Facts

- `2026-04-19`: `QueryDiagnostics`, `QueryExposurePolicy`, `SqlLikePlanPreview` are public.
- `2026-04-20`: `PageResult<T>` and name suggestions are public.
- `2026-04-23`: guard limits apply to `filter`, `stream`, `iterator`, bound-query execution.
- `2026-04-23`: pre-execution row-scan budgeting includes join-source rows.
- `2026-04-23`: `TypedQuery.executionGuard` checks scan + post-execution rows/duration; no complexity score.
- `2026-04-23`: pushdown readiness is planning metadata only; host apps own actual adapters and authorization.
- `2026-04-23`: `QueryExecutionGuardException.of(outcome)` is public static factory.
- `2026-04-23`: `QueryExecutionGuard#hasPreExecutionLimits()` skips plan-preview for cancel-only guards.
- `2026-04-23`: `QueryCancellationToken` and cancellation outcome APIs are stable public surface.
- User-authored query text needs params, approved fields/sources, lint, strict typing, host-owned authorization.

## Validate

- After code changes: `mvn -B -ntp test`; benchmark guardrails when perf changes.
- After docs/process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`
- Last validation: `2026-04-23` WP4 pushdown-readiness slice passed doc consistency, `git diff --check`, targeted public API tests 34 green, and full Maven suite 1032 green.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
