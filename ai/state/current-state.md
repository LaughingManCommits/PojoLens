# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:1.0.0`; `TODO.md` tracks `Entropy Reduction` (`WP8.1`-`WP8.6`).

## Focus

- `WP8.5` is in progress.
- Highest-priority operational work is Maven Central release retry or verification for `v1.0.0`.

## Verified

- `2026-03-28`: `WP8.4` landed in `docs/entropy-execution-path-audit.md`.
- `2026-03-28`: the first `WP8.5` slice landed: shared SQL-like output resolution across `filter` / `stream` / `chart`, deleted `executeIteratorWithOptionalJoin(...)`, and internalized `ChartValidation`.
- `2026-03-28`: the second `WP8.5` slice landed: flat fluent `filter` and `chart` now share one internal materialization path across window/qualify, fast-array, fast-stats, and raw-row fallback.
- `2026-03-28`: `mvn -q test` passed after the `WP8.5` slice.
- `PojoLens` is helper-only; `PojoLensRuntime` is the public cache-tuning surface.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key; the public key upload is done and retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- `WP8.5` still has open SQL-like stage-accounting and grouped fluent stage-runner work.

## Next

- Continue `WP8.5`: shared SQL-like stage accounting, then grouped fluent stage-running cleanup.
- Retry the release workflow or a manual release dispatch for `v1.0.0`.
- After structural or doc changes, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.
