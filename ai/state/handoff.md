# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; `WP8.1` is done and `WP8.2` / `WP8.3` are ready in the `Entropy Reduction` roadmap.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- `Entropy Reduction` is the active engineering roadmap; `WP8.2` public leak/internalization decisions and `WP8.3` wrapper/binding simplification are now the next executable packages.
- Release retry or release verification is the main pending repo task.
- Starter example and dashboard simplification work are complete and validated.
- Keep hot memory small; use `ai/state/recent-validations.md` and cold files for detail.

## Facts

- `PojoLens` is helper-only; `PojoLensRuntime` owns public cache tuning.
- User-facing docs now match the helper-only `PojoLens` facade and runtime-only public cache-tuning model.
- The active backlog is the `Entropy Reduction` roadmap in `TODO.md`.
- `WP8.1` baseline is recorded in `docs/entropy-audit.md`: `122` public top-level types across `36` packages, including `52` clear internalization candidates.
- Runtime code lives in `pojo-lens/src/...`; benchmark code lives in `pojo-lens-benchmarks/src/...`.
- The starter example at `examples/spring-boot-starter-basic` is the current reference app and has Java Playwright E2E coverage.
- Recent exact validations live in `ai/state/recent-validations.md`.
- Release details and checklist live in `ai/core/runbook.md` and `RELEASE.md`.
- AI memory default query prefers hot, warm, and cold material; use `-Tier "cold,archive"` with `-Path "ai/log/archive/*"` for raw archive drilldowns.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- After AI memory retrieval changes: `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- repo structure: `ai/core/module-index.md`, `ai/core/architecture-map.md`
- docs and process: `ai/core/documentation-index.md`, `ai/core/runbook.md`
- public-surface baseline: `docs/entropy-audit.md`
- recent validation details: `ai/state/recent-validations.md`
- archive history summary: `ai/log/archive/*-summary.md`
- benchmarks only when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`
