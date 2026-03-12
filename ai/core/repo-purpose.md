# Repo Purpose

- Verified: `pojo-lens` is a single-module Maven Java library (`packaging=jar`) with `groupId` `laughing.man.commits`, `artifactId` `pojo-lens`, and Java release `17`.
- Verified: The library exposes two primary in-memory query styles over POJOs:
  - fluent builder entry via `PojoLens.newQueryBuilder(...)` / `PojoLensCore`
  - SQL-like text entry via `PojoLens.parse(...)` / `PojoLensSql`
- Verified: Query execution can project rows into Java types, apply filtering, ordering, grouping, HAVING, metrics, time buckets, and joins.
- Verified: The repo also owns chart payload mapping, chart/query presets, reusable report definitions, dataset bundles, snapshot comparison, telemetry hooks, tabular schema metadata, field metamodel generation, regression fixtures, and benchmark tooling.
- Inferred: The intended use case is application-side analytics/reporting over already loaded or cached data, not database query pushdown.
- Verified: The repository does not define an HTTP service, CLI app, Docker image, Kubernetes manifests, Terraform, or runtime deployment configuration.
- Unknown: No artifact publication target or release distribution destination is declared in `pom.xml`.
