# TODO

## SURFACE-WP1: Make SQL-like The Primary Public Query API

**Priority:** High
**Goal:** Present SQL-like as the default user-facing query surface.

Context:
- Most users will prefer a familiar query string over a custom Java builder.
- SQL-like should be the public face for filtering, ordering, grouping, joins,
  windows, subqueries, time buckets, charts, schemas, explain, and templates.
- Java-owned query composition should not require exposing the mutable fluent
  builder as product API.

Tasks:
- [x] Rewrite the README quick start so the first query example is SQL-like.
- [x] Make `PojoLensSql.parse(...)`, `PojoLensSql.template(...)`, and runtime
      SQL-like parsing the recommended default entry points.
- [x] Update `docs/entry-points.md`, `docs/usecases.md`, and
      `docs/product-surface.md` so SQL-like is the primary query story.
- [x] Keep examples focused on parameter binding, typed execution, schema,
      explain, streaming, chart output, and `DatasetBundle` / `JoinBindings`.
- [x] Replace public wording that says SQL-like binds into fluent with wording
      that it lowers into the shared execution engine.

Validate:
- `scripts/check-doc-consistency.ps1`
- `py -3 scripts/check-doc-consistency.py`

---

## SURFACE-WP2: Keep Natural As The Guided Text Alternative

**Priority:** High
**Goal:** Keep natural queries as a controlled non-SQL option without competing
with SQL-like as the default technical API.

Tasks:
- [x] Position `PojoLensNatural` as guided plain-English query text for users
      who should not author SQL-like syntax directly.
- [x] Keep natural docs explicit about controlled grammar, vocabulary, lint,
      strict typing, parameter binding, and authorization boundaries.
- [x] Replace wording that says natural lowers into fluent with wording that it
      lowers into the shared execution engine.
- [x] Keep natural examples after SQL-like examples in public docs unless the
      page is specifically about natural queries.

Validate:
- `scripts/check-doc-consistency.ps1`
- `py -3 scripts/check-doc-consistency.py`

---

## SURFACE-WP3: Demote Fluent To Internal Engine DSL

**Priority:** High
**Goal:** Make the fluent builder surface implementation infrastructure, not a
public product surface.

Context:
- There are no public users yet, so this is a compatibility reset rather than
  a staged deprecation.
- Fluent remains valuable for internal lowering, engine tests, parity checks,
  benchmarks, and programmatic execution planning.
- Do not keep a public Java-native builder just because the current mutable
  builder exists; add a future narrow API only if a real use case proves it.

Tasks:
- [ ] Remove fluent from the README quick start and recommended public
      entry-point tables.
- [ ] Move fluent authoring guidance into an internal engine doc for
      maintainers.
- [ ] Update `docs/public-api-stability.md` so `QueryBuilder`,
      `FilterQueryBuilder`, `Filter`, `QueryRule`, and
      `FluentQueryDefinition` are not stable public API.
- [ ] Decide whether `PojoLensCore` disappears from user docs or remains only
      as an internal-facing bridge.
- [ ] Remove public `ReportDefinition.fluent(...)` positioning or replace it
      with a non-builder public contract.

Validate:
- `scripts/check-doc-consistency.ps1`
- `py -3 scripts/check-doc-consistency.py`

---

## SURFACE-WP4: Reset Compatibility Guards Around The New Surface

**Priority:** High
**Goal:** Make tests and binary compatibility enforce the simplified public API.

Tasks:
- [ ] Remove fluent builder classes from the `binary-compat` include list.
- [ ] Replace stable public API tests that assert fluent availability with
      tests for SQL-like, natural, runtime, reports, CSV, tree, chart,
      cursor, schema, and join-binding surfaces.
- [ ] Keep internal engine tests for fluent behavior, SQL-like lowering,
      natural lowering, reports, streaming, joins, windows, and subqueries.
- [ ] Remove or rewrite public fluent coverage tests so they are not
      compatibility promises.
- [ ] Add a guard that prevents `*.internal.*` APIs from being documented as
      public entry points.

Validate:
- `mvn -B -ntp test`
- `mvn -B -ntp -pl pojo-lens -Pbinary-compat "-Dcompat.baseline.version=2026.04.17.1834" -DskipTests verify` after the reset is intentionally reflected.
- `git diff --check`

---

## SURFACE-WP5: Internalize Or Hide Builder Packages

**Priority:** Medium
**Goal:** Put fluent implementation types behind an internal boundary without
destabilizing SQL-like and natural execution.

Tasks:
- [ ] Inventory imports of `laughing.man.commits.builder.*` across main code,
      tests, docs, examples, and benchmarks.
- [ ] Choose the target package shape for internal builder types.
- [ ] Move or wrap builder types so public entry points do not expose mutable
      internal builders.
- [ ] Replace public factories that return `QueryBuilder` with public
      contracts that return rows, reports, charts, schemas, or explain payloads.
- [ ] Keep benchmarks able to measure the internal engine path without
      implying it is public API.

Validate:
- `mvn -B -ntp test`
- `mvn -B -ntp -pl pojo-lens-benchmarks -am test`
- `scripts/check-doc-consistency.ps1`

---

## SURFACE-WP6: Refresh Docs, Examples, And Release Readiness

**Priority:** Medium
**Goal:** Prepare the next date-based release around the SQL-like-first public
surface.

Tasks:
- [ ] Update `README.md`, `RELEASE.md`, `MIGRATION.md`,
      `docs/product-surface.md`, `docs/entry-points.md`, `docs/usecases.md`,
      `docs/reusable-wrappers.md`, `docs/reports.md`, and `docs/modules.md`.
- [ ] Update docs that currently use fluent as the primary example: charts,
      computed fields, time buckets, tabular schema, tree, telemetry,
      metamodel, and caching.
- [ ] Confirm examples do not teach fluent as user API.
- [ ] Confirm benchmark docs and threshold checks still work after internal
      package moves.
- [ ] Update changelog with the SQL-like-first public-surface reset.
- [ ] Cut a new date-based release only after the reset is validated.

Validate:
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- release benchmark guardrails from `docs/benchmarking.md`
