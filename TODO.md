# TODO

## Product Direction

**Conclusion:** PojoLens is the embedded reporting and governed query layer for
Java apps working over already-materialized object snapshots.

**Winning niche:** Safe configurable reporting over data the application already
owns in memory. Strong diagnostics, plan preview, explain, keyset pagination,
telemetry hooks, reusable report/chart/schema helpers, and optional Spring Boot
wiring.

**Non-goals:**
- Not a replacement for jOOQ, Querydsl, or Spring Data for DB-backed queries.
- No free-form AI/chatbot natural queries.
- No auth, RBAC, or tenant-security framework in core.

---

## Status Overview

Execution order is dependency-first, not ticket-number order.

| WP  | Title                                        | Status  | Key deliverables                                                                      |
|-----|----------------------------------------------|---------|---------------------------------------------------------------------------------------|
| WP25| Boundary Loader Consolidation And Expansion  | Completed | `PojoLensFiles` now owns CSV/TSV/JSON/JSONL, shared row-schema plumbing landed, and Excel is an explicit non-goal |
| WP22| Developer Tooling And Static Validation      | Completed | Library-first build tooling now covers batch metamodel generation, saved-report/query validation, and documented build recipes |
| WP23| Typed DSL Aggregation And Join Expansion     | Completed | Typed joins, grouped aggregates, totals-style metrics, and SQL-like parity on one `TypedQuery` surface |
| WP24| Typed DSL Advanced Analytics                 | Completed | Typed `HAVING`, windows, bounded subqueries, `QUALIFY`, and aggregate window frames now share one `TypedQuery`/`TypedPredicate` story |
| WP26| Typed Authoring Compiler Integration         | Completed | `@GeneratePojoLensTypedFields` plus `PojoLensTypedFieldsProcessor` now emit IDE-visible typed constants during javac/Gradle/kapt compilation without AST rewriting |
| WP18| JDK 25 Runtime Knob Evaluation               | Pending | Compact headers, generational Shenandoah, AOT cache startup/runtime matrix            |
| Release Gate | Release Gate                          | Pending | Scope decisions made; lint/chart parity cleared; final release guardrails pending     |

---

Completed work packages were cleared from the active backlog. Historical detail
stays in `CHANGELOG.md` and git history.

---

## WP25: Boundary Loader Consolidation And Expansion

**Priority:** Medium
**Goal:** Broaden file-boundary onboarding under one loader surface without
turning PojoLens into a dataframe or ETL framework.

**Context:**
- The CSV adapter started intentionally bounded, and format expansion only
  makes sense if it stays read-only, row-oriented, and subordinate to the same
  in-memory engine story.
- Competitive analytics-adjacent libraries gain adoption through format
  convenience even when their core engine story is different.
- Any expansion here needs to stay read-only, boundary-only, and explicitly
  subordinate to the in-memory query engine story.
- New format growth should not create a new peer public surface for each file
  type.
- The first slice established `PojoLensFiles` / `runtime.files()` as the
  single file-boundary loader story while keeping `PojoLensCsv` and
  `runtime.csv()` as stable CSV-only convenience routes.

**Tasks:**
- [x] Decide the single public boundary-loader surface for format-specific
      loading so future adapters do not fragment into separate peer entry
      points.
- [x] Add first-party TSV typed loading under that single boundary-loader
      story, with load reports, coercion controls, and runtime-owned defaults
      where the model stays bounded.
- [x] Add first-party JSON/JSONL typed loading under that same
      boundary-loader story with bounded options and diagnostics.
- [x] Factor shared row-schema diagnostics plumbing out of CSV internals where
      reuse improves clarity and keeps error contracts aligned.
- [x] Decide whether Excel support is a bounded adapter worth owning or a
      deliberate non-goal, and document that decision explicitly.
- [x] Keep adapters read-only, boundary-only, and schema-explicit rather than
      widening into a general table platform.
- [x] Add docs and example flows that feed loaded rows into the normal
      SQL-like, natural, and typed execution paths.
- [x] Do not add separate top-level peer product stories such as distinct
      `PojoLensJson` or `PojoLensTsv` surfaces unless that decision is
      explicitly justified and reviewed.

**Validate:**
- `mvn -B -ntp test`
- `scripts/check-doc-consistency.ps1`

---

## WP22: Developer Tooling And Static Validation

**Priority:** High
**Goal:** Close the build-time and CI ergonomics gap with first-party code
generation and query-validation hooks.

**Context:**
- `FieldMetamodelGenerator` started as library-level tooling; WP26 later added
  compiler-time typed generation on top of it.
- `SavedReport`, SQL-like diagnostics, natural diagnostics, and plan preview
  already provide most of the raw pieces for static validation.
- This work should target the consolidated first-read surface from WP21/WP25 so
  build integration does not bake in avoidable naming churn.

**Tasks:**
- [x] Decide the first-party build integration shape: Maven plugin, Gradle task
      recipe, annotation processor, or a staged combination.
- [x] Add automated metamodel generation for typed-field and string-field
      constants without requiring handwritten driver code.
- [x] Add build-time validation for `SavedReport` catalogs and config-owned
      SQL-like/natural query text using diagnostics/plan preview without live
      data execution.
- [x] Emit deterministic machine-readable diagnostics that can fail CI with
      stable error contracts.
- [x] Ship at least one documented build integration example, including
      generated-sources wiring and incremental-build behavior.

**Validate:**
- `mvn -B -ntp test`
- `scripts/check-doc-consistency.ps1`

---

## WP23: Typed DSL Aggregation And Join Expansion

**Priority:** High
**Goal:** Close the largest typed-authoring gap by bringing code-owned grouped
and multi-source queries onto typed APIs.

**Context:**
- `TypedQuery` currently stops at projection, filters, ordering, offset, limit,
  explain, schema, and execution guards.
- SQL-like already owns grouping, joins, metrics, windows, and bounded
  subqueries; typed code-owned use cases fall back to strings for those shapes.
- WP22 should land first so metamodel/codegen and validation support can back
  the larger typed surface.

**Tasks:**
- [x] Design typed join bindings that reuse `JoinBindings` and `DatasetBundle`
      concepts instead of inventing a parallel multi-source model.
- [x] Add typed grouping, aggregate selection, ordering by aggregate output,
      and totals-style projection support.
- [x] Preserve parity with SQL-like validation, schema, explain, and execution
      guards where query shapes overlap.
- [x] Generate or derive the typed field helpers needed for grouped and joined
      projections.
- [x] Land parity/regression coverage against equivalent SQL-like queries
      before exposing the new typed surface as stable guidance.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Pstatic-analysis verify -DskipTests`

---

## WP24: Typed DSL Advanced Analytics

**Priority:** Medium
**Goal:** Extend the typed story beyond grouped queries once the typed
aggregation foundation is stable.

**Context:**
- Windows, `HAVING`, `QUALIFY`, and bounded subqueries are already
  differentiators on the SQL-like surface.
- Bringing those shapes to typed authoring only makes sense after grouped and
  joined typed composition settles into a coherent API.
- This package is about code-owned query composition, not replacing the text
  surfaces for user-authored queries.

**Tasks:**
- [x] Add typed `HAVING` on the existing `TypedQuery` surface for grouped
      fields and aggregate aliases without adding a parallel grouped-query
      wrapper.
- [x] Add typed window outputs and `QUALIFY` on the existing `TypedQuery`
      surface for rank and default running-window shapes without adding a
      parallel window-query wrapper.
- [x] Decide whether bounded/public window-frame configuration belongs on
      `TypedQuery` or remains text-only.
- [x] Evaluate bounded typed subquery and existence predicates against API
      readability, error reporting, and generic-type weight.
- [x] Keep user-authored text flows on SQL-like/natural while extending typed
      composition only where code-owned queries clearly benefit.
- [x] Stage rollout behind parity tests and usage docs so the typed surface
      grows in one direction instead of fragmenting.
- [x] Decide and document any advanced shapes that should remain text-only even
      after the typed expansion work.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Pstatic-analysis verify -DskipTests`

---

## WP26: Typed Authoring Compiler Integration

**Priority:** High
**Goal:** Make typed authoring feel closer to Lombok-style ergonomics by
generating typed field constants automatically at compile time, using the
existing metamodel generator instead of handwritten driver code or AST
rewriting.

**Context:**
- `FieldMetamodelGenerator` already produces deterministic typed constants and
  generated source files.
- The current docs still describe metamodel generation as library-level
  tooling rather than compiler-driven generation.
- The useful part is compiler-integrated source generation plus
  IDE-visible completion, not Java syntax transformation.
- This should complement `docs/typed.md` and `docs/metamodel.md` without
  creating a new public query surface.

**Tasks:**
- [x] Decide the first-party delivery shape: annotation processor,
      Maven/Gradle plugin, or a staged combination.
- [x] Wrap the existing metamodel generator so typed constants can be emitted
      automatically during compilation.
- [x] Wire generated sources into build examples so IDE completion works
      without manual driver code.
- [x] Keep the library generator as the documented fallback, with no AST
      rewriting or Lombok-style syntax expansion.
- [x] Add docs and regression coverage for the generated-source workflow and
      compiler diagnostics.

**Follow-up kept deferred:**
- A dedicated `pojo-lens-processor` artifact remains deferred until processor
  adoption justifies a module-topology change.
- Kotlin property/data-class-native generation remains deferred pending a KSP
  or property-metadata design; the current Kotlin/JVM support is field-model
  based through kapt and `@JvmField var`.

**Validate:**
- `mvn -B -ntp test`
- `scripts/check-doc-consistency.ps1`

---

## WP18: JDK 25 Runtime Knob Evaluation

**Priority:** Experimental Runtime Performance
**Goal:** Measure JDK 25 runtime features as deployment guidance rather than as
mandatory code changes.

**Context:**
- JDK 25 ships productized runtime features such as compact object headers and
  generational Shenandoah, plus simpler AOT cache creation and method-profile
  reuse.
- These knobs can improve startup, footprint, or GC behavior without changing
  PojoLens source code, but they need repo-local data before they become
  guidance.
- The benchmark module and Spring examples provide a reasonable place to gather
  comparative startup and throughput numbers.
- This package is intentionally late in the queue because it is experimental
  and does not reduce product-surface overlap or unlock the developer-facing
  API roadmap.

**Tasks:**
- [ ] Define a small runtime matrix covering default JVM settings, compact
      object headers, generational Shenandoah, and AOT cache startup for the
      benchmark runner and one Spring example app.
- [ ] Execute the matrix with `$env:JAVA_HOME\\bin\\java.exe` and capture
      startup time, heap footprint, and relevant throughput/parity outputs.
- [ ] Decide which knobs are worth documenting in `docs/benchmarking.md` or
      release guidance, and which should remain experimental notes only.
- [ ] Keep all runtime-feature guidance explicitly optional until the data is
      stable across multiple runs and environments.
- [ ] Document platform or tooling assumptions so reruns do not depend on
      unstated local setup.

**Validate:**
- `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- `scripts/check-doc-consistency.ps1`

---

## Release Gate

**Priority:** High
**Goal:** Cut the next release after the active roadmap queue is complete and
the final release guardrails are rerun.

**Tasks:**
- [ ] Run final release guardrails from `RELEASE.md`.
- [ ] Update `ai/state/current-state.md` and `ai/state/handoff.md` after release.

**Current release-cut decisions (2026-04-26):**
- Defer the unimplemented WP6/WP8/WP9 benchmark-backfill tasks until after the
  next release cut. Existing strict core/chart guardrails already cover the
  shipped performance surface, and adding new benchmark suites or threshold
  entries would expand scope while the release gate is blocked elsewhere.
- WP11 landed before the release cut on `2026-04-26`.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- Release benchmark guardrails from `docs/benchmarking.md`.
