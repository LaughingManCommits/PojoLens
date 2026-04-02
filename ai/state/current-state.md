# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Runtime artifact: `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`.
- Date-based releases: Maven `YYYY.MM.DD.HHmm`, Git `release-<version>`.

## Focus

- Main repo task is Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` kept direct and bound near parity and isolated reflection-heavy chart mapping.
- `2026-04-01`: Claude orchestration now defaults to repo-local `.claude-orchestrator/`.
- `2026-04-01`: `example-parallel.json` validates the first parallel copy-mode orchestrator batch.
- `2026-04-01`: `ai/orchestrator/tasks/sql-like-scatter-followup.json` is the tracked five-task scatter DAG.
- `2026-04-02`: `PojoLensNatural` and `PojoLensRuntime.natural()` now expose an MVP controlled plain-English query surface beside fluent and SQL-like.
- `2026-04-02`: the natural MVP lowers `show`/`where`/`sort by`/`limit`/`offset`, aliases, and named params into shared execution with natural-specific explain and telemetry.
- `2026-04-02`: `mvn -q -pl pojo-lens test` and `scripts/check-doc-consistency.ps1` passed after the natural-query/docs change.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish uploaded the bundle but failed signature verification before key propagation finished.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Natural-query MVP is intentionally narrow; grouping, joins, chart phrases, and runtime vocabulary binding are not implemented yet.
- Remaining warm hotspots are SQL windows, computed-field joins, reflection/projection conversion, and list materialization.
- Live non-interactive Claude worker execution is unverified; earlier `claude -p` probes timed out.
- Module-routing retrieval is sensitive without facets; use `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If plain-English query work continues, decide whether the next slice is runtime vocabulary binding or grouped-query support.
- If benchmark work resumes, use `target/benchmarks/2026-04-01-sqllike-profile/` and focus on the scatter chart-mapping gap.
- For multi-agent scatter follow-up, validate and dry-run `ai/orchestrator/tasks/sql-like-scatter-followup.json` with `--max-parallel 2`.
- For orchestration smoke tests, validate and dry-run `ai/orchestrator/tasks/example-parallel.json` with `--max-parallel 2`.
- After memory/doc edits, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

