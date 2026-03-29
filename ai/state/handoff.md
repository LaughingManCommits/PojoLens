# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; `WP9` context-loading work is complete.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or release verification for the dated version/tag scheme is the main pending repo task.
- Keep conditional context loading and summarization rules stable as future changes land.

## Facts

- Release versioning is date-based now: Maven versions use
  `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.
- Context-loading hardening is in place:
  conditional cold-load matrix in `AGENTS.md` and `ai/AGENTS.md`,
  with query fallback via `scripts/query-ai-memory.ps1`.
- Hot-context budget policy is now explicit:
  hard cap `240` lines and `24 KB`; target range `160-200` total lines.
- Query routing quality is benchmarked and currently healthy:
  `ai/indexes/memory-benchmark.json` reports `top1=1.0`, `top3=1.0`.
- For module/architecture retrieval, use facet-constrained lookup:
  `scripts/query-ai-memory.ps1 -Query "<keywords>" -Kind ai-core`.
- `docs/` is now user-facing only; `consolidation-review.md` and benchmark `WP*`
  wording were removed.
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
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`

