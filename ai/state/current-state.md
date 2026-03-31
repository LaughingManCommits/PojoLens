# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime artifact remains `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`; `TODO.md` tracks the `2026-03-31` benchmark snapshot and follow-up.
- Release versioning is date-based: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.

## Focus

- `WP9` context-loading hardening is complete: trigger matrix and summarization guardrails are live.
- Highest-priority operational repo task remains Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-03-31`: full forked suite under `target/benchmarks/2026-03-31-full` passed core/chart thresholds, failed chart parity, and is mapped in `TODO.md`.
- `2026-03-31`: cache-reset follow-up under `target/benchmarks/2026-03-31-followup-cache-reset/` kept thresholds green and cut chart parity failures from `15` to `3`, leaving only `SCATTER`.
- `2026-03-29`: context-loading matrix, routing fallback, and same-task `ai/state/*` no-reload guidance are live in `AGENTS.md` and `ai/AGENTS.md`.
- `2026-03-29`: `scripts/refresh-ai-memory.ps1`, `scripts/refresh-ai-memory.ps1 -Check`, and `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` passed (`top1=1.0`, `top3=1.0`).
- `2026-03-29`: starter examples are split into `examples/spring-boot-starter-quickstart` and `examples/spring-boot-starter-basic`.
- `2026-03-28`: pre-first-release surface cleanup is complete (`PojoLens` facade and compatibility-only overlap removed).

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key; the public key upload is done and retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- Corrected chart parity still fails for `SCATTER`, and the `LINE|100000` inversion needs a cleaner rerun before chart work is closed.
- Window stages, computed-field join materialization, reflection/projection conversion, and list materialization are the highest warm allocation hotspots in the new benchmark snapshot.
- Query quality for module-routing intent is sensitive without facet-constrained lookup; use `scripts/query-ai-memory.ps1 -Kind ai-core` for module/architecture retrieval.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If benchmark follow-up resumes, use `TODO.md` plus the `2026-03-31` full/follow-up artifacts, then focus on `SCATTER` parity and allocation hotspots.
- Keep memory indexes fresh after memory/doc changes:
  `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

