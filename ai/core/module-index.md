# Module Index

Top-level Java package roles under `src/main/java/laughing/man/commits`:

- facades: `PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`
- fluent pipeline: `builder/*` builds queries and `filter/*` executes them through both the legacy `QueryRow` engine and a selective single-join array fast path
- SQL-like pipeline: `sqllike/parser`, `sqllike/internal/validation`, `sqllike/internal/binding`, `sqllike/internal/execution`
- reporting and visualization: `chart/*`, `report/*`, `table/*`, `time/*`
- supporting features: `computed/*`, `snapshot/*`, `testing/*`, `telemetry/*`, `metamodel/*`
- performance tooling: `benchmark/*`, `benchmarks/*`, `scripts/benchmark-suite-*.args`

High-level flow:

- fluent builders and SQL-like binding both feed the filter engine
- the selective computed single-join hot path can bypass `QueryRow` materialization and stay on `Object[]` rows until projection
- chart and report layers consume query results rather than execute separate engines
- benchmark tooling exercises the same runtime stack as the public APIs
