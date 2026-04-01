# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Runtime artifact: `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`.
- Date-based releases: Maven `YYYY.MM.DD.HHmm`, Git `release-<version>`.

## Focus

- Main repo task is Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-03-31`: chart reruns under `target/benchmarks/2026-03-31-followup-stability/` passed thresholds and parity.
- `2026-03-31`: plain source-backed SQL-like scatter charts now skip `QueryRow` materialization; direct and bound are near-allocation parity.
- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` kept direct and bound near parity and isolated reflection-heavy chart mapping.
- `2026-04-01`: Claude orchestration now defaults to repo-local `.claude-orchestrator/`.
- `2026-04-01`: copy-mode workspaces exclude `.claude-orchestrator/`; `example-parallel.json` validates a parallel batch.
- `2026-04-01`: `ai/orchestrator/SYSTEM-SPEC.md` is the portable AI memory plus orchestration contract.
- `2026-04-01`: `ai/orchestrator/tasks/sql-like-scatter-followup.json` defines the five-task scatter follow-up DAG.
- `2026-04-01`: orchestrator dry-runs now expose model choice and prompt estimates; worker prompts default to minimal context.
- `2026-04-01`: root `SPIKE.md` explores a controlled plain-English query surface beside SQL-like and fluent.
- `2026-04-01`: root `SPIKE-CSV.md` scopes CSV support as a bounded adapter into the existing engine, not a dataframe pivot.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish uploaded the bundle but failed signature verification before key propagation finished.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Remaining warm hotspots are SQL windows, computed-field joins, reflection/projection conversion, and list materialization.
- Live non-interactive Claude worker execution is unverified; earlier `claude -p` probes timed out.
- Module-routing retrieval is sensitive without facets; use `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If benchmark work resumes, use `target/benchmarks/2026-04-01-sqllike-profile/` plus `TODO.md` and focus on the scatter chart-mapping gap.
- For multi-agent scatter follow-up, validate and dry-run `ai/orchestrator/tasks/sql-like-scatter-followup.json` with `--max-parallel 2`.
- For orchestration smoke tests, validate and dry-run `ai/orchestrator/tasks/example-parallel.json` with `--max-parallel 2`.
- Use orchestration dry-runs to inspect `prompt_estimated_tokens`, `usageTotals`, and model choice before live runs.
- After memory/doc edits, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

