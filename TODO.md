# TODO

## Current Goal

Expand typed-surface completeness and cross-surface parity identified in the
2026-05-18 wild-comparison audit and the source-backed feature audit
(`feature-audit.md`). Keep PojoLens focused on the Java library, benchmarks,
release flow, docs, and repo-memory helpers.

### Quick fixes (no WP needed)

- [ ] **README JDK requirement** — README says `JDK 17+`; root POM uses
  `<maven.compiler.release>25</maven.compiler.release>`. Update to Java 25,
  or intentionally lower the build target.
- [ ] **Mixed-sort error text** — `TypedQuery.resolveGlobalSort()` tells
  callers to "Use SQL-like for mixed directions", but SQL-like has the same
  global-direction limit. Fix the error text to reflect reality.
- [ ] **Repo-memory drift** — `ai/core/module-index.md` and
  `ai/core/architecture-map.md` mention a removed `PojoLens` facade;
  `ai/core/system-boundaries.md` says time buckets require `java.util.Date`.
  Scrub those files.

---

## Work Packages

### ~~WP-6 — Execution convenience methods on `TypedQuery`~~ ✓ DONE 2026-05-18

---

### ~~WP-7 — Case-insensitive string matching~~ ✓ DONE 2026-05-18

**Problem:** `TypedField.contains(value)` is case-sensitive. The single most
common reason callers fall back from typed to SQL-like. No `icontains`
equivalent exists on any surface today.

**Work:**
- Audit `FilterQueryBuilder` / `Clauses` for an existing case-insensitive path
- If engine supports it: add `Operator.CONTAINS_IGNORE_CASE`, wire lowering,
  expose `TypedField.containsIgnoreCase(String)` and
  `TypedPredicate.containsIgnoreCase(field, value)`
- If engine does not: implement as a scan-level post-filter in `TypedQuery`
  (apply `String.toLowerCase` on both sides before match)
- Consider whether SQL-like/natural should get an equivalent operator or a
  documented `MATCHES`-based recipe alongside the typed implementation
- Add contract tests (parity with `filter(…).stream().filter(…)` reference)
- Update `docs/typed.md`

---

### ~~WP-8 — `stream()` lazy execution on `TypedQuery`~~ ✓ DONE 2026-05-18

**Problem:** `TypedQuery.filter()` is eager and returns a `List<T>`. SQL-like
and natural both expose `stream()`. Callers doing downstream `flatMap`,
`collect`, or early-exit patterns pay for full materialisation unnecessarily.

**Work:**
- `TypedQuery.stream(List<T>)` → `Stream<T>`
- `TypedQuery.stream(DatasetBundle)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings, Class<P>)` → `Stream<P>`
- Thin wrapper over `filter(…).stream()` for now; document laziness caveat
- Add stable-surface assertions and basic stream-chain tests
- Update `docs/typed.md`

---

### ~~WP-9 — `TypedPredicate.any()` / `.none()` sentinels~~ ✓ DONE 2026-05-18

**Problem:** Building conditional predicate chains without null guards requires
boilerplate. Spring Data `Specification.where(null)`, jOOQ `trueCondition()`,
and QueryDSL `BooleanBuilder` all solve this with sentinel values.

**Work:**
- `TypedPredicate.any()` — always-true predicate; lowers to no WHERE clause
- `TypedPredicate.none()` — always-false predicate; returns zero rows
- Add `Operator.ANY` / `Operator.NONE` or handle as special cases in lowering
- Add contract tests: `any()` returns all rows, `none()` returns empty,
  `pred.and(any())` ≡ `pred`, `pred.or(none())` ≡ `pred`
- Update `docs/typed.md`

---

### WP-10 — `computedFields(ComputedFieldRegistry)` on `TypedQuery`  [P2]

**Problem:** SQL-like and Natural both expose `.computedFields(registry)`.
TypedQuery is the only entry point that cannot attach one, forcing callers to
switch surfaces just for computed fields.

**Work:**
- Add `TypedQuery.computedFields(ComputedFieldRegistry registry)` fluent method
- Store in builder state; apply via `builder.computedFields(registry)` in
  `applyToBuilder`
- Add `hasComputedFields()` / `computedFieldRegistry()` accessors
- Add stable-surface assertion and a basic computed-field filter test
- Update `docs/typed.md`

---

### WP-11 — `filterPage()` / `PageResult<T>` on `TypedQuery`  [P2]

**Problem:** SQL-like exposes `filterPage(…)` → `PageResult<T>`. TypedQuery
has `limit()` and `offset()` but no `filterPage()`, so callers run two queries
manually. Also: assess whether Natural should gain `filterPage()` for parity.

**Work:**
- `TypedQuery.filterPage(List<T>)` → `PageResult<T>`
- `TypedQuery.filterPage(DatasetBundle)` → `PageResult<T>`
- `TypedQuery.filterPage(List<T>, JoinBindings)` → `PageResult<T>`
- Reuse existing `PageResult` from the `sqllike` package or extract to shared
- Run filtered-count pass (no limit/offset) + paginated-result pass
- Consider `NaturalQuery.filterPage(…)` for cross-surface pagination parity
- Add contract tests: total reflects unfiltered count, rows respect limit/offset
- Update `docs/typed.md` (and `docs/natural.md` if natural gets it)

---

### WP-12 — `TimeBucket.HOUR` granularity  [P2]

**Problem:** `TimeBucket` has DAY → YEAR but no HOUR. Event-stream workloads
routinely bucket by hour. `ObjectUtil` already parses `DATE_HOUR` and
`DATE_MINUTE` formats, suggesting the engine can handle sub-day granularity.

**Work:**
- Check `TimeBucketUtil` and `FilterCore` for existing HOUR/MINUTE support
- If supported: add `TimeBucket.HOUR` (and `MINUTE` if stable) to the enum
- Verify `TimeBucketPreset`, `QueryTimeBucket`, and `TimeBucketAggregationTest`
  handle the new granularity correctly
- Add a contract test: bucket by HOUR groups rows into correct hour slots
- Update `docs/typed.md`

---

### WP-13 — Mixed-direction sort (engine-level)  [P2]

**Problem:** The engine enforces one global sort direction. This blocks common
ordering (`department ASC, salary DESC`) and weakens keyset cursor expressions.
`TypedSortOrder` models per-field direction at the descriptor level, but
`resolveGlobalSort()` rejects mixed configurations at execution time. SQL-like
has the same limitation.

**Work:**
- Audit `OrderEngine`, `FilterCore`, and `FilterQueryBuilder` for what changes
  are needed to preserve per-field direction through execution
- If feasible: implement per-field direction in the engine and remove
  `resolveGlobalSort()` mixed-direction rejection from `TypedQuery`
- Update SQL-like lowering and parser to pass per-field direction if engine gains it
- Fix keyset cursor metadata to reflect per-field directions
- Update `docs/sql-like.md`, `docs/typed.md`, and the SQL-like mixed-direction
  error message
- If not feasible in one pass: at minimum fix the misleading error text (see
  Quick fixes above) and document the limitation clearly on all surfaces

---

### WP-14 — `ReportDefinition.typed(…)` reusable typed workflow  [P2]

**Problem:** `ReportDefinition` and `SavedReport` wrap SQL-like and natural
queries for reusable report definitions, schema review, replay, and validation.
`TypedQuery` has no equivalent entry point — callers must hold a raw
`TypedQuery<?>` reference and re-execute it manually.

**Work:**
- Add `ReportDefinition.typed(TypedQuery<?> query, Class<?> projectionClass)`
  factory (or a `TypedReportDefinition` adapter preserving immutability)
- Keep `SavedReport` text-only unless a serialization-safe typed descriptor is
  introduced separately
- Wire into `SavedReportCatalogValidator` if applicable
- Add contract test: typed report definition executes against a data source
  and returns the correct projection
- Update `docs/typed.md` or `docs/output-helpers.md` with a usage example

---

### WP-15 — `NaturalQuery.filterPage(…)` pagination parity  [P2]

**Problem:** SQL-like has `filterPage()` / `PageResult<T>` but natural queries
do not, despite natural being a first-class endpoint/query-studio surface.

**Work:**
- Add `NaturalQuery.filterPage(List<?> rows, Class<T> cls)` → `PageResult<T>`
- Add `NaturalQuery.filterPage(DatasetBundle, Class<T>)` → `PageResult<T>`
- Resolve the natural query to SQL-like and delegate to SQL-like `filterPage`
- Add contract tests mirroring SQL-like pagination behaviour
- Update `docs/natural.md`

---

### WP-16 — Typed diagnostics / plan preview  [P3]

**Problem:** SQL-like and natural have `diagnostics()` and `QueryDiagnostics`
for no-data pre-execution review. TypedQuery exposes only `explain(rows)` and
`schema(rows)`, both of which require data. There is no pre-execution typed
plan review that reports referenced fields, joins, windows, limits, guard
policy, or projection issues.

**Work:**
- Add `TypedQuery.diagnostics()` → a typed plan/review object (could reuse
  `QueryDiagnostics` or introduce `TypedPlanPreview`)
- Should work without a data source (no rows argument); read builder state
  directly
- Report: referenced field names, joins, group keys, metrics, windows,
  sort orders, limit/offset, guard policy, time buckets, computed fields
- Add contract tests: diagnostics reflects configured query shape
- Update `docs/typed.md`

---

### WP-17 — File loader `Reader`/`InputStream` overloads  [P3]

**Problem:** `PojoLensFiles` accepts only `Path`. This is inconvenient for
classpath resources, in-memory uploads, object-store streams, and tests that
already hold a `Reader` or `InputStream`.

**Work:**
- Add `PojoLensFiles.csv(Reader reader, CsvOptions options, Class<T> cls)`
  and equivalent for TSV, JSON, and JSONL
- Preserve `Path`-based overloads as the primary convenience API
- Include synthetic source-name metadata in `CsvLoadReport` / `JsonLoadReport`
  so diagnostics remain useful when no path is available
- Add contract tests using `StringReader` as a source
- Update `docs/onboarding.md` or the relevant data-loading guide

---

### WP-18 — `startsWith` / `endsWith` on typed and SQL-like  [P4]

**Problem:** Natural maps starts-with/ends-with phrases to `MATCHES` regex
under the hood. Typed and SQL-like expose only `contains()`/`matches()`.
Callers who want prefix/suffix matching must write regex patterns by hand.

**Work:**
- Add `TypedField.startsWith(String)` → `TypedPredicate` backed by a
  `MATCHES` lowering with `^` prefix anchor
- Add `TypedField.endsWith(String)` with `$` suffix anchor
- Add `TypedPredicate.startsWith(field, value)` and `.endsWith(field, value)`
  static factories
- Consider SQL-like `STARTS_WITH(field, value)` / `ENDS_WITH(field, value)`
  function syntax or just document the `MATCHES` recipe
- Add contract tests and natural parity coverage
- Update `docs/typed.md`

---

## Working Rules

- Do not add unrelated services, runtime infrastructure, or extra subsystems.
- Each WP ships as its own commit with a `CHANGELOG.md` entry under
  `[Unreleased]`.
- Run `mvn -B -ntp test` before marking a WP done.
- After any `docs/**` change run `scripts/docs/check-doc-consistency.ps1`.
- After any `ai/**` change run `scripts/ai/refresh-ai-memory.ps1` and
  `scripts/ai/refresh-ai-memory.ps1 -Check`.

---

## Done Recently

- [x] `2026-05-18`: WP-1 — `TypedPredicate.not()` lowered via DeMorgan; 1141 tests.
- [x] `2026-05-18`: WP-2 — `contains()` / `matches()` on TypedField and TypedPredicate; 1153 tests.
- [x] `2026-05-18`: WP-3 — `TypedSortOrder` + `orderBy(TypedSortOrder...)` vararg; 1160 tests.
- [x] `2026-05-18`: WP-4 — `TypedQuery.timeBucket(…)` wired to engine; 1165 tests.
- [x] `2026-05-18`: WP-5 — `TypedField.between(lo, hi)` / `TypedPredicate.between(…)`; 1171 tests.
- [x] `2026-05-18`: WP-6 — `count`, `exists`, `findFirst`, `findOne` on TypedQuery; 1183 tests.
- [x] `2026-05-18`: WP-7 — `containsIgnoreCase` on TypedField/TypedPredicate; lowers to `MATCHES(?i)`; 1191 tests.
- [x] `2026-05-18`: WP-8 — `stream()` overloads on TypedQuery (4 overloads, wraps `filter`); 1197 tests.
- [x] `2026-05-18`: WP-9 — `TypedPredicate.any()` / `none()` sentinels with identity/absorption laws; 1211 tests.
