# Test Strategy

Primary validation:

- `mvn -B -ntp test`
- CI test matrix on Java `17` and `21`

Coverage emphasis:

- public API and compatibility contracts
- README and docs examples
- SQL-like parsing, validation, parameters, lint, joins, aliases, and typed execution
- fluent filtering, grouping, metrics, joins, nested paths, time buckets, and selective materialization
- cache behavior, runtime presets, telemetry, reports, chart mapping, snapshots, regression fixtures, and benchmark utilities

Artifacts and fixtures:

- runtime test fixtures live under `pojo-lens/src/test/resources/fixtures`
- benchmark test fixtures live under `pojo-lens-benchmarks/src/test/resources/fixtures`
- `ChartLibraryInteropTest` generates chart PNG artifacts under `target/generated-charts`

Additional validation paths:

- `mvn -B -ntp -Plint verify -DskipTests`
- `mvn -B -ntp -Pstatic-analysis verify -DskipTests`
- benchmark suites built from the `benchmark-runner` profile
