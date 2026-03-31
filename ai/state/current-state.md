# Current State

## Repo

- Multi-module Maven Java 17 library: runtime, Spring Boot modules, and benchmark tooling.
- Current runtime artifact is `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`.
- Release naming is date-based: Maven `YYYY.MM.DD.HHmm`, Git `release-<version>`.

## Focus

- `WP9` context-loading hardening is complete and live.
- Main repo task is Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-03-31`: repeated non-join `SqlLikeBoundQuery` runs now reuse materialized source rows.
- `2026-03-31`: the full forked suite in `target/benchmarks/2026-03-31-full/` passed thresholds, failed chart parity, and is mapped in `TODO.md`.
- `2026-03-31`: the cache-reset follow-up in `target/benchmarks/2026-03-31-followup-cache-reset/` cut chart parity failures from `15` to `3`.
- `2026-03-31`: the warmed chart rerun in `target/benchmarks/2026-03-31-followup-stability/charts-full/` passed thresholds and chart parity.
- `2026-03-31`: plain source-backed SQL-like scatter charts now skip `QueryRow` materialization; direct and bound are near-allocation parity.
- `2026-03-31`: the local Claude orchestration MVP lives in `ai/orchestrator/` and `scripts/claude-orchestrator*`; validate/run dry-runs passed and memory indexes it as `ai-orchestrator`.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish uploaded the bundle but failed signature verification before key propagation finished.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Remaining warm hotspots are SQL windows, computed-field join materialization, reflection/projection conversion, and list materialization.
- Live non-interactive Claude worker execution is unverified here; earlier `claude -p` probes timed out.
- Module-routing retrieval is sensitive without facets; use `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If benchmark work resumes, use `TODO.md` plus the `2026-03-31` artifacts and focus on the shared SQL-like scatter gap.
- If orchestration work resumes, start with `scripts/claude-orchestrator.ps1 validate ...` and `run ... --dry-run`.
- After memory/doc edits, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

