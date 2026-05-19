# Repo Purpose

- `pojo-lens` is a multi-module Maven Java library build with `groupId`
  `io.github.laughingmancommits`, root-POM date-based versioning, and Java
  release `25`.
- Runtime consumer artifact remains
  `io.github.laughingmancommits:pojo-lens` (`jar`).
- Optional Spring Boot integration is provided via modules/artifacts
  `io.github.laughingmancommits:pojo-lens-spring-boot-autoconfigure`
  and `io.github.laughingmancommits:pojo-lens-spring-boot-starter`.
- Benchmark/JMH tooling is isolated in module/artifact
  `io.github.laughingmancommits:pojo-lens-benchmarks` (module-local tooling;
  deploy skipped).
- Release versioning is date-based: Maven versions use `YYYY.MM.DD.HHmm` and
  Git tags use `release-<version>`.
- The library provides in-memory querying over POJOs through typed Java,
  controlled plain-English, and SQL-like entry points (`TypedQuery`,
  `PojoLensNatural`, and `PojoLensSql`) plus scoped `PojoLensRuntime`; the old
  `PojoLens` facade and old public fluent facade are removed from the public surface.
- Core behavior includes filtering, ordering, grouping, HAVING, metrics, time buckets, joins, typed projection, bounded subqueries/existence predicates, windows, and explain or schema support.
- Adjacent library features include CSV boundary loading, tree row shaping, chart payload mapping, chart or query presets, reusable reports, dataset bundles, snapshot comparison, telemetry hooks, tabular schema metadata, field metamodel generation, regression fixtures, and benchmark tooling.
- The repository does not own a web service, CLI application, deployment manifests, or database pushdown integration.
- The repository no longer contains the extracted local AI runtime; that tooling now lives in the separate `neon` codebase.
- `pojo-lens/pom.xml` includes a `release-central` profile for Maven Central publishing (sources, javadocs, gpg signing, and central publishing plugin).
