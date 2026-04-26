# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Fix WP12, WP13, and WP14 from the WP6-WP10 review, then run `scripts/check-doc-consistency.ps1`, `mvn -B -ntp test`, and the release guardrails.

## Focus

- `2026-04-26`: Reviewed WP6-WP10 implementation; TODO now tracks WP12-WP14 for mixed-schema QueryRow alias projection, stats-plan-cache reset semantics, and SqlExpressionEvaluator null/blank validation.
- `2026-04-25`: Java 25 upgrade complete; `maven.compiler.release=25`, CI matrix `[25]`, and workflow Java versions are aligned.
- `2026-04-25`: Full pojo-lens scan (208 files) produced the WP6-WP11 performance/quality backlog.
- `2026-04-24`: Core helpers, docs continuity, and risk-console tab-navigation work all shipped.

## Facts

- `2026-04-26`: `SqlLikeExecutionSupport.projectAliasedQueryRows()` reuses first-row field indexes across all `QueryRow`s; mixed-order rows can return wrong aliased values (WP12).
- `2026-04-26`: `FilterExecutionPlanCacheStore.resetStats()` still repopulates entries via `rebuildCache()`; the next identical query hits instead of missing (WP13).
- `2026-04-26`: `SqlExpressionEvaluator.compileNumeric(null)` now throws `NullPointerException` via Caffeine; restore the validation contract in WP14.
- `2026-04-26`: Full core suite still passes (`mvn -B -ntp -pl pojo-lens test`, 1037/1037), so these regressions need targeted coverage.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-26` core `mvn -B -ntp -pl pojo-lens test` passed at 1037/1037; `2026-04-24` risk-console example tests passed at 36/36.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
