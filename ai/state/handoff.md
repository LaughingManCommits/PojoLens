# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; the latest benchmark entries are the `2026-03-31` full snapshot and cache-reset follow-up.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or verification for the dated version/tag scheme is the main pending repo task.
- Keep conditional context loading and summarization rules stable.

## Facts

- Release versioning is date-based now: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.
- The `2026-03-31` full benchmark suite lives under `target/benchmarks/2026-03-31-full/`; thresholds passed, but chart parity failed for every chart type.
- The corrected follow-up lives under `target/benchmarks/2026-03-31-followup-cache-reset/`; thresholds still passed and chart parity dropped to `3/15` failures after clearing fluent `FilterImpl` caches per invocation in the chart/stats harness.
- Fresh benchmark risks are `SCATTER` parity, SQL window allocation overhead, computed-field join materialization, reflection/projection conversion, and list materialization vs lazy streaming.
- Context-loading hardening is in place: conditional cold-load matrix in `AGENTS.md` and `ai/AGENTS.md`, with query fallback via `scripts/query-ai-memory.ps1`.
- Hot-context budget policy is now explicit: hard cap `240` lines and `24 KB`; target range `160-200` total lines.
- For module/architecture retrieval, use facet-constrained lookup: `scripts/query-ai-memory.ps1 -Query "<keywords>" -Kind ai-core`.
- Lint baseline was refreshed on `2026-03-29`; run `mvn -B -ntp -Plint verify -DskipTests` before using `scripts/check-lint-baseline.ps1 -WriteBaseline` again.
- Context-loading guidance is non-recursive between `AGENTS.md` and `ai/AGENTS.md`, and same-task `ai/state/*` edits do not force reloads unless needed immediately.
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
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`, `target/benchmarks/2026-03-31-full/`, `target/benchmarks/2026-03-31-followup-cache-reset/`
