# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; the `Entropy Reduction` roadmap (`WP8.1` through `WP8.6`) is complete.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or release verification is the main pending repo task.

## Facts

- `PojoLens` is helper-only; `PojoLensRuntime` owns public cache tuning.
- `WP8.1` through `WP8.4` are recorded in `docs/entropy-audit.md`, `docs/entropy-internalization-decision.md`, `docs/entropy-wrapper-binding-decision.md`, and `docs/entropy-execution-path-audit.md`.
- The first `WP8.5` slice is landed: `SqlLikeExecutionFlowSupport` now shares one output-mode resolver across `filter` / `stream` / `chart`, `SqlLikeExecutionSupport.executeIteratorWithOptionalJoin(...)` is deleted, and `ChartValidation` is package-private in `laughing.man.commits.chart`.
- The second `WP8.5` slice is landed: `FilterImpl` now shares one internal materialization resolver for flat fluent `filter` and `chart`, covering window/qualify fallback, fast-array rows, fast-stats rows, and raw-row fallback without changing the fast-path choices.
- The final `WP8.5` slice is landed: SQL-like explain stage counts now come from an unpaged bound execution context that runs through the real fluent pipeline, the manual SQL-like `QUALIFY` replay helper is deleted, and `FilterImpl.filterGroups(...)` shares one internal base distinct/filter stage runner with the row-based flat path.
- `WP8.6` is landed in `docs/entropy-release-refresh.md`: README/selection docs reinforce the reduced default path set, migration and release wording call out `ChartValidation` internalization plus execution-explain alignment, and benchmark guidance now includes the new `fluentGroupedRows` and `parseAndExplainExecution` evidence paths.
- Runtime code lives in `pojo-lens/src/...`; benchmarks live in `pojo-lens-benchmarks/src/...`.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- roadmap artifacts: `docs/entropy-audit.md`, `docs/entropy-internalization-decision.md`, `docs/entropy-wrapper-binding-decision.md`, `docs/entropy-execution-path-audit.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`
