# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:1.0.0`.
- `TODO.md` now tracks the active `Entropy Reduction` roadmap (`WP8.1`-`WP8.6`).

## Focus

- `Entropy Reduction` is active; `WP8.1` through `WP8.4` are complete and `WP8.5` is next.
- Highest-priority operational work is Maven Central release retry or verification for `v1.0.0`.
- Hot memory should stay minimal; exact validation history lives in `ai/state/recent-validations.md`.

## Verified

- `2026-03-26`: `mvn -q test`, `scripts/check-doc-consistency.ps1`, and the starter-example Playwright suite passed.
- `2026-03-27`: AI memory refresh/check and benchmark passed; SQLite cold search and fixed-query hit quality are verified.
- `2026-03-27`: `WP8.1` and `WP8.2` artifacts landed in `docs/entropy-audit.md` and `docs/entropy-internalization-decision.md`.
- `2026-03-28`: `WP8.3` landed in `docs/entropy-wrapper-binding-decision.md`; docs now default to `ReportDefinition<T>` and `JoinBindings` -> `DatasetBundle`.
- `2026-03-28`: `WP8.4` landed in `docs/entropy-execution-path-audit.md`; `WP8.5` now has three primary targets: shared fluent stage running, shared SQL-like stage accounting, and unified SQL-like output materialization, plus low-risk optional-join cleanup.
- `PojoLens` is helper-only; `PojoLensRuntime` is the public cache-tuning surface.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow publishes runtime plus Boot artifacts.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key.
- The public key was uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- AI memory becomes stale after repo structure or doc changes unless `scripts/refresh-ai-memory.ps1` is rerun.

## Next

- Start `WP8.5` using `docs/entropy-internalization-decision.md`,
  `docs/entropy-wrapper-binding-decision.md`, and
  `docs/entropy-execution-path-audit.md`.
- Retry the release workflow or a manual release dispatch for `v1.0.0`.
- After structural or doc changes, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.
