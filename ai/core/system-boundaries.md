# System Boundaries

## Owned Capabilities

- Verified: Fluent query composition through `QueryBuilder` and execution through `Filter`.
- Verified: SQL-like parsing, validation, bind-first execution, parameter binding, linting, explain payloads, and cache controls.
- Verified: Deterministic in-memory joins, including chained SQL-like joins when each join references the current plan correctly.
- Verified: Aggregate metrics, grouped queries, HAVING rules, and calendar-aware time buckets.
- Verified: Chart payload mapping only; output model is `ChartData` plus datasets, not rendered images.
- Verified: Reusable report/query presets, dataset bundles, snapshot diff rows, regression fixtures, and benchmark utilities.

## Explicit Non-Ownership

- Verified: No persistence layer, SQL database integration, ORM mapping, or query pushdown.
- Verified: No native chart renderer in the library runtime. XChart appears only in benchmark plotting utilities and tests.
- Verified: No web framework, API server, auth stack, scheduler, or deployment manifests are present in the repo.
- Verified: No annotation processor or build plugin owns field metamodel generation; generation is library-level and opt-in.

## External Dependencies

- Verified: Runtime dependencies are `slf4j-api` and `caffeine`.
- Verified: Optional dependencies are `xchart` and `jmh-core`.
- Verified: Test dependencies include `junit-jupiter` and `slf4j-simple`.

## Behavioral Boundaries

- Verified: SQL-like subqueries are limited to `WHERE <field> IN (select <field> ...)`.
- Verified: SQL-like subqueries do not support grouped, aggregate, or joined subquery plans.
- Verified: Time bucket inputs must be `java.util.Date` fields.
- Verified: Builders are mutable; thread-safe reuse depends on `copyOnBuild(true)` snapshots.
- Unknown: Long-term semantic-versioning guarantees are not declared beyond current release/migration notes.
