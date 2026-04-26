# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Decide benchmark backfill and WP11 release-cut scope, then run release notes and final guardrails.

## Focus

- `2026-04-26`: Completed WP14. `SqlExpressionEvaluator` now rejects null and blank expressions before any Caffeine cache access, restoring deterministic `IllegalArgumentException` behavior while keeping the valid-expression cache path unchanged.
- `2026-04-26`: Release Gate is the next active package: decide benchmark backfill, decide WP11 timing, then run release notes and final guardrails.
- `2026-04-26`: Completed WP13. `FilterExecutionPlanCacheStore.resetStats()` now swaps to a fresh empty cache, and runtime/public reset regressions are green.
- `2026-04-26`: Completed WP12. `SqlLikeExecutionSupport` now validates preferred field indexes against each `QueryRow`'s field names, and mixed-order alias/computed projection is covered by focused regressions.
- `2026-04-25`: Java 25 upgrade complete; `maven.compiler.release=25`, CI matrix `[25]`, and workflow Java versions are aligned.
- `2026-04-25`: Full pojo-lens scan (208 files) produced the WP6-WP11 performance/quality backlog.
- `2026-04-24`: Core helpers, docs continuity, and risk-console tab-navigation work all shipped.

## Facts

- `2026-04-26`: WP12 is done. `SqlLikeExecutionSupport.projectAliasedQueryRows()` now resolves `QueryRow` fields through `QueryFieldLookupUtil.findFieldValue(..., preferredIndex)`, so mismatched row-local schema order falls back to name lookup instead of returning swapped values.
- `2026-04-26`: WP13 is done. `FilterExecutionPlanCacheStore.resetStats()` now replaces the cache with a fresh empty instance under `mutationLock`, so `size()==0` immediately after reset and the next equivalent stats query records a miss.
- `2026-04-26`: WP14 is done. `SqlExpressionEvaluator` now validates null and blank expressions through a shared front-door guard before `TOKEN_CACHE` or `COMPILED_CACHE` access, so `compileNumeric`, `collectIdentifiers`, `rewriteIdentifiers`, and `evaluateNumeric` all fail with the same validation message.
- `2026-04-26`: Focused evaluator coverage passed at 6/6, the full reactor passed at 1066/1066, and `scripts/check-doc-consistency.ps1` passed after the WP14 completion and state reconciliation.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` `mvn -B -ntp -pl pojo-lens "-Dtest=SqlExpressionEvaluatorTest" test` passed at 6/6; `2026-04-26` `mvn -B -ntp test` passed at 1066/1066; `2026-04-26` `scripts/check-doc-consistency.ps1` passed; `2026-04-24` risk-console example tests passed at 36/36.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
