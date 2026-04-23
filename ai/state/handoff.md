# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Finish remaining `STRAT-WP2` follow-up: cooperative cancellation + deterministic aborted-query metadata.
4. Use `ai/state/benchmark-state.md` only for benchmark work.
5. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- Latest release alignment is complete for `2026.04.17.1834`.
- SQL-like is public default; natural is guided text; fluent is internal.
- `STRAT-WP1` is complete.
- `STRAT-WP2` hardening fixed lazy/bound guard bypasses and join-row scan budgeting.
- Remaining WP2 follow-up is cooperative cancellation plus deterministic aborted-query metadata.
- `STRAT-WP3` typed DSL foundation is complete and review-hardened.
  - Scope: typed fields, predicates, immutable query builder, projection/filter/order/page, explain/schema, and guard interop.
  - Review fixes: nested mixed `AND`/`OR` semantics preserved; generated typed metamodel source compiles for primitive and nested-model fields.
  - Deferred: typed grouping, aggregation, joins, windows, and subqueries.
- WP3 review full suite: 972 tests green; targeted typed/public API suite: 73 tests green.
- Active roadmap: WP2 cancellation follow-up, then WP4/WP5.

## Facts

- `2026-04-19`: `QueryDiagnostics`, `QueryExposurePolicy`, and `SqlLikePlanPreview` are public.
- `2026-04-20`: `PageResult<T>` and name suggestions are public.
- `2026-04-23`: guard limits now apply to `filter`, `stream`, `iterator`, and bound-query execution.
- `2026-04-23`: pre-execution row-scan budgeting now includes join-source rows.
- `2026-04-23`: `TypedQuery.executionGuard(guard)` does pre-execution row-scan check + post-execution rows-returned/duration check. No complexity score (no plan preview in typed DSL path).
- `2026-04-23`: `QueryExecutionGuardException.of(outcome)` is now a public static factory (was package-private constructor only).
- User-authored query text still needs params, approved fields/sources, lint, strict typing, and host-owned authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then benchmark guardrails when performance changes.
- After docs/process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-23` WP3 review full Maven suite passed (972 green), plus targeted typed/public API suite (73 green).

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
