# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Check `TODO.md` for benchmark follow-ups.
4. Use `ai/state/benchmark-state.md` only for benchmark work.
5. If AI memory freshness is uncertain, run `scripts/refresh-ai-memory.ps1 -Check`.

## Focus

- Release retry or verification is the pending repo task.

## Facts

- The `2026-04-01` scatter profile under `target/benchmarks/2026-04-01-sqllike-profile/` kept direct and bound close; the remaining gap is reflection-heavy chart mapping.
- `ai/orchestrator/tasks/sql-like-scatter-followup.json` is the tracked five-task scatter DAG.
- `PojoLensNatural` and `PojoLensRuntime.natural()` now implement the first natural-query MVP over the shared engine.
- The natural MVP covers `show`/`where`/`sort by`/`limit`/`offset`, aliases, named params, explain, telemetry, and docs alignment.
- Remaining benchmark risks are scatter allocation, SQL windows, computed-field joins, reflection/projection conversion, and list materialization.
- Claude orchestration uses repo-local `.claude-orchestrator/`; `example-parallel.json` proves the first batch can run in parallel.
- Treat the first non-dry-run orchestrator launch as an environment check; earlier `claude -p` probes timed out.

## Validate

- After code changes: `mvn -q test`
- Natural-query MVP was last validated with `mvn -q -pl pojo-lens test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For orchestration changes: validate `example-review.json` and `example-parallel.json`, then dry-run `example-parallel.json` with `--max-parallel 2`
- For scatter follow-up orchestration: validate and dry-run `sql-like-scatter-followup.json` with `--max-parallel 2`
- Before live runs, inspect the dry-run manifest for prompt size and model choice.
- When retrieval behavior changes materially: `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`
- For release-path changes: `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/summarization policy: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- benchmark context: `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md`, `target/benchmarks/2026-03-31-full/`, `target/benchmarks/2026-03-31-followup-cache-reset/`, `target/benchmarks/2026-03-31-followup-stability/`
- latest SQL-like scatter profile: `target/benchmarks/2026-04-01-sqllike-profile/`
