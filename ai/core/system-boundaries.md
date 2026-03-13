# System Boundaries

Owned capabilities:

- in-memory fluent and SQL-like querying over Java object lists
- filtering, ordering, grouping, HAVING, metrics, time buckets, joins, projection, explain, and schema metadata
- chart payload mapping, reusable reports, dataset bundles, snapshot comparison, telemetry, metamodel generation, and benchmark tooling

Explicit non-ownership:

- no database integration, query pushdown, ORM layer, or persistence runtime
- no native chart renderer in the library runtime
- no web framework, scheduler, auth stack, or deployment assets

Dependency boundaries:

- runtime: `slf4j-api`, `caffeine`
- optional: `xchart`, `jmh-core`
- tests: `junit-jupiter`, `slf4j-simple`

Behavioral constraints:

- SQL-like subqueries are limited to `WHERE field IN (select oneField ...)`
- grouped, aggregate, and joined subquery plans are not supported
- time bucket fields must be `java.util.Date`
- builders are mutable; reusable snapshots depend on `copyOnBuild(true)`
