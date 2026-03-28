# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; `WP8.1` through `WP8.4` are done and `WP8.5` is ready in the `Entropy Reduction` roadmap.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- `WP8.5` implementation is the next executable package in `Entropy Reduction`.
- Release retry or release verification is the main pending repo task.
- Keep hot memory small; use `ai/state/recent-validations.md` and cold files for detail.

## Facts

- `PojoLens` is helper-only; `PojoLensRuntime` owns public cache tuning.
- `WP8.1` baseline is recorded in `docs/entropy-audit.md`: `122` public top-level types across `36` packages, including `52` clear internalization candidates.
- `WP8.2` decisions are in `docs/entropy-internalization-decision.md`: internalize builder/filter, execution-plan, parser/AST, chart-helper, row-model, and util leaks in `1.x`; keep `SqlLikeQueryCache`, `FilterExecutionPlanCacheStore`, and `SqlLikeErrorCodes` public.
- `WP8.3` decisions are in `docs/entropy-wrapper-binding-decision.md`: `ReportDefinition<T>` is the reusable-wrapper default; `JoinBindings` is the multi-source default and `DatasetBundle` is the reusable snapshot form.
- `WP8.4` decisions are in `docs/entropy-execution-path-audit.md`: `WP8.5` should lead with shared fluent stage running, shared SQL-like stage accounting, unified SQL-like output materialization, and optional-join cleanup; prepared rebind-mode removal stays deferred.
- Runtime code lives in `pojo-lens/src/...`; benchmarks live in `pojo-lens-benchmarks/src/...`.
- Release details and checklist live in `ai/core/runbook.md` and `RELEASE.md`.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- After AI memory retrieval changes: `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- repo structure: `ai/core/module-index.md`, `ai/core/architecture-map.md`
- docs/process: `ai/core/documentation-index.md`, `ai/core/runbook.md`
- roadmap artifacts: `docs/entropy-audit.md`, `docs/entropy-internalization-decision.md`, `docs/entropy-wrapper-binding-decision.md`, `docs/entropy-execution-path-audit.md`
- recent validation detail: `ai/state/recent-validations.md`
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`
