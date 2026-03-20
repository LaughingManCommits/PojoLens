# Repo Purpose

- `pojo-lens` is a single-module Maven Java library with `groupId` `io.github.laughingmancommits`, `artifactId` `pojo-lens`, `version` `1.0.0`, `packaging` `jar`, and Java release `17`.
- The library provides in-memory querying over POJOs through two public entry styles: fluent builders (`PojoLens.newQueryBuilder(...)` / `PojoLensCore`) and SQL-like queries (`PojoLens.parse(...)` / `PojoLensSql`).
- Core behavior includes filtering, ordering, grouping, HAVING, metrics, time buckets, joins, typed projection, and explain or schema support.
- Adjacent library features include chart payload mapping, chart or query presets, reusable reports, dataset bundles, snapshot comparison, telemetry hooks, tabular schema metadata, field metamodel generation, regression fixtures, and benchmark tooling.
- The repository does not own a web service, CLI application, deployment manifests, or database pushdown integration.
- No publication target is declared in `pom.xml`.
