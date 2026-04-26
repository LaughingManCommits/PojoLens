# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Fix WP14 from the WP6-WP10 review, then run `scripts/check-doc-consistency.ps1`, `mvn -B -ntp test`, and the release guardrails.

## Focus

- `2026-04-26`: Completed WP13. `FilterExecutionPlanCacheStore.resetStats()` now swaps to a fresh empty cache, and runtime/public reset regressions are green.
- `2026-04-26`: WP14 is the remaining open follow-up from the WP6-WP10 review: SqlExpressionEvaluator null/blank validation before Caffeine access.
- `2026-04-26`: Completed WP12. `SqlLikeExecutionSupport` now validates preferred field indexes against each `QueryRow`'s field names, and mixed-order alias/computed projection is covered by focused regressions.
- `2026-04-25`: Java 25 upgrade complete; `maven.compiler.release=25`, CI matrix `[25]`, and workflow Java versions are aligned.
- `2026-04-25`: Full pojo-lens scan (208 files) produced the WP6-WP11 performance/quality backlog.
- `2026-04-24`: Core helpers, docs continuity, and risk-console tab-navigation work all shipped.

## Facts

- `2026-04-26`: WP12 is done. `SqlLikeExecutionSupport.projectAliasedQueryRows()` now resolves `QueryRow` fields through `QueryFieldLookupUtil.findFieldValue(..., preferredIndex)`, so mismatched row-local schema order falls back to name lookup instead of returning swapped values.
- `2026-04-26`: WP13 is done. `FilterExecutionPlanCacheStore.resetStats()` now replaces the cache with a fresh empty instance under `mutationLock`, so `size()==0` immediately after reset and the next equivalent stats query records a miss.
- `2026-04-26`: `SqlExpressionEvaluator.compileNumeric(null)` now throws `NullPointerException` via Caffeine; restore the validation contract in WP14.
- `2026-04-26`: Focused alias/projection coverage passed at 52/52, focused cache-reset coverage passed at 15/15, and the full core suite passed at 1041/1041 after the WP12/WP13 fixes.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` focused alias/projection suite passed at 52/52; `2026-04-26` focused cache-reset suite passed at 15/15; `2026-04-26` core `mvn -B -ntp -pl pojo-lens test` passed at 1041/1041; `2026-04-24` risk-console example tests passed at 36/36.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
