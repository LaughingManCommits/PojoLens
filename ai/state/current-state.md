# Current State

## Repository Health

- Repository is a multi-module Maven Java library on Java 17 (`pojo-lens-parent` + `pojo-lens` + `pojo-lens-spring-boot-autoconfigure` + `pojo-lens-spring-boot-starter` + `pojo-lens-benchmarks`).
- Runtime consumer coordinates remain `io.github.laughingmancommits:pojo-lens:1.0.0`.
- Central release profiles now exist for deployable modules `pojo-lens`, `pojo-lens-spring-boot-autoconfigure`, and `pojo-lens-spring-boot-starter`.
- CI workflows present: `.github/workflows/ci.yml` and `.github/workflows/release.yml`.
- `TODO.md` now has pagination, streaming, optional index, stable API, binary compatibility, artifact/module-boundary, and SQL window-functions MVP spikes completed.
- `2026-03-21` artifact-scope split is complete: runtime jar excludes benchmark/JMH classes and benchmark tooling is isolated in `pojo-lens-benchmarks`.
- `2026-03-22` source layout split is complete: runtime code/tests/resources now live under `pojo-lens/src/...` and benchmark code/tests/resources now live under `pojo-lens-benchmarks/src/...` (no shared top-level `src` compile path).

## Latest Validation

- `2026-03-23`: SQL window-functions MVP (`OVER`) passed:
  - parser/AST support added for `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()` with `OVER (PARTITION BY ... ORDER BY ...)`
  - execution support added for window-value computation after `WHERE` and before query-level `ORDER BY`/pagination/projection
  - deterministic tie handling for non-unique window `ORDER BY` uses stable source-row order fallback
  - validation requires `OVER(... ORDER BY ...)` and restricts window selects to non-aggregate query shapes
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: SQL-like maintainability refactor slices 2/3 + lint baseline refresh passed:
  - extracted SQL-like execution flow/stage telemetry internals out of `SqlLikeQuery` into new package-private helper `SqlLikeExecutionFlowSupport`
  - extracted SQL-like tokenization internals (`tokenize`, token model, position map) out of `SqlLikeParser` into new package-private helper `SqlLikeTokenizationSupport`
  - size reductions: `SqlLikeQuery` `1002 -> 748` lines, `SqlLikeParser` `1292 -> 1070` lines
  - focused SQL-like regression: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,SqlLikeParserTest,SqlLikeMappingParityTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
- `2026-03-22`: SQL-like maintainability refactor slice passed:
  - extracted prepared-execution cache/binding internals out of `SqlLikeQuery` into new package-private helper `SqlLikePreparedExecutionSupport`
  - focused SQL-like regression: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,SqlLikeParserTest,SqlLikeMappingParityTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: starter distribution + integration hardening passed:
  - starter module integration smoke test (app context + endpoint): `mvn -q -pl pojo-lens-spring-boot-starter -am test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - release-path package validation for publishable modules:
    `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`
- `2026-03-22`: Spring Boot dependency baseline updated to `4.0.4` in parent BOM import and validated with:
  - focused module regression: `mvn -q -pl pojo-lens-spring-boot-autoconfigure -am test`
  - full regression: `mvn -q test`
- `2026-03-22`: Added runnable Spring Boot starter example project (`examples/spring-boot-starter-basic`) and validated with:
  - local starter install for example resolution: `mvn -q -pl pojo-lens-spring-boot-starter -am install -DskipTests`
  - example package: `mvn -q -f examples/spring-boot-starter-basic/pom.xml -DskipTests package`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: Spring Boot starter/autoconfigure baseline passed:
  - focused module regression: `mvn -q -pl pojo-lens-spring-boot-autoconfigure -am test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: `mvn -q test` passed after moving all source/test/resource trees into module-local `src` directories and simplifying module POM source configuration.
- `2026-03-22`: `scripts/check-doc-consistency.ps1` passed after source-layout migration.
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
- `2026-03-21`: binary compatibility hardening validations passed:
  - baseline tag worktree install: `v1.0.0` -> `mvn -q -DskipTests install`
  - compatibility check: `mvn -q -Pbinary-compat -DskipTests -Dcompat.baseline.version=1.0.0 verify`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-21`: artifact/module-boundary slimming validations passed:
  - release path package: `mvn -B -ntp -Prelease-central -DskipTests package` (runtime javadocs now pass with benchmark source exclusion)
  - full regression: `mvn -ntp test`
  - binary compatibility: `mvn -q "-Pbinary-compat" "-DskipTests" "-Dcompat.baseline.version=1.0.0" verify`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - runtime jar scope assertion: `jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` -> `0`
- `2026-03-22`: lint baseline was intentionally refreshed after SQL-like decomposition (`scripts/checkstyle-baseline.txt`, `10779` entries) and currently reports `new=0`, `fixed=0`.

## Release Status

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow supports tag-triggered (`v*`) and manual publish.
- Release workflow now validates root version and deploys module set
  `pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter` with `-Prelease-central` (benchmark module remains deploy-skipped).
- Most recent Central publish reached bundle upload but failed signature verification because Central could not find the signer public key.
- Public key was then uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; publish should be retried after keyserver propagation.

## Active Work

- SQL window analytics spike 1 (Window Functions MVP) completed:
  - Added SQL-like parser/AST support for rank window syntax:
    `ROW_NUMBER()/RANK()/DENSE_RANK() OVER (PARTITION BY ... ORDER BY ...)`.
  - Added SQL-like execution-stage window computation that runs after `WHERE` and before final projection.
  - Query-level `ORDER BY` now supports window aliases for window-enabled query shapes.
  - Determinism guardrails:
    missing window `ORDER BY` now fails validation; non-unique window ordering uses stable source-row tie fallback.
  - Added parser/runtime/docs coverage:
    `SqlLikeWindowFunctionTest` + parser/docs updates.
  - Updated SQL-like docs and marked TODO window spike item complete.
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
- Binary compatibility spike 5 baseline delivered:
  - Added `binary-compat` Maven profile with `japicmp` (`pom.xml`) gated by `compat.baseline.version`.
  - Scoped compatibility checks to the documented stable API types and enabled fail-on binary/source incompatibilities.
  - Added CI `binary-compat` job (`.github/workflows/ci.yml`) that resolves latest `v*` tag baseline, installs baseline artifact via detached worktree, and runs compatibility verification.
  - Added local run guidance to `CONTRIBUTING.md`.
  - Updated stable API policy enforcement note in `docs/public-api-stability.md`.
  - Marked TODO binary compatibility item complete.
- Artifact/module boundary spike 6 delivered:
  - Converted root build to parent POM (`pojo-lens-parent`) with modules `pojo-lens` and `pojo-lens-benchmarks`.
  - Runtime module now owns module-local runtime source/tests/resources under `pojo-lens/src/...` and participates in `release-central` publishing alongside Spring Boot integration modules.
  - Benchmark module now owns module-local benchmark source/tests/resources under `pojo-lens-benchmarks/src/...`, plus JMH annotation processing and benchmark runner shading.
  - Runtime test coupling to benchmark classes was removed (`FilterImplFastPathTest` now uses local fixture generation).
  - Benchmark tests were updated to module-aware resource paths.
  - CI now includes runtime artifact-scope assertion to block benchmark-class bleed into runtime jar.
  - TODO artifact-slimming item is marked complete.
- Spring Boot integration baseline delivered:
  - Added `pojo-lens-spring-boot-autoconfigure` with `pojo-lens.*` configuration properties and `PojoLensRuntime` auto-configuration.
  - Added optional Micrometer telemetry bridge (`QueryTelemetryListener`) with low-cardinality `stage`/`query_type` tags.
  - Added `pojo-lens-spring-boot-starter` for simplified Boot dependency wiring.
  - Starter now includes `micrometer-core` to prevent Boot 4 classpath introspection failure when evaluating micrometer-conditional auto-config methods.
  - Parent build now imports Spring Boot BOM version `4.0.4` for starter/autoconfigure dependency alignment.
  - Added standalone runnable example app at `examples/spring-boot-starter-basic` with starter-driven `PojoLensRuntime` injection and a basic SQL-like REST use case.
  - Added starter integration smoke test `PojoLensStarterSmokeIntegrationTest` (context + endpoint).
  - Updated release policy to publish starter/autoconfigure artifacts to Central alongside runtime artifact.
  - Added auto-configuration tests covering defaults, property overrides, bean backoff, and micrometer enable/disable behavior.
  - Updated README/modules documentation with starter usage and property examples.
- SQL-like maintainability hardening delivered (slices 1-3):
  - `SqlLikeQuery` prepared-execution/binding cache internals were split into new helper class `SqlLikePreparedExecutionSupport`.
  - `SqlLikeQuery` execution flow, raw-row execution, stage row counts, and chart telemetry paths were split into new helper class `SqlLikeExecutionFlowSupport`.
  - `SqlLikeParser` tokenization internals were split into new helper class `SqlLikeTokenizationSupport` (tokens, token types, position map, tokenizer).
  - size reductions now at `SqlLikeQuery`: `1254 -> 748`, `SqlLikeParser`: `1292 -> 1070`, with public API behavior preserved by regression suites.

## Next Actions

- Retry release workflow for `v1.0.0` (or manual dispatch) and confirm Central publish status for runtime + Boot starter artifacts.
- Continue SQL window analytics roadmap with spike 2 (`QUALIFY` clause support with explain-stage metadata).
- Keep lint baseline stable by reducing inherited violations incrementally and refreshing baseline only when intentional.
