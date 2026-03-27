# Repo Purpose

- `pojo-lens` is a multi-module Maven Java library build with `groupId` `io.github.laughingmancommits`, version `1.0.0`, and Java release `17`.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:1.0.0` (`jar`).
- Optional Spring Boot integration is provided via modules/artifacts `io.github.laughingmancommits:pojo-lens-spring-boot-autoconfigure:1.0.0` and `io.github.laughingmancommits:pojo-lens-spring-boot-starter:1.0.0`.
- Benchmark/JMH tooling is isolated in module/artifact `io.github.laughingmancommits:pojo-lens-benchmarks:1.0.0` (module-local tooling; deploy skipped).
- The library provides in-memory querying over POJOs through explicit fluent and SQL-like entry points (`PojoLensCore` and `PojoLensSql`), plus a helper-only `PojoLens` facade and scoped `PojoLensRuntime`.
- Core behavior includes filtering, ordering, grouping, HAVING, metrics, time buckets, joins, typed projection, and explain or schema support.
- Adjacent library features include chart payload mapping, chart or query presets, reusable reports, dataset bundles, snapshot comparison, telemetry hooks, tabular schema metadata, field metamodel generation, regression fixtures, and benchmark tooling.
- The repository does not own a web service, CLI application, deployment manifests, or database pushdown integration.
- `pojo-lens/pom.xml` includes a `release-central` profile for Maven Central publishing (sources, javadocs, gpg signing, and central publishing plugin).
