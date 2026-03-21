# Current State

## Repository Health

- Repository is a single-module Maven Java library (`jar`) on Java 17.
- Coordinates are `io.github.laughingmancommits:pojo-lens:1.0.0`.
- `pom.xml` includes `release-central` profile for Maven Central publishing.
- CI workflows present: `.github/workflows/ci.yml` and `.github/workflows/release.yml`.
- `TODO.md` now has pagination + streaming spikes completed; next high-value feature is optional in-memory indexes.

## Latest Validation

- `2026-03-20`: `mvn -q test` passed after pagination changes (`OFFSET` support + docs/tests updates).
- `2026-03-20`: focused suite passed (`SqlLikeDocsExamplesTest`, `SqlLikeParserTest`, `SqlLikeMappingParityTest`, `ExplainToolingTest`, `PublicApiCoverageTest`).
- `2026-03-20`: `scripts/check-doc-consistency.ps1` passed.
- `2026-03-21`: focused SQL-like suite passed (`SqlLikeParserTest`, `SqlLikePaginationParameterSupportTest`, `SqlLikeDocsExamplesTest`, `SqlLikeErrorCodesContractTest`, `SqlLikeStrictParameterTypeModeTest`).
- `2026-03-21`: `mvn -q test` passed after adding SQL-like `LIMIT/OFFSET` named parameter support.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed.
- `2026-03-21`: expanded focused suite passed after keyset cursor primitives (`SqlLikeKeysetCursorTest`, `SqlLikeDocsExamplesTest`, `SqlLikeErrorCodesContractTest`, `PublicApiCoverageTest`, plus parser/lint/explain/strict suites).
- `2026-03-21`: `mvn -q test` passed after adding first-class SQL-like keyset cursor API and token support.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after keyset docs updates.
- `2026-03-21`: focused streaming suite passed (`StreamingExecutionOutputTest`, `SqlLikeDocsExamplesTest`, `PublicApiCoverageTest`).
- `2026-03-21`: `mvn -q test` passed after adding fluent + SQL-like streaming APIs (`iterator`/`stream`) with simple-query lazy fast path and complex-query fallback.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after streaming docs updates.
- `2026-03-21`: streaming benchmark suite (`StreamingExecutionJmhBenchmark`) executed with forked warmed run (`-f 1 -wi 1 -i 3 -prof gc`) and results captured in `target/benchmarks/streaming-execution-forked.json`.
- `2026-03-21`: `mvn -q test` passed after adding streaming benchmark scaffolding and benchmark docs/TODO updates.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after adding streaming tradeoff notes to `docs/benchmarking.md`.
- `2026-03-20`: lint baseline script currently reports baseline drift (`new=1549`, `fixed=5417`) and needs intentional baseline refresh strategy before treating as a gate.

## Release Status

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow supports tag-triggered (`v*`) and manual publish.
- Most recent Central publish reached bundle upload but failed signature verification because Central could not find the signer public key.
- Public key was then uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; publish should be retried after keyserver propagation.

## Active Work

- Pagination spike 1 completed:
  - Fluent + SQL-like `OFFSET` implemented.
  - SQL-like pagination now supports named parameters in `LIMIT/OFFSET` with integer/non-negative validation.
  - First-class SQL-like keyset cursor API is implemented (`SqlLikeCursor`, `keysetAfter`, `keysetBefore`) with cursor token support.
  - Cursor contract is documented (token format, required sort-field matching, non-null value requirements).
  - Tie-heavy/large dataset behavior is validated in tests.
  - Explain payloads include `offset`.
  - SQL-like grammar supports `OFFSET` and `LIMIT ... OFFSET`.
  - Docs/tests include offset, pattern-based keyset pagination, and first-class cursor flows.
- Streaming spike 2 completed:
  - New fluent `Filter` streaming APIs: `iterator(...)` and `stream(...)`.
  - New SQL-like streaming APIs: `SqlLikeQuery.stream/iterator(...)` and `SqlLikeBoundQuery.stream/iterator()`.
  - Simple POJO-source non-joined/non-aggregate/non-ordered queries now stream lazily row-by-row (no full result materialization).
  - Complex shapes (join/group/having/ordered windows/stats paths) fall back to list-backed streams for deterministic behavior.
  - Docs/tests cover fluent + SQL-like stream usage.
  - Benchmarks now document short-circuit tradeoffs (`stream().limit(50)` vs list materialization) with large allocation/latency wins for first-page consumers.

## Next Actions

- Start spike 3: optional in-memory indexes for repeated hot filter/join paths.
- Decide lint baseline policy (refresh baseline vs reduce inherited violations).
- Retry release workflow for `v1.0.0` (or manual dispatch) and confirm Central publish status.
