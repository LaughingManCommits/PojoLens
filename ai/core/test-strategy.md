# Test Strategy

- Verified: `mvn -B -ntp test` passed on 2026-03-12 with `398` tests and `0` failures/errors.
- Verified: CI runs the main test job on Java `17` and `21`.

## Main Coverage Areas

- Verified: Public API and backward-compatibility contracts.
- Verified: README and doc example execution.
- Verified: SQL-like parser, validation, error codes, named parameters, lint mode, and typed bind-first execution.
- Verified: Fluent vs SQL-like parity, joins, nested paths, HAVING, time buckets, computed fields, and selective materialization.
- Verified: Cache policy/configuration, cache concurrency, runtime presets, and telemetry stages.
- Verified: Chart payload validation/mapping plus XChart interoperability artifact generation.
- Verified: Report definitions, dataset bundles, snapshot comparison, and regression fixtures.
- Verified: Benchmark utility parity, metric loading, and plot generation helpers.

## Fixtures And Artifacts

- Verified: Chart fixtures live under `src/test/resources/fixtures/chart`.
- Verified: Benchmark fixtures live under `src/test/resources/fixtures/benchmark`.
- Verified: `ChartLibraryInteropTest` writes generated PNGs to `target/generated-charts` and mirrors them into benchmark image artifacts.

## Not Run During Bootstrap

- Unknown locally: `mvn -B -ntp -Plint verify -DskipTests`
- Unknown locally: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`
- Unknown locally: benchmark runner and threshold suites

- Verified separately: `scripts/check-doc-consistency.ps1` passed on 2026-03-12 when run with Windows PowerShell.
