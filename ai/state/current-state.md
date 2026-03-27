# Current State

## Repo

- Multi-module Maven Java 17 library with runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:1.0.0`.
- `TODO.md` now tracks the active `Entropy Reduction` roadmap (`WP8.1`-`WP8.6`).

## Focus

- The active engineering roadmap is `Entropy Reduction`; `WP8.1` is complete and `WP8.2` / `WP8.3` are now the next executable packages.
- Highest-priority operational work is Maven Central release retry or verification for `v1.0.0`.
- Hot memory should stay minimal; exact validation history lives in `ai/state/recent-validations.md`.

## Verified

- `2026-03-26`: `mvn -q test`, `scripts/check-doc-consistency.ps1`, and the starter example Playwright suite passed.
- `2026-03-27`: AI memory refresh/check passed; SQLite cold search is built and archive-aware.
- `2026-03-27`: AI memory benchmark passed; incremental refresh reuse is verified and fixed-query top-1/top-3 hit quality is `1.0`.
- `2026-03-27`: User-facing docs were realigned to the current helper-only `PojoLens` facade and runtime-only public cache-tuning model.
- `2026-03-27`: `WP8.1` audit was delivered in `docs/entropy-audit.md`; baseline is `122` public top-level types across `36` packages with `52` clear internalization candidates.
- `PojoLens` is helper-only; `PojoLensRuntime` is the public cache-tuning surface.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow publishes runtime plus Boot artifacts.
- The last publish attempt reached bundle upload but failed signature verification because Central could not find the signer public key.
- The public key was uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- AI memory becomes stale after repo structure or doc changes unless `scripts/refresh-ai-memory.ps1` is rerun.

## Next

- Start `WP8.2` and decide which `WP8.1` internalization candidates can move in `1.x` versus `2.x`.
- Start `WP8.3` and decide the canonical reusable-wrapper and multi-source binding stories.
- Retry the release workflow or a manual release dispatch for `v1.0.0`.
- After structural or doc changes, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.
- After AI memory retrieval changes, rerun `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`.
- Keep `mvn -q test` and `scripts/check-doc-consistency.ps1` green after code or doc changes.
