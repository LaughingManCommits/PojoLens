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
- Next high-value hardening work is binary compatibility CI checks + artifact/module slimming.
- Scope audit (`2026-03-21`) found broad public surface and packaging bleed:
  - entry contracts are large (`PojoLens` static API ~52 methods, `QueryBuilder` ~40 methods, `SqlLikeQuery` ~50 public members)
  - main runtime jar currently includes benchmark/JMH classes (`198` benchmark entries, `134` under `benchmark/jmh_generated`)
  - fold artifact-slimming decisions into platform hardening work.
- `TODO.md` now explicitly tracks artifact/module-boundary slimming as a dedicated spike (parent + runtime + benchmarks split path).
- Maven Central release completion remains pending operational work.

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -Prelease-central -DskipTests package`.
- For packaging-boundary edits: verify runtime jar no longer ships benchmark classes (for example, `jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` should be empty).
- For stable API contract edits: include `StablePublicApiContractTest` in focused suites.
- Lint note: `scripts/check-lint-baseline.ps1` currently reports large baseline drift and is not a clean gate without baseline maintenance.

## Release Retry Checklist

- Confirm GitHub secrets exist: `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
- Ensure release tag matches `pom.xml` version (`vX.Y.Z` vs `project.version`).
- Trigger `.github/workflows/release.yml` via tag push or `workflow_dispatch`.
- If signature lookup still fails, wait and retry after keyserver propagation.

## Index Benchmark Notes

- Warm run command:
  `java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-forked.json`
- Cold run command:
  `java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 0 -i 1 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-cold.json`
- Current conclusion:
  index hints improved warmed latency for this selective equality workload, but increased allocation and can regress cold one-shot runs.
