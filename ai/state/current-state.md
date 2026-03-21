# Current State

## Repository Health

- Repository is a single-module Maven Java library (`jar`) on Java 17.
- Coordinates are `io.github.laughingmancommits:pojo-lens:1.0.0`.
- `pom.xml` includes `release-central` profile for Maven Central publishing.
- CI workflows present: `.github/workflows/ci.yml` and `.github/workflows/release.yml`.
- `TODO.md` now has pagination, streaming, and optional index spikes completed.
- `2026-03-21` artifact-scope check found benchmark packaging bleed: `target/pojo-lens-1.0.0.jar` contains `198` benchmark entries, including `134` `benchmark/jmh_generated` entries.

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
- `2026-03-21`: focused index suite passed (`OptionalIndexExecutionTest`, `IndexHintJmhBenchmarkParityTest`, `PublicApiCoverageTest`) after adding fluent optional index hints and indexed candidate narrowing.
- `2026-03-21`: full `mvn -q test` passed after index API/prototype/docs updates.
- `2026-03-21`: doc consistency passed after index use-case + benchmarking updates.
- `2026-03-21`: index benchmark suites executed:
  - warm forked run: `target/benchmarks/index-hint-forked.json` (`-f 1 -wi 1 -i 3 -prof gc`)
  - cold forked run: `target/benchmarks/index-hint-cold.json` (`-f 1 -wi 0 -i 1 -prof gc`)
- `2026-03-21`: updated `TODO.md` platform hardening backlog with explicit artifact/module boundary slimming spike and validated docs consistency (`scripts/check-doc-consistency.ps1`).
- `2026-03-21`: stable API hardening validations passed:
  - focused API suites: `StablePublicApiContractTest`, `PublicSurfaceContractTest`, `PublicApiCoverageTest`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
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
- Optional index spike 3 completed:
  - Fluent API now supports optional index hints via `addIndex(String)` and `addIndex(FieldSelector<...>)`.
  - Query explain now surfaces configured `indexes`.
  - Execution prototype narrows simple POJO filter candidates through indexed equality predicates and falls back safely to scan when inapplicable.
  - Added parity and behavior coverage tests (`OptionalIndexExecutionTest`, `IndexHintJmhBenchmarkParityTest`, `PublicApiCoverageTest` updates).
  - Benchmark notes now include warm/cold gain-loss tradeoffs for index hints.
- Stable API surface spike 4 started and baseline delivered:
  - Added explicit stability policy doc: `docs/public-api-stability.md` (stable/advanced/internal tiers, inclusion rules, compatibility/deprecation policy).
  - Linked stability policy from `README.md` and `docs/modules.md`.
  - Added stable contract enforcement test: `StablePublicApiContractTest` (entry-point method presence + baseline fluent/SQL-like/runtime behavior flows).
  - Marked TODO platform hardening stable-API item complete.

## Next Actions

- Evaluate and wire binary compatibility checks in CI (`revapi` or `japicmp`).
- Decide artifact boundary for benchmark tooling (move benchmark/JMH classes out of default runtime jar or split artifact/module).
- Decide lint baseline policy (refresh baseline vs reduce inherited violations).
- Retry release workflow for `v1.0.0` (or manual dispatch) and confirm Central publish status.
