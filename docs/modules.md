# Module Boundaries

PojoLens uses a parent + module split:

- `pojo-lens-parent`:
  build parent, packaging `pom`
- `pojo-lens`:
  runtime library artifact for consumers
- `pojo-lens-spring-boot-autoconfigure`:
  Spring Boot auto-configuration module
- `pojo-lens-spring-boot-starter`:
  starter dependency for Boot apps
- `pojo-lens-benchmarks`:
  benchmark/JMH tooling module

Standalone runnable examples live under `examples/` and are intentionally not
published as release artifacts.

Canonical product-surface classification:
- [product-surface.md](product-surface.md)

Docs starting points:
- choose a path first in [usecases.md](usecases.md)
- choose explicit entry points in [entry-points.md](entry-points.md)
- choose reusable wrappers in [reusable-wrappers.md](reusable-wrappers.md)
- use [advanced-features.md](advanced-features.md) only for optional follow-on
  surface

This page is an artifact and packaging reference, not the main onboarding path.

## Distribution Decision

- Published to Central:
  `pojo-lens`, `pojo-lens-spring-boot-autoconfigure`,
  `pojo-lens-spring-boot-starter`
- Not published:
  `pojo-lens-benchmarks`, `examples/*`

## Public Runtime Layering

- `PojoLensCore`:
  core fluent query-engine entry point
- `PojoLensSql`:
  core SQL-like query-engine entry point
- `PojoLensRuntime`:
  scoped runtime/configuration surface over the same engine
- `PojoLensChart`:
  chart-mapping workflow helper over query results

Additional workflow helpers such as `ReportDefinition`, chart presets,
stats presets, `DatasetBundle`, and schema metadata stay in the runtime
artifact as convenience layers on top of the core engine; they are not
separate modules.

Compatibility tiers for these entry points and related contracts are defined in
[public-api-stability.md](public-api-stability.md).

## Artifact Scope

Consumer dependency remains:

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

The public surface no longer includes the `PojoLens` facade.
Use the owning type directly:

1. `PojoLens.newQueryBuilder(...)` -> `PojoLensCore.newQueryBuilder(...)`
2. `PojoLens.parse(...)` -> `PojoLensSql.parse(...)`
3. `PojoLens.template(...)` -> `PojoLensSql.template(...)`
4. `PojoLens.toChartData(...)` -> `PojoLensChart.toChartData(...)`
5. `PojoLens.newRuntime(...)` -> `new PojoLensRuntime()` or
   `PojoLensRuntime.ofPreset(...)`
6. `PojoLens.report(...)` -> `ReportDefinition.sql(...)` or
   `ReportDefinition.fluent(...)`
7. `PojoLens.bundle(...)` -> `DatasetBundle.of(...)`
8. `PojoLens.compareSnapshots(...)` -> `SnapshotComparison.builder(...)`

Use `JoinBindings` for named secondary sources and `DatasetBundle` when the
same multi-source snapshot will be reused.
