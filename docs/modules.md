# Module Boundaries

PojoLens now uses a parent + module split:
- `pojo-lens-parent` (build parent, packaging `pom`)
- `pojo-lens` (runtime library artifact for consumers)
- `pojo-lens-benchmarks` (benchmark/JMH tooling module)

Runtime boundaries remain explicitly separated by entry point:

- `PojoLensCore`: fluent query builder + filter execution + stats-plan cache controls
- `PojoLensSql`: SQL-like parser + SQL-like cache controls
- `PojoLensChart`: chart payload mapping helpers
- `PojoLens`: compatibility facade that delegates to the three entry points above

Compatibility tiers for these entry points and related contracts are defined in
[public-api-stability.md](public-api-stability.md).

## Artifact Scope

Consumer dependency remains unchanged:

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>1.0.0</version>
</dependency>
```

Benchmark/JMH classes are packaged in `pojo-lens-benchmarks` and excluded from
the runtime `pojo-lens` jar. Benchmark-only dependencies such as `jmh-core` and
`xchart` are isolated to the benchmark module.

To build the forked benchmark runner jar:

```bash
mvn -B -ntp -Pbenchmark-runner -DskipTests package
```

This writes `target/pojo-lens-<version>-benchmarks.jar` at repository root.

## Migration Guidance

Existing code using `PojoLens` remains valid. New code can opt into explicit
entry points incrementally:

1. Replace `PojoLens.newQueryBuilder(...)` with `PojoLensCore.newQueryBuilder(...)`.
2. Replace `PojoLens.parse(...)` with `PojoLensSql.parse(...)` in SQL-like paths.
3. Replace `PojoLens.toChartData(...)` with `PojoLensChart.toChartData(...)` where desired.

