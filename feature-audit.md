# Feature Audit

Date: 2026-05-18

Scope: repository state after the `neon` extraction cleanup, release-tag repair,
and typed-query WP-1 through WP-6 work. Reviewed `README.md`, `TODO.md`,
`docs/product-surface.md`, `docs/entry-points.md`, the SQL-like, natural, and
typed guides, public Java entry points, and the related contract tests.

## Executive Summary

PojoLens is not missing a foundational product layer. The current surface covers
in-memory POJO querying, SQL-like text, controlled natural text, typed Java DSL,
joins, grouping, windows, bounded subqueries, reusable reports, charts, trees,
facets, file loading, runtime policy, Spring/JDBC integration, diagnostics,
pushdown metadata, regression fixtures, build tooling, and benchmarks.

The highest-value gaps are concentrated in typed-surface parity, string matching
ergonomics, time-bucket granularity, and a few documentation/source-truth drifts.
There is one immediate docs correctness issue: `README.md` says JDK `17+`, while
the root build compiles with `maven.compiler.release=25`.

No P0 feature blocker was found.

## Feature Inventory

| Area | Current status | Notes |
|---|---|---|
| Core row querying | Broad and active | SQL-like and natural routes execute against `List<T>`/`DatasetBundle`; typed route lowers into the same engine. |
| SQL-like text | Strongest public surface | Includes grouping, joins, windows, bounded subqueries, templates, params, diagnostics, explain/schema, streaming, pagination, computed fields, exposure policy, and pushdown metadata. |
| Natural text | Good guided-text surface | Lowers to SQL-like, includes vocabulary/runtime support, computed fields, diagnostics, streaming, charts, joins, subqueries, windows, and time buckets. |
| Typed DSL | Strong but still catching up | Recent WPs added NOT lowering, contains/matches, typed sort descriptors, time buckets, between, and execution helpers. Remaining parity gaps are already mostly represented in `TODO.md`. |
| Reusable contracts | SQL-like/natural oriented | `ReportDefinition` and `SavedReport` support SQL-like and natural query contracts, chart specs, schema, review, and replay. |
| Output helpers | Healthy | Charts, Chart.js payloads, tabular schema, facets, stats presets, tree shaping, and snapshot comparison are documented. |
| Data onboarding | Bounded and useful | `PojoLensFiles` covers CSV, TSV, JSON, and JSONL from `Path`; `PojoLensCsv` remains CSV-only convenience. |
| Runtime and integration | Well-scoped | `PojoLensRuntime` owns policy/caches/vocabulary/defaults; optional Spring Boot/JDBC bridge stays out of core query ownership. |
| Tooling and validation | Mature | Generated typed fields, saved-report validation, regression fixtures, benchmark scripts, docs consistency checks, and public API stability tests exist. |

## Findings

Priority guide: P1 should be fixed before the next polish/release pass, P2 is
important product parity, P3 is useful but not urgent, and P4 is optional or
depends on product direction.

| Priority | Area | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| P1 | Docs correctness | README requirements are wrong for the current build. | `README.md` says JDK `17+`; root `pom.xml` uses `<maven.compiler.release>25</maven.compiler.release>`. | Update README and any install/getting-started docs to Java 25, or lower the build target if Java 17 compatibility is actually intended. |
| P1 | Typed execution parity | `TypedQuery` lacks the operational APIs users get on SQL-like/natural: `stream()`, `iterator()`, `filterPage()`, and `computedFields(...)`. | `SqlLikeQuery` has `computedFields`, `stream`, and `filterPage`; `NaturalQuery` has `computedFields` and `stream`; `TypedQuery` currently has `filter`, `count`, `exists`, `findFirst`, and `findOne`. | Finish TODO WP-8, WP-10, and WP-11. Also decide whether natural should get `filterPage(...)` so pagination is not SQL-like-only. |
| P1 | String matching | Case-insensitive query matching is absent. `CONTAINS` is explicitly case-sensitive and there is no cross-surface ignore-case operator. | `TypedQueryContractTest.containsIsCaseSensitive`; `Clauses` only has `CONTAINS` and `MATCHES`; TODO WP-7 tracks `containsIgnoreCase`. | Add `containsIgnoreCase` at least for typed. Consider whether SQL-like/natural should get an equivalent operator or a documented `MATCHES` recipe. |
| P2 | Time-series querying | Time buckets stop at day/week/month/quarter/year. Hour-level buckets are explicitly rejected even though date parsing already handles hour/minute formats elsewhere. | `TimeBucket` enum has `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`; parser tests reject `bucket(hireDate,'hour')`; `ObjectUtil` has `DATE_HOUR` and `DATE_MINUTE` parsing plans. | Finish TODO WP-12 with `TimeBucket.HOUR`; consider `MINUTE` only if the extra cardinality is acceptable. |
| P2 | Sorting | The engine still requires one global sort direction. This blocks common ordering like department asc, salary desc and makes cursor pagination less expressive. | `docs/sql-like.md` documents one global direction; `TypedQuery.resolveGlobalSort()` throws on mixed directions. | Plan a deeper sort-model change with per-field direction preserved through parser, builder, keyset cursor support, typed sort orders, docs, and tests. |
| P2 | Reusable typed workflows | `ReportDefinition` and `SavedReport` can wrap SQL-like and natural queries, but not `TypedQuery`. | `ReportDefinition.sql(...)` and `.natural(...)` are the only factories; `SavedReport` is text/serialization focused. | Add a code-owned reusable wrapper for typed queries, likely `ReportDefinition.typed(...)`, but keep `SavedReport` text-only unless a serialization-safe typed-query descriptor is introduced. |
| P2 | Natural pagination parity | Natural queries expose `stream()`/`iterator()` through the shared bound-query pattern, but do not expose `filterPage(...)` / `PageResult`. | No `filterPage`/`PageResult` API in `NaturalQuery`; SQL-like has the page API and docs. | If natural text is a first-class endpoint/query-studio surface, add `NaturalQuery.filterPage(...)` after resolving the query to SQL-like. |
| P3 | Typed diagnostics | Typed queries expose `explain(...)` and `schema(...)`, but not a no-data diagnostics/review object equivalent to SQL-like/natural `QueryDiagnostics`. | `SqlLikeQuery.diagnostics(...)` and `NaturalQuery.diagnostics(...)` exist; `TypedQuery` has no diagnostics method. | Add typed diagnostics or a typed plan preview that reports referenced fields, joins, windows, limits, guard policy, and projection issues before data execution. |
| P3 | Error/doc drift | The mixed-sort typed error suggests using SQL-like for mixed directions, but SQL-like has the same global-direction limitation. | `TypedQuery.resolveGlobalSort()` error text says "Use separate queries or SQL-like for mixed directions"; `docs/sql-like.md` says all ORDER BY fields use one direction. | Fix the error text or implement mixed-direction SQL-like support before recommending that route. |
| P3 | File loaders | File onboarding only accepts `Path`. This is fine for local files but awkward for classpath resources, uploads, object-store streams, and tests that already have a `Reader`/`InputStream`. | `PojoLensFiles.csv/tsv/json/jsonl` all take `Path`; loader support reads through `Files.*`. | Add `Reader` or `InputStream` overloads while preserving `Path` as the convenience API. Include source-name metadata in load reports so diagnostics remain useful. |
| P3 | Repo-memory drift | Some AI memory files still describe removed or outdated facts. | `ai/core/module-index.md` and `ai/core/architecture-map.md` mention a `PojoLens` facade; `ai/core/system-boundaries.md` says time buckets require `java.util.Date`, while code/docs support several Java time types. | Clean those memory files so future agents do not rediscover or repeat stale product facts. |
| P4 | Cross-surface string operators | Natural accepts starts-with/ends-with phrases by lowering them to regex matches; typed and SQL-like expose only `CONTAINS`/`MATCHES`. | Natural parser maps starts/ends phrases to `Clauses.MATCHES`; typed has only `contains` and `matches`; SQL-like docs list `CONTAINS` and `MATCHES`. | Either document natural-only sugar clearly, or add first-class `startsWith`/`endsWith` helpers in typed and SQL-like. |
| P4 | Pushdown metadata parity | Pushdown preview/request/filter-with-pushdown is SQL-like-only. Natural can lower to SQL-like; typed can lower to the builder, but neither exposes host-adapter planning metadata directly. | `SqlLikeQuery` owns `pushdownPreview`, `pushdownRequest`, and `filterWithPushdown`; typed/natural do not. | Keep pushdown SQL-like-only unless host adapters become a major product path. If they do, expose natural resolved pushdown first and typed later. |

## Suggested Roadmap

Do next:

1. Fix the README Java requirement to Java 25.
2. Finish the active typed parity backlog: WP-7, WP-8, WP-10, WP-11, WP-12.
3. Clean stale repo-memory references to removed `PojoLens` facade and old time-bucket type rules.
4. Fix the mixed-sort error text if mixed-direction sorting is not implemented immediately.

Do after the active backlog:

1. Add `ReportDefinition.typed(...)` for code-owned reusable typed workflows.
2. Decide whether natural gets `filterPage(...)` alongside SQL-like.
3. Design mixed-direction sorting as an engine-level change, not a typed-only patch.
4. Add typed diagnostics/plan preview once typed execution parity is complete.
5. Consider `Reader`/`InputStream` loader overloads for non-filesystem boundaries.

Keep out of scope unless the product direction changes:

- Database ownership, ORM behavior, SQL rendering, or real database execution.
- Open-ended AI natural language parsing; the current controlled grammar is a strength.
- Full-text search/indexing; README already points repeated search workloads at indexed collection/search libraries.
- General file-platform scope such as Excel, Parquet, or Avro unless added as explicit adapter modules.

## Validation Notes

This is an audit document only. It did not change library behavior. The findings
come from the documented product surface, public source APIs, and contract-test
coverage available in the repo on 2026-05-18.
