# System Boundaries

Owned capabilities:

- in-memory SQL-like, natural, and typed querying over Java object lists
- filtering, ordering, grouping, HAVING, metrics, time buckets, joins, projection, explain, and schema metadata
- chart payload mapping, reusable reports, dataset bundles, snapshot comparison, telemetry, metamodel generation, and benchmark tooling
- advisory SQL-like pushdown-readiness metadata, host-adapter bridge contracts,
  and JDBC `ResultSet` materialization into in-memory rows

Explicit non-ownership:

- no database execution, ORM layer, SQL rendering, adapter authorization, or persistence runtime
- no repository-owned SQL rendering, database execution, adapter authorization,
  or ORM/query-framework integration
- no native chart renderer in the library runtime
- no web framework, scheduler, auth stack, or deployment assets

Dependency boundaries:

- runtime: `slf4j-api`, `caffeine`
- optional: `xchart`, `jmh-core`
- tests: `junit-jupiter`, `slf4j-simple`

Behavioral constraints:

- SQL-like subqueries are limited to `WHERE field IN (select oneField ...)`
- grouped, aggregate, and joined subquery plans are not supported
- time bucket fields may be `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, or `ZonedDateTime`
- internal builders are mutable; public typed and report definitions remain immutable wrappers
