# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; the latest benchmark entries are the `2026-03-31` snapshot and follow-ups.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or verification for the dated version/tag scheme is the pending repo task.
- Keep context-loading stable.

## Facts

- Release versioning is date-based: Maven `YYYY.MM.DD.HHmm`, Git `release-<version>`.
- The `2026-03-31` full benchmark suite under `target/benchmarks/2026-03-31-full/` passed thresholds but failed chart parity.
- The cache-reset follow-up under `target/benchmarks/2026-03-31-followup-cache-reset/` cut chart parity failures to `3/15`.
- The warmed rerun under `target/benchmarks/2026-03-31-followup-stability/charts-full/` passed thresholds and chart parity.
- The latest scatter slice makes plain source-backed SQL-like charts skip `QueryRow` materialization; direct and bound are now near-allocation parity.
- Remaining benchmark risks are scatter allocation, SQL windows, computed-field join materialization, reflection/projection conversion, and list materialization.
- Context-loading hardening is in place: conditional cold-load matrix in `AGENTS.md` and `ai/AGENTS.md`, with query fallback via `scripts/query-ai-memory.ps1`.
- Claude orchestration now uses repo-local `.claude-orchestrator/`; copy-mode workspaces exclude that tree, and `example-parallel.json` proves the first batch can run in parallel.
- `ai/orchestrator/SYSTEM-SPEC.md` is the reusable bootstrap spec for porting AI memory plus orchestration to another repo.
- Treat the first non-dry-run orchestrator launch as an environment check; earlier direct `claude -p` probes timed out.
- For module facts, use facet-constrained lookup: `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For orchestration changes: validate `example-review.json` and `example-parallel.json`, then run `scripts/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-parallel.json --dry-run --max-parallel 2`
- When retrieval behavior changes materially:
  `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/summarization policy: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- benchmark context when needed: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`, `target/benchmarks/2026-03-31-full/`, `target/benchmarks/2026-03-31-followup-cache-reset/`, `target/benchmarks/2026-03-31-followup-stability/`
