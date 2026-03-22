# Handoff

## Resume Order

1. Load hot context files.
2. Confirm workspace status with `git status --short`.
3. Check backlog status in `TODO.md`.
4. Use `ai/state/benchmark-state.md` only for benchmark-specific tasks.

## Current Focus

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
- Maven Central release completion remains pending operational work.

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`.
- For packaging-boundary edits: verify runtime jar no longer ships benchmark classes (for example, `jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` should be empty).
- For stable API contract edits: include `StablePublicApiContractTest` in focused suites.
- For binary-compat edits: validate against a baseline tag with `mvn -q -Pbinary-compat -DskipTests -Dcompat.baseline.version=<X.Y.Z> verify`.
- Lint note: `scripts/check-lint-baseline.ps1` currently reports large baseline drift and is not a clean gate without baseline maintenance.

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
