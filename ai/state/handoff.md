# Handoff

## Resume Order

1. Load hot context files.
2. Confirm workspace status with `git status --short`.
3. Check backlog status in `TODO.md`.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific tasks.

## Current Focus

- SQL window analytics spike 1 (Window Functions MVP) is now completed:
  - parser/AST now supports `ROW_NUMBER()`, `RANK()`, and `DENSE_RANK()` with `OVER (PARTITION BY ... ORDER BY ...)`.
  - window computation now executes after `WHERE` and before query-level `ORDER BY`/pagination/projection.
  - query-level `ORDER BY` can reference window aliases for window-enabled query shapes.
  - determinism guardrails were added:
    missing window `ORDER BY` fails validation, and non-unique window sort ties are resolved with stable source-row order.
  - docs + tests updated (`SqlLikeWindowFunctionTest`, parser/docs examples, `docs/sql-like.md`, `TODO.md` spike item checked).
- SQL window analytics spike 2 (`QUALIFY`) is now completed:
  - parser/AST now supports `QUALIFY` predicates and enforces clause order around window execution.
  - validator now restricts `QUALIFY` to non-aggregate query shapes with at least one window select output and rejects unknown/subquery references.
  - `QUALIFY` supports window aliases and direct matching window expressions.
  - execution now applies `QUALIFY` after window computation and before query-level `ORDER BY`/pagination.
  - explain now includes `qualifyRuleCount` and `stageRowCounts.qualify`.
  - docs + tests updated (`SqlLikeWindowFunctionTest`, `SqlLikeParserTest`, `ExplainToolingTest`, `SqlLikeDocsExamplesTest`, `docs/sql-like.md`, `TODO.md` spike item checked).
- SQL window analytics spike 3 (aggregate windows) is now completed:
  - parser/AST now supports `SUM/AVG/MIN/MAX/COUNT(...) OVER (...)` including aggregate-window argument metadata (`COUNT(*)` vs value field).
  - parser guardrails now enforce aggregate frame syntax:
    `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`; unsupported frame expressions fail fast.
  - fluent runtime now computes running aggregate windows (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`) with null/type behavior aligned to existing aggregate semantics.
  - SQL-like binder/validation/join-canonicalization now compile aggregate windows to fluent `addWindow(...)` value-argument APIs.
  - SQL-like alias projection now applies typed value casting before aliased select field assignment to preserve numeric compatibility in projections.
  - docs/tests updated and `TODO.md` spike-3 checkboxes are marked complete.
- SQL window analytics spike 4 (API/docs hardening) is now completed:
  - docs now include aggregate-window syntax/limitations plus recipes for top-N per group, dense rank, and running total.
  - public API/docs regression coverage now includes aggregate-window parse/filter/explain usage.
  - benchmark coverage now includes windowed vs non-windowed SQL-like comparisons with notes in `docs/benchmarking.md`.
  - `TODO.md` spike-4 checkboxes are now marked complete.
  - focused regression rerun: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,PublicApiCoverageTest" test`.
- Predefined stats views spike 5 (easy usage presets) is now completed:
  - added `StatsViewPresets` standard table view APIs: `summary()`, `by(field)`, `topNBy(field, metric, n)`.
  - added `StatsViewPreset<T>` execution wrapper (list/map/join-bundle overloads) and `StatsTable<T>` payload (`rows`, optional `totals`, `schema`).
  - preset execution now compiles to existing SQL-like query runtime (no separate execution engine).
  - docs/examples added in `README.md`, `docs/stats-presets.md`, `docs/usecases.md`, and `docs/reports.md`.
  - coverage added/updated: `StatsViewPresetsTest`, `StatsDocsExamplesTest`, `PublicApiCoverageTest`.
  - validations passed:
    `mvn -q -pl pojo-lens -am "-Dtest=StatsViewPresetsTest,StatsDocsExamplesTest,PublicApiCoverageTest" test`
    `mvn -q test`
    `scripts/check-doc-consistency.ps1`
- Fluent parity for window analytics is now implemented:
  - `QueryBuilder` now exposes rank-window API (`addWindow(alias, function, partitionFields, orderFields)`) and fluent `QUALIFY` APIs (`addQualify(...)`, `addQualifyAllOf(...)`, `addQualifyAnyOf(...)`).
  - fluent execution now applies window stage then qualify stage in non-aggregate flows, matching SQL-like behavior.
  - fluent guardrails now reject aggregate window shapes and invalid qualify references.
  - fast execution paths now opt out when fluent windows/qualify are configured.
  - parity/contract coverage added (`FluentWindowFunctionTest`, `SqlLikeMappingParityTest`, `PublicApiCoverageTest`, `StablePublicApiContractTest` updates).
- SQL-like now compiles window/qualify through fluent runtime path:
  - `SqlLikeBinder` now translates SQL window definitions into fluent `addWindow(...)` entries.
  - `SqlLikeBinder` now translates `QUALIFY` predicates/boolean expressions into fluent qualify rules/groups (`addQualify(...)`, `addQualifyAllOf(...)`).
  - SQL-like raw-row execution now delegates to fluent filter execution (`FilterImpl`) instead of a separate SQL-like window/qualify branch.
  - SQL-like explain stage-row-count path now also applies qualify/window through fluent execution (staged fluent builder), so stage counts no longer rely on SQL-like-specific window/qualify runtime helpers.
  - `ReflectionUtil.toClassList(QueryRow.class, ...)` now returns row passthrough to support internal fluent raw-row delegation.
  - removed dead SQL-like helper classes `SqlLikeWindowSupport` and `SqlLikeQualifySupport`.
- Spike 1 (pagination) is completed:
  - `OFFSET` is implemented in fluent + SQL-like flows.
  - SQL-like named parameters are supported for `LIMIT/OFFSET` with integer/non-negative validation.
  - first-class SQL-like keyset cursor primitives are implemented (`SqlLikeCursor`, `keysetAfter`, `keysetBefore`) with token encode/decode support.
  - keyset cursor contract docs now include field matching rules and non-null value requirements.
  - large/tie-heavy keyset behavior is validated in dedicated tests.
- Spike 2 (streaming execution output) is completed:
  - fluent `Filter` now exposes `iterator(...)` and `stream(...)`.
  - SQL-like query/bind flows now expose `stream/iterator`.
  - simple query shapes stream lazily without full result materialization.
  - complex query shapes intentionally fall back to list-backed streams for deterministic behavior.
  - benchmark notes were added via `StreamingExecutionJmhBenchmark` and `docs/benchmarking.md`.
- Spike 3 (optional in-memory indexes) is completed:
  - fluent index API added: `QueryBuilder.addIndex(String)` and typed selector overload.
  - indexed candidate narrowing implemented for compatible simple POJO equality filters.
  - safe fallback to scan preserved for inapplicable shapes/fields.
  - benchmark parity and behavior tests added.
  - benchmark notes recorded for warm/cold tradeoffs.
- Stable API surface spike 4 baseline is now implemented:
  - `docs/public-api-stability.md` defines stable/advanced/internal tiers for `1.x`.
  - `README.md` and `docs/modules.md` link and summarize the stability contract.
  - `StablePublicApiContractTest` enforces stable entry-point availability and baseline fluent/SQL-like/runtime behavior.
  - `TODO.md` marks stable-API hardening item complete.
- Binary compatibility spike 5 baseline is now implemented:
  - `pom.xml` includes `binary-compat` profile with `japicmp` fail-on binary/source incompatible changes, activated by `compat.baseline.version`.
  - `.github/workflows/ci.yml` now has a `binary-compat` job that resolves latest `v*` tag baseline, installs baseline artifact from detached worktree, and runs compatibility verify.
  - `CONTRIBUTING.md` includes local binary-compat run instructions.
  - `TODO.md` marks binary-compat hardening item complete.
- Artifact/module boundary spike 6 is now implemented:
  - root `pom.xml` is now parent build `pojo-lens-parent` with `pojo-lens` + `pojo-lens-benchmarks` modules.
  - runtime module (`pojo-lens`) now owns runtime source/tests/resources under `pojo-lens/src/...` (module-local source layout).
  - benchmark module (`pojo-lens-benchmarks`) now owns benchmark source/tests/resources under `pojo-lens-benchmarks/src/...`, plus benchmark/JMH runner shading.
  - runtime jar scope is clean (`jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` returns empty).
  - CI now has `runtime-artifact-scope` job that fails if benchmark classes leak into runtime jar.
  - `TODO.md` artifact-slimming item is marked complete.
- Spring Boot support baseline is now implemented:
  - added modules `pojo-lens-spring-boot-autoconfigure` and `pojo-lens-spring-boot-starter`.
  - root parent imports Spring Boot BOM version `4.0.4`.
  - autoconfigure module now provides `PojoLensRuntime` bean wiring via `pojo-lens.*` properties (`preset`, strict/lint flags, cache overrides).
  - optional micrometer bridge auto-registers a `QueryTelemetryListener` when `MeterRegistry` is present and `pojo-lens.telemetry.micrometer.enabled=true` (default).
  - starter module now exposes a single Boot dependency entry-point for PojoLens and includes `micrometer-core` for stable Boot 4 auto-config introspection.
  - standalone runnable starter demo app now exists at `examples/spring-boot-starter-basic` with `/api/employees/top-paid` and `/api/employees/runtime` endpoints.
  - starter module now has an integration smoke test (`PojoLensStarterSmokeIntegrationTest`) that boots a web context and exercises a real endpoint using injected `PojoLensRuntime`.
  - release distribution decision is now to publish three Central artifacts: `pojo-lens`, `pojo-lens-spring-boot-autoconfigure`, and `pojo-lens-spring-boot-starter` (benchmarks/examples remain non-published).
  - behavior is covered by `PojoLensSpringBootAutoConfigurationTest` (defaults, overrides, backoff, micrometer toggle).
- SQL-like maintainability refactor slice 1 is implemented:
  - extracted prepared-execution internals (`prepareExecution`, shape key cache modeling, prepared execution/run context plumbing) from `SqlLikeQuery` into new package-private helper `SqlLikePreparedExecutionSupport`.
  - `SqlLikeQuery` is now smaller (`1002` lines, down from `1254`) with unchanged public API surface.
  - validated with focused SQL-like suites + full `mvn -q test` + docs consistency.
- SQL-like maintainability refactor slices 2/3 are now implemented:
  - extracted execution-flow internals from `SqlLikeQuery` into new package-private helper `SqlLikeExecutionFlowSupport` (`executeFilter`, `executeStream`, `executeChart`, raw-row pipeline, stage-row-count explain path, telemetry stage emitters).
  - extracted parser tokenization internals from `SqlLikeParser` into new package-private helper `SqlLikeTokenizationSupport` (tokenizer, token/position model).
  - class size reductions now at: `SqlLikeQuery` `1254 -> 748`, `SqlLikeParser` `1292 -> 1070`.
  - validated with focused SQL-like suites, full `mvn -q test`, and docs consistency.
- Lint baseline refresh is completed:
  - ran lint profile: `mvn -B -ntp -Plint verify -DskipTests`
  - refreshed baseline: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - post-refresh gate check passes with `11896` report/baseline entries and `new=0`, `fixed=0`.
- Maven Central release completion remains pending operational work.
- Next roadmap item should be selected after spike-5 completion (release retry remains operationally pending).

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- Window-function focused suite:
  `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`.
- `QUALIFY`/explain-focused suite (recommended during window-spike follow-up):
  `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeWindowFunctionTest,SqlLikeParserTest,ExplainToolingTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`.
- Fluent window/qualify parity suite:
  `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeMappingParityTest,ExplainToolingTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest,StablePublicApiContractTest,PublicSurfaceContractTest" test`.
- Aggregate-window focused suite (recommended during follow-up):
  `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeMappingParityTest,PublicApiCoverageTest,StablePublicApiContractTest" test`.
- Stats-preset focused suite:
  `mvn -q -pl pojo-lens -am "-Dtest=StatsViewPresetsTest,StatsDocsExamplesTest,PublicApiCoverageTest" test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`.
- For packaging-boundary edits: verify runtime jar no longer ships benchmark classes (for example, `jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` should be empty).
- For stable API contract edits: include `StablePublicApiContractTest` in focused suites.
- For binary-compat edits: validate against a baseline tag with `mvn -q -Pbinary-compat -DskipTests -Dcompat.baseline.version=<X.Y.Z> verify`.
- Lint note: baseline currently matches lint report (`11896` entries, `new=0`, `fixed=0`); refresh intentionally when repo-wide checkstyle set changes.

## Release Retry Checklist

- Confirm GitHub secrets exist: `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
- Ensure release tag matches `pom.xml` version (`vX.Y.Z` vs `project.version`).
- Release workflow now resolves root version and publishes module set `pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter` via reactor `deploy` with `-Prelease-central`.
- Trigger `.github/workflows/release.yml` via tag push or `workflow_dispatch`.
- If signature lookup still fails, wait and retry after keyserver propagation.

## Index Benchmark Notes

- Warm run command:
  `java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-forked.json`
- Cold run command:
  `java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 0 -i 1 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-cold.json`
- Current conclusion:
  index hints improved warmed latency for this selective equality workload, but increased allocation and can regress cold one-shot runs.
