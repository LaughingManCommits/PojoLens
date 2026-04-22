# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: `STRAT-WP1` and `STRAT-WP2` are done; scope `STRAT-WP3` (typed DSL) or prepare release.

## Focus

- Java 25 CI, DOC-WP1 through DOC-WP10, `PojoLensTree`, and CI warnings are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `SURFACE-WP1` through `SURFACE-WP6` are complete except the release cut.
- SQL-like is public default; natural is guided text; fluent is internal engine DSL.
- `QOL-WP1` through `QOL-WP5` are complete.
- `STRAT-WP1` complete: SavedReport, SavedReportKind, TabularColumn.typeName(), 25 tests, contract coverage.
- `STRAT-WP2` complete: QueryExecutionGuard, QueryGuardOutcome, QueryComplexitySummary, QueryExecutionGuardException; wired into SqlLikeQuery + NaturalQuery; 23 tests, contract coverage.
- Active backlog: `STRAT-WP3` through `STRAT-WP5`; release gate partially met (2 strategic packages done).
- Product direction is embedded reporting plus governed query execution over in-memory snapshots.

## Facts

- `2026-04-18`: No public users are recorded in repo memory.
- `2026-04-18`: `PojoLensCore` is gone from the public surface; fluent planning stays under `laughing.man.commits.internal.builder`.
- `2026-04-19`: `QueryDiagnostics`, `QueryExposurePolicy`, and `SqlLikePlanPreview` are implemented on the public surface.
- `2026-04-20`: `PageResult<T>` and name suggestions are implemented.
- `2026-04-22`: `TODO.md` now tracks the strategic roadmap: stable reporting contract, governance, typed DSL, hybrid adapters, and performance.
- `2026-04-22`: `STRAT-WP1` shipped — SavedReport (versioned saved-report contract), SavedReportKind, TabularColumn.typeName(); full suite clean.
- `2026-04-22`: `STRAT-WP2` shipped — QueryExecutionGuard (builder API, pre+post checks), QueryGuardOutcome (audit metadata, block codes), QueryComplexitySummary (additive score from plan shape), QueryExecutionGuardException; wired into both SqlLikeQuery.executionGuard() and NaturalQuery.executionGuard(); GUARD_REJECTED telemetry stage; full suite clean.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with `TreeEntry<T>` metadata.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and host-owned authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then benchmark guardrails when performance changes.
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-22` STRAT-WP2 full `mvn test` on pojo-lens module passed (0 failures, 23 new tests).

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
