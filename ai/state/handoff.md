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

- `PojoLens` is removed from the public surface; owning entry/workflow types
  now carry the API directly.
- `PojoLensRuntime` owns the only public cache-tuning surface.
- `WP8.1` through `WP8.4` are recorded in `docs/entropy-audit.md`, `docs/entropy-internalization-decision.md`, `docs/entropy-wrapper-binding-decision.md`, and `docs/entropy-execution-path-audit.md`.
- `WP8.5` is complete: shared execution-path cleanup landed, SQL-like explain
  stage counts now come from live bound execution, and `ChartValidation` is
  internal.
- `WP8.6` is complete: README/selection docs, migration/release wording, and
  benchmark notes now reflect the reduced default path set.
- The stronger pre-v2 cleanup is now executed end-to-end: `PojoLens` is gone,
  raw public join-map execution overloads are gone, public static/global cache
  policy methods are gone, and the public `FilterExecutionPlanCache`
  compatibility facade is gone.
- `pojo-lens-benchmarks` is already updated to the owning-type/runtime surface,
  so full multi-module builds no longer depend on the removed facade.
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
