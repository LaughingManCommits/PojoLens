# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md`; the latest benchmark entries are the `2026-03-31` snapshot and follow-ups.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or verification is the pending repo task.
- Keep context-loading stable.

## Facts

- The `2026-03-31` full suite under `target/benchmarks/2026-03-31-full/` passed thresholds but failed chart parity.
- The cache-reset follow-up under `target/benchmarks/2026-03-31-followup-cache-reset/` cut chart parity failures to `3/15`.
- The warmed rerun under `target/benchmarks/2026-03-31-followup-stability/charts-full/` passed thresholds and chart parity.
- The latest scatter slice makes plain source-backed SQL-like charts skip `QueryRow` materialization; direct and bound are now near-allocation parity.
- The `2026-04-01` scatter profile under `target/benchmarks/2026-04-01-sqllike-profile/` showed direct and bound SQL-like scatter still track closely; the remaining gap is reflection-heavy source-row chart mapping (`ChartMapper.readField` / `ReflectionUtil.getFieldValue`).
- Remaining benchmark risks are scatter allocation, SQL windows, computed-field join materialization, reflection/projection conversion, and list materialization.
- Context-loading hardening is in `AGENTS.md` and `ai/AGENTS.md`, with query fallback via `scripts/query-ai-memory.ps1`.
- Claude orchestration now uses repo-local `.claude-orchestrator/`; `example-parallel.json` proves the first batch can run in parallel.
- `ai/orchestrator/SYSTEM-SPEC.md` is the reusable spec for other repos.
- Treat the first non-dry-run orchestrator launch as an environment check; earlier `claude -p` probes timed out.
- For module facts, use `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For orchestration changes: validate `example-review.json` and `example-parallel.json`, then dry-run `example-parallel.json` with `--max-parallel 2`
- When retrieval behavior changes materially:
  `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes:
  `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/summarization policy: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- benchmark context: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`, `target/benchmarks/2026-03-31-full/`, `target/benchmarks/2026-03-31-followup-cache-reset/`, `target/benchmarks/2026-03-31-followup-stability/`
- latest SQL-like scatter profile: `target/benchmarks/2026-04-01-sqllike-profile/`
