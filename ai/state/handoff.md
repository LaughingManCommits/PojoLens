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
- Next high-value feature is Spike 3 (optional in-memory indexes for hot filter/join paths).
- Maven Central release completion remains pending operational work.

## Next Validation

- After any code change: run focused tests, then `mvn -q test`.
- For docs/process edits: run `scripts/check-doc-consistency.ps1`.
- For release-path changes: run `mvn -B -ntp -Prelease-central -DskipTests package`.
- Lint note: `scripts/check-lint-baseline.ps1` currently reports large baseline drift and is not a clean gate without baseline maintenance.

## Release Retry Checklist

- Confirm GitHub secrets exist: `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
- Ensure release tag matches `pom.xml` version (`vX.Y.Z` vs `project.version`).
- Trigger `.github/workflows/release.yml` via tag push or `workflow_dispatch`.
- If signature lookup still fails, wait and retry after keyserver propagation.

## Streaming Follow-Up Checklist

- Benchmark command (forked warmed run):
  `java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-streaming.args -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/streaming-execution-forked.json`
- Results file for latest notes:
  `target/benchmarks/streaming-execution-forked.json`
- Current conclusion:
  streaming provides strong wins for short-circuit/first-page consumers (`stream().limit(50)`) by avoiding full list materialization.
