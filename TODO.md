# TODO

## QOL-WP1: Query Diagnostics Report API

**Priority:** High
**Goal:** Give developers a non-executing way to inspect query requirements,
validation findings, and lint guidance before running against rows.

Context:
- SQL-like is the primary public query surface, and config/admin-driven query
  text needs better preflight tooling.
- Existing parse, validation, lint, schema, and explain behavior already know
  most of the information developers need.
- Diagnostics should improve developer experience without creating a new query
  style or exposing internal fluent planning types.

Scope:
- Add a public diagnostics result for SQL-like queries with referenced fields,
  required parameters, selected output fields, joins/sources, warnings, and
  validation errors.
- Prefer a non-throwing diagnostics path so tooling can show multiple findings
  at once.
- Keep execution `explain(...)` separate from pre-execution diagnostics.
- Add natural-query diagnostics only if it can reuse the SQL-like equivalent
  safely through `equivalentSqlLike()`.

Tasks:
- [x] Inventory existing validation, lint, schema, and parse-error metadata.
- [x] Design a small public `QueryDiagnostics` contract.
- [x] Add `diagnostics(...)` entry points on `SqlLikeQuery` and runtime-owned
      SQL-like parsing.
- [x] Include required named params, referenced fields, output fields, joins,
      subquery usage, lint warnings, and validation failures.
- [x] Add docs and examples for config-screen validation and CI query checks.
- [x] Add contract tests for success, missing params, unknown fields, joins,
      subqueries, and lint warnings.

Review hardening findings fixed:
- [x] Document `QueryDiagnostics` with executable SQL-like examples.
- [x] Include diagnostics in public API stability docs and contract tests.
- [x] Include parent fields, child join fields, and nested subquery fields in
      `referencedFields()`.
- [x] Include named subquery sources in `joinSources()`.
- [x] Collect multiple unknown `WHERE` field diagnostics instead of reporting
      only the first thrown validator error.
- [x] Resolve runtime natural vocabulary before class-backed natural
      diagnostics.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Diagnostics*Test,SqlLike*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## QOL-WP2: Field And Source Exposure Policy

**Priority:** High
**Goal:** Turn the existing "approved fields/sources" safety guidance into a
bounded validation feature for user-authored query text.

Context:
- Public docs already tell users to restrict exposed fields/sources before
  executing SQL-like or natural text.
- This should be query exposure validation only, not authentication,
  authorization, row-level security, or a policy framework.
- The feature should work with SQL-like first and natural where natural lowers
  to the same referenced-field/source model.

Scope:
- Add a small public policy type for allowlisted fields and named sources.
- Validate referenced fields and sources before execution.
- Support explicit deny rules only if they keep the API clearer than separate
  allowlists.
- Keep row visibility and tenant authorization outside PojoLens.

Tasks:
- [x] Design `QueryExposurePolicy` or equivalent with field and source
      allowlists.
- [x] Add runtime-scoped policy wiring without making runtime a third query
      style.
- [x] Apply policy checks to SQL-like parse/bind/execute paths.
- [x] Apply policy checks to natural queries through the lowered SQL-like
      representation where practical.
- [x] Document security boundaries and non-goals clearly.
- [x] Add tests for allowed fields, blocked fields, blocked sources, joins,
      subqueries, and natural vocabulary aliases.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Exposure*Test,*Policy*Test,SqlLike*Test,Natural*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## QOL-WP3: SQL-like Dry Run / Plan Preview

**Priority:** Medium
**Goal:** Let developers inspect a query's structural shape without executing
against rows.

Context:
- `explain(...)` is execution-oriented and can include row/stage behavior.
- A dry run should answer "what will this query try to do?" rather than "what
  happened while it ran?"
- This is useful for admin tooling, CI validation, and generated query review.

Scope:
- Add a structural preview for SQL-like queries: selected fields, filters,
  grouping, ordering, windows, joins, subqueries, limit/offset, and required
  params.
- Do not add cost estimation, optimizer hints, row-count estimates, database
  semantics, or fluent API exposure.
- Keep preview deterministic and serializable enough for logging/tests.

Tasks:
- [x] Decide whether preview is part of `QueryDiagnostics` or a separate
      `SqlLikePlanPreview`.
- [x] Reuse parser/AST metadata instead of rebuilding query inspection by hand.
- [x] Add preview output for joins, grouped predicates, windows, time buckets,
      subqueries, and paging.
- [x] Add docs showing preview before executing config-owned queries.
- [x] Add tests that lock stable preview fields without overfitting internal
      AST implementation details.

Review hardening findings fixed:
- [x] Preserve grouped predicate shape through `PlanPreviewPredicate` instead
      of exposing only a flattened predicate list.
- [x] Keep repeated literal predicates in preview output instead of collapsing
      same-field/same-operator entries.
- [x] Expose nested `subqueryPreview()` details for `IN`, `EXISTS`, and
      `NOT EXISTS` predicates.
- [x] Add stable public API contract coverage for `planPreview()` and preview
      companion types.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Preview*Test,SqlLikeParserTest,SqlLikeQueryContractTest" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## QOL-WP4: Page Result Helper

**Priority:** Medium
**Goal:** Make common API pagination easier by returning rows plus cursor
metadata in one public helper.

Context:
- `SqlLikeCursor` and keyset pagination already exist, but service authors must
  assemble response metadata manually.
- The helper should sit on top of existing SQL-like execution and cursor
  contracts.
- It must not change core query semantics or invent a new pagination language.

Scope:
- Add a `PageResult<T>` style contract with rows, optional next cursor, and
  `hasMore`.
- Support deterministic keyset pagination when the query has a stable
  `ORDER BY`.
- Define exact behavior around `LIMIT`, `limit + 1` lookahead, and empty pages.
- Keep offset pagination as normal query behavior unless a small helper is
  clearly useful.

Tasks:
- [x] Design `PageResult<T>` and page execution methods for SQL-like queries.
- [x] Enforce stable ordering requirements for cursor generation.
- [x] Implement lookahead behavior without leaking the extra row.
- [x] Document API endpoint usage and edge cases.
- [x] Add tests for first page, next page, no more rows, composite sort keys,
      missing order, and ties.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Page*Test,*Cursor*Test,SqlLikeQueryContractTest" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## QOL-WP5: Better Error Suggestions

**Priority:** Medium
**Goal:** Make common query mistakes easier to fix by suggesting nearby fields,
params, and source names in deterministic error messages.

Context:
- Unknown field and parameter errors are frequent during adoption.
- Suggestions improve developer experience without changing query semantics.
- Suggestions must be deterministic, bounded, and safe for lint/strict modes.

Scope:
- Add nearest-name suggestions for unknown fields, params, aliases, and named
  sources where candidate sets are already known.
- Keep suggestions short; avoid noisy "maybe" lists.
- Do not expose denied fields when an exposure policy blocks them.
- Reuse the same suggestion helper from SQL-like and natural diagnostics where
  possible.

Tasks:
- [x] Inventory current unknown-field, unknown-param, and unknown-source errors.
- [x] Add a deterministic bounded name-suggestion helper.
- [x] Wire suggestions into SQL-like validation errors.
- [x] Wire suggestions into natural vocabulary/field errors where safe.
- [x] Add docs examples only where they help troubleshooting.
- [x] Add tests for typos, no close match, multiple close matches, case
      differences, and blocked-policy fields.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Validation*Test,*Diagnostics*Test,Natural*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## Release Follow-Up

**Priority:** High
**Goal:** Cut a new date-based release after the SQL-like-first public surface
reset and any chosen QoL package are validated.

Tasks:
- [ ] Decide whether to release immediately or include one QoL package first.
- [ ] Investigate or refresh the stale Checkstyle baseline comparator; the
      current `scripts/check-lint-baseline.ps1` run reports repo-wide baseline
      drift before release.
- [ ] Run the final release guardrails from `RELEASE.md`.
- [ ] Update release notes for the selected shipped scope.
- [ ] Cut the next date-based release.

Validate:
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- release benchmark guardrails from `docs/benchmarking.md`
