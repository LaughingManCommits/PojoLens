# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime artifact remains `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`; `TODO.md` tracks the latest benchmark follow-up.
- Release versioning is date-based: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.

## Focus

- `WP9` context-loading hardening is complete: trigger matrix and summarization guardrails are live.
- Highest-priority operational repo task remains Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-03-31`: non-join `SqlLikeBoundQuery` now reuses materialized source rows across repeated bound executions; `mvn -q -pl pojo-lens-benchmarks -am test` passed after adding bound scatter coverage.
- `2026-03-31`: full forked suite under `target/benchmarks/2026-03-31-full` passed core/chart thresholds, failed chart parity, and is mapped in `TODO.md`.
- `2026-03-31`: cache-reset follow-up under `target/benchmarks/2026-03-31-followup-cache-reset/` kept thresholds green and cut chart parity failures from `15` to `3`, leaving only `SCATTER`.
- `2026-03-31`: warmed chart stability rerun under `target/benchmarks/2026-03-31-followup-stability/charts-full/` passed chart thresholds and chart parity; the remaining chart concern is SQL-like scatter allocation, not latency parity.
- `2026-03-31`: bound scatter follow-up shows bind-first SQL-like scatter trims direct repeated-path allocation by about `1.32x` (`1k`), `1.39x` (`10k`), and `1.89x` (`100k`).

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key; the key upload is done and retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- SQL-like scatter still allocates materially more than fluent in the warmed chart GC spot-checks even though chart latency parity now passes.
- The remaining scatter gap is now split between direct repeated SQL-like rebind/materialization cost and residual chart/execution overhead after the bind-first improvement.
- Window stages, computed-field join materialization, reflection/projection conversion, and list materialization are the highest warm allocation hotspots in the new benchmark snapshot.
- Module-routing retrieval is sensitive without facets; use `scripts/query-ai-memory.ps1 -Kind ai-core`.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If benchmark follow-up resumes, use `TODO.md` plus the `2026-03-31` artifacts, then focus on the direct SQL-like scatter allocation gap and the broader hotspots.
- After memory/doc edits, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

