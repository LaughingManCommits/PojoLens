# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`; `TODO.md` tracks `Entropy Reduction` (`WP8.1`-`WP8.6`).
- Release versioning is date-based: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.

## Focus

- The `Entropy Reduction` roadmap (`WP8.1`-`WP8.6`) is complete.
- Pre-first-release surface cleanup is now executed in code/docs, and the highest-priority
  operational work is Maven Central release retry or verification for
  `2026.03.28.1919`.

## Verified

- `2026-03-28`: parent/module POMs, release workflow, CI baseline discovery, and release/process docs now use date-based release versioning (`2026.03.28.1919`, `release-<version>`).
- `2026-03-28`: `WP8.5` completed with shared execution-path cleanup,
  execution-backed SQL-like explain stage counts, and `ChartValidation`
  internalization.
- `2026-03-28`: `WP8.6` completed with docs, migration/release wording, and
  benchmark evidence refresh around the reduced default path set.
- `2026-03-28`: the stronger pre-first-release cleanup stance is now executed: the
  `PojoLens` facade, raw public join-map execution overloads, public
  static/global cache policy methods, and the public
  `FilterExecutionPlanCache` compatibility facade are removed.
- `2026-03-28`: `pojo-lens-benchmarks` is aligned to the owning-type/runtime
  surface; `mvn -q -pl pojo-lens-benchmarks -am test-compile`, `mvn -q test`,
  and `scripts/check-doc-consistency.ps1` passed after the cleanup.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key; the public key upload is done and retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- After structural or doc changes, run `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

