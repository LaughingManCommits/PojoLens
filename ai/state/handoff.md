# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; the latest benchmark entries are the `2026-03-31` full snapshot and follow-ups.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or verification for the dated version/tag scheme is the main pending repo task.
- Keep conditional context loading and summarization rules stable.

## Facts

- Release versioning is date-based now: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.
- The `2026-03-31` full benchmark suite lives under `target/benchmarks/2026-03-31-full/`; thresholds passed, but chart parity failed for every chart type.
- The corrected follow-up lives under `target/benchmarks/2026-03-31-followup-cache-reset/`; clearing fluent `FilterImpl` caches per invocation cut chart parity failures to `3/15`.
- The warmed stability rerun lives under `target/benchmarks/2026-03-31-followup-stability/charts-full/`; chart thresholds and chart parity passed there, so the remaining chart risk is SQL-like scatter allocation.
- The latest scatter follow-up adds a simple direct-source SQL-like chart fast path; warmed GC artifacts in `target/benchmarks/2026-03-31-followup-stability/chart-scatter-bound-gc.json` now put direct and bound scatter at near-allocation parity.
- Fresh benchmark risks are scatter allocation, SQL window allocation overhead, computed-field join materialization, reflection/projection conversion, and list materialization vs lazy streaming.
- Remaining scatter work should focus on the residual SQL-like-vs-fluent overhead shared by direct and bound runs.
- Context-loading hardening is in place: conditional cold-load matrix in `AGENTS.md` and `ai/AGENTS.md`, with query fallback via `scripts/query-ai-memory.ps1`.
- For module/architecture retrieval, use facet-constrained lookup: `scripts/query-ai-memory.ps1 -Query "<keywords>" -Kind ai-core`.
- Runtime code lives in `pojo-lens/src/...`; benchmarks live in `pojo-lens-benchmarks/src/...`.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- When retrieval behavior changes materially:
  `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/summarization policy: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`, `target/benchmarks/2026-03-31-full/`, `target/benchmarks/2026-03-31-followup-cache-reset/`, `target/benchmarks/2026-03-31-followup-stability/`
