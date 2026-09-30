# Feature Audit

Date: 2026-09-27

Scope: docs-based review of the current feature list after WP-1 through WP-23.
Reviewed every guide under `docs/` (grammar, limitations, and non-goal sections in
`sql-like.md`, `natural.md`, and `typed.md`; the family matrix in
`product-surface.md`; helper, tooling, loader, and runtime guides), plus `README.md`.
Supersedes the 2026-05-18 audit (its findings are listed as resolved below).

## Executive Summary

PojoLens covers in-memory querying over POJOs through SQL-like text, controlled
natural text, and a typed Java DSL, with joins, grouping, HAVING, windows and
QUALIFY, time buckets, bounded subqueries, keyset paging, computed fields, charts,
tables, reports, trees, facets, snapshot comparison, file loaders (including
`Reader`/`InputStream` and records), runtime policy, Spring/JDBC bridges,
diagnostics, pushdown metadata, regression fixtures, build tooling, and benchmarks.

The typed DSL had overtaken the text surfaces on everyday filtering. WP-24 to WP-26
closed most of that gap (literal lists, `NOT IN`, `BETWEEN`, null checks, general and
text negation) and WP-27 added `LIKE`/`ILIKE` and natural `ignoring case`, so the
SQL-like and natural filtering gap is closed. WP-28 added `SELECT DISTINCT` and
`COUNT(DISTINCT)`, WP-30 statistical aggregates, and WP-29 text, null, and date-part
expression functions (also in `GROUP BY`/`ORDER BY` and computed fields), and WP-31
`LAG`/`LEAD` plus windows and `QUALIFY` over grouped rows, and WP-32 typed field
validation for joined queries, and WP-33 lazy typed streaming. No audited gap remains
open.

No correctness blocker is open: the 2026-09-27 core bug hunt fixes shipped as
WP-22/WP-23, and release gates (tests, japicmp, lint baseline, SpotBugs,
benchmark guardrails) pass.

## Feature Inventory

| Area | Current status | Notes |
|---|---|---|
| SQL-like querying | Strong, primary surface | SELECT/aliases/`DISTINCT`, chained INNER/LEFT/RIGHT joins, WHERE with AND/OR and bounded `IN`/`EXISTS` subqueries, aggregates incl. `COUNT(field)`, `COUNT(DISTINCT field)`, MEDIAN/PERCENTILE/STDDEV/VARIANCE, GROUP BY, HAVING, rank, aggregate, and `LAG`/`LEAD` windows (also over grouped rows), QUALIFY, time buckets, ORDER BY (mixed direction), LIMIT/OFFSET, params/templates, keyset paging (aliases, aggregates, windows, nulls), explain/diagnostics/plan preview, pushdown metadata, lists, `IS NULL`, `BETWEEN`, `NOT`, `[NOT] LIKE`/`ILIKE`. |
| Natural querying | Good guided surface | Controlled grammar over the same engine: comparison phrases, contains/starts/ends with (literal and `:param`), joins, subqueries, grouping, time buckets, windows, charts, pagination, vocabulary. `is one of`, `is between`, `is null`, parenthesized groups and `not (...)`. `does not contain/start/end`, `... ignoring case`. |
| Typed DSL | Broadest filtering | eq/ne/range/`between`/`in`/null checks, contains/containsIgnoreCase/matches/startsWith/endsWith, `any`/`none`, `not()` (including string predicates), joins, grouping, windows, time buckets, computed fields, paging, stream/iterator, diagnostics/plan preview, field-name validation. `stream()` materialises. |
| Comparison semantics | Uniform and exact | Null never matches a value comparison; exact numeric, enum, char, text ordering; date values exact, text literals at written precision. |
| Output helpers | Healthy | Charts/Chart.js, tabular schema, stats presets, facets, reports (SQL-like, natural, typed), tree shaping, snapshot comparison. |
| Data onboarding | Healthy | CSV/TSV/JSON/JSONL from `Path`, `Reader`, `InputStream`; records; load reports with `sourceName()`. Excel is a documented non-goal. |
| Runtime and integration | Well-scoped | `PojoLensRuntime` caches/policy/vocabulary/defaults, Spring Boot starter, JDBC bridge, telemetry. |
| Tooling | Mature | Metamodel processor/generator, saved-report catalog validation, regression fixtures, benchmark suites incl. core-semantics diagnostics, doc-consistency and compat checks. |

Filtering parity (the main gap):

| Need | SQL-like | Natural | Typed |
|---|---|---|---|
| Literal list `in ('A','B')` | yes (WP-24) | yes, `is one of` (WP-24) | yes |
| `NOT IN` | yes (WP-24) | yes, `is not one of` (WP-24) | yes |
| `BETWEEN` in WHERE | yes (WP-25) | yes, `is [not] between` (WP-25) | yes |
| `IS [NOT] NULL` | yes (WP-25) | yes, `is [not] null` | yes |
| General `NOT (...)` | yes (WP-25) | yes, `not (...)` groups (WP-25) | yes |
| Case-insensitive contains | yes, `ILIKE` (WP-27) | yes, `ignoring case` (WP-27) | yes |
| Negated text match | yes, `NOT CONTAINS`/`NOT MATCHES` (WP-26) | yes, `does not contain/start/end` (WP-26) | yes, `not()` (WP-26) |
| `LIKE` wildcards | yes, `[NOT] LIKE`/`ILIKE` (WP-27) | n/a | no (`startsWith`/`endsWith`/`matches`) |

## Findings

Priority guide: P1 blocks everyday use of the primary surface, P2 is important
breadth, P3 is completeness.

| Priority | Area | Finding | Evidence | Recommendation (TODO) |
|---|---|---|---|---|
| P1 (done) | SQL-like/natural filtering | No literal `IN`/`NOT IN` lists. | `sql-like.md` Supported Grammar lists only `IN (select ...)`; `natural.md` only `is in query`. | WP-24 |
| P1 (done) | SQL-like/natural filtering | No `IS NULL`, `BETWEEN`, or general `NOT`. | `sql-like.md` grammar; `NOT` only in `NOT EXISTS`; `natural.md` Canonical Grammar has no null/range phrase. | WP-25 |
| P1 (done) | All surfaces | Negated text matching missing in the engine. | `typed.md`: `NOT(CONTAINS/.../ENDS_WITH)` not supported. | WP-26 |
| P1 (done) | SQL-like/natural matching | No `LIKE`/`ILIKE`; no case-insensitive matching on text surfaces. | `sql-like.md` "Prefix And Suffix Matching" regex recipe; typed-only `containsIgnoreCase`. | WP-27 |
| P2 (done) | Output | No `SELECT DISTINCT` or `COUNT(DISTINCT)`. | Not in `sql-like.md` grammar; distinct exists only in the internal fluent engine. | WP-28 |
| P2 (done) | Expressions | Computed fields and expressions are numeric only; no string/date functions. | `computed-fields.md`; `sql-like.md` grammar. | WP-29 |
| P2 (done) | Aggregation | Only COUNT/SUM/AVG/MIN/MAX. | `sql-like.md` aggregate list. | WP-30 |
| P2 (done) | Windows | No `LAG`/`LEAD`; windows cannot combine with GROUP BY. | `sql-like.md` Current Limitations and Window Functions Contract. | WP-31 |
| P3 (done) | Typed validation | Field-name validation skipped for joined typed queries. | `typed.md` validation note. | WP-32 |
| P3 (done) | Typed execution | `stream()`/`iterator()` materialise before streaming. | `typed.md` laziness caveat. | WP-33 |

## Resolved Since The 2026-05-18 Audit

- README Java 25 requirement, mixed-sort error text, repo-memory drift (quick fixes).
- Typed execution parity: `stream`, `iterator`, `filterPage`, `computedFields`
  (WP-8, WP-10, WP-12, WP-21).
- Case-insensitive typed matching (WP-7); prefix/suffix matching (WP-19, WP-20).
- `TimeBucket.HOUR` (WP-13); engine-level mixed-direction sort (WP-14).
- `ReportDefinition.typed(...)` (WP-15); natural `filterPage` (WP-16).
- Typed diagnostics and plan preview (WP-17).
- `Reader`/`InputStream` loaders with `sourceName()` (WP-18).
- Core correctness pass and follow-ups: comparison semantics, precision, grouping,
  joins, keyset paging, records, `COUNT(field)`, typed field validation (WP-22,
  WP-23).
- Still open from May, deliberately: pushdown metadata stays SQL-like-only unless
  host adapters become a major product path.

## Suggested Roadmap

1. WP-24 and WP-25: cheap parser work over existing engine support; biggest
   day-one win for SQL users.
2. WP-26 then WP-27: one engine capability (negated text match), then `LIKE`/`ILIKE`
   built on the existing literal-pattern owner.
3. WP-28, WP-30, WP-31: output and aggregation breadth.
4. WP-29: done 2026-09-28 (`docs/design/wp-29-expression-functions.md`).
   WP-31: done 2026-09-30 (`docs/design/wp-31-window-gaps.md`).
5. WP-32 and WP-33: typed completeness (done 2026-09-30).

A release cut for the unreleased WP-7 to WP-23 work can happen before or after the
P1 packages; its gates already pass.

## Keep Out Of Scope

- Correlated and scalar subqueries, `UNION`/set operations, and arbitrary nested SQL
  planning (`sql-like.md` Current Limitations, `README.md` Limits And Non-Goals).
- Database ownership: query execution, SQL rendering, ORM behavior (pushdown stays
  advisory metadata).
- Free-form or fuzzy natural language (`natural.md` Non-goals).
- Excel and document-style file formats (`files.md`).

## Validation Notes

Audit only; it changes no library behavior. Findings come from the documented
grammar, limitations, and non-goal sections under `docs/` as of 2026-09-27, after
WP-23 and the release-gate run.
