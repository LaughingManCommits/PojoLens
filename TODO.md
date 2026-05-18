# TODO

## Current Goal

Expand typed-surface completeness and parity identified in the 2026-05-18
wild-comparison audit. Keep PojoLens focused on the Java library, benchmarks,
release flow, docs, and repo-memory helpers.

---

## Work Packages

### ~~WP-6 — Execution convenience methods on `TypedQuery`~~ ✓ DONE 2026-05-18

**Problem:** Every typed query library in the wild (`jOOQ`, `QueryDSL`,
Spring Data) provides `count`, `exists`, `findFirst`, `findOne` as first-class
execution methods. `TypedQuery` forces callers to call `filter()` and
post-process the list — incurring full materialisation even when only a boolean
or single row is needed.

**Work:**
- `TypedQuery.count(List<T>)` → `long` — matching row count without full list
- `TypedQuery.count(DatasetBundle)` → `long`
- `TypedQuery.exists(List<T>)` → `boolean` — true if ≥1 match (short-circuits)
- `TypedQuery.exists(DatasetBundle)` → `boolean`
- `TypedQuery.findFirst(List<T>)` → `Optional<T>` — first match or empty
- `TypedQuery.findFirst(DatasetBundle)` → `Optional<T>`
- `TypedQuery.findOne(List<T>)` → `Optional<T>` — throws if >1 result
- `TypedQuery.findOne(DatasetBundle)` → `Optional<T>`
- Add contract tests and stable-surface assertions
- Update `docs/typed.md`

**Notes:** All implementable on top of `filter()` — no engine changes needed.
`exists` should apply `.limit(1)` internally to avoid full scan.

---

### WP-7 — Case-insensitive string matching  [P2]

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
- Add contract tests (parity with `filter(…).stream().filter(…)` reference)
- Update `docs/typed.md`

---

### WP-8 — `stream()` lazy execution on `TypedQuery`  [P2]

**Problem:** `TypedQuery.filter()` is eager and returns a `List<T>`. Callers
doing downstream `flatMap`, `collect`, or early-exit patterns pay for full
materialisation. SQL-like already exposes `stream()` and `iterator()`.

**Work:**
- `TypedQuery.stream(List<T>)` → `Stream<T>`
- `TypedQuery.stream(DatasetBundle)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings, Class<P>)` → `Stream<P>`
- Thin wrapper over `filter(…).stream()` for now; document laziness caveat
- Add stable-surface assertions and basic stream-chain tests
- Update `docs/typed.md`

---

### WP-9 — `TypedPredicate.any()` / `.none()` sentinels  [P3]

**Problem:** Building conditional predicate chains without null guards requires
boilerplate: start with a known predicate, or null-check before `.and()`.
Spring Data's `Specification.where(null)`, jOOQ's `DSL.trueCondition()`, and
QueryDSL's `BooleanBuilder` all solve this with sentinel values.

**Work:**
- `TypedPredicate.any()` — always-true predicate; lowers to no WHERE clause
  (or an engine no-op rule)
- `TypedPredicate.none()` — always-false predicate; lowers to guaranteed
  zero-result filter
- Add `Operator.ANY` / `Operator.NONE` or handle as special cases in lowering
- Add contract tests: `any()` returns all rows, `none()` returns empty,
  `pred.and(any())` ≡ `pred`, `pred.or(none())` ≡ `pred`
- Update `docs/typed.md`

---

### WP-10 — `computedFields(ComputedFieldRegistry)` on `TypedQuery`  [P3]

**Problem:** `ComputedFieldRegistry` allows derived numeric fields to be
materialised onto working rows before filter and aggregation. SQL-like and
Natural both expose `.computedFields(registry)`. TypedQuery is the only entry
point that cannot attach one, breaking parity and forcing callers to switch
surfaces just for computed fields.

**Work:**
- Add `TypedQuery.computedFields(ComputedFieldRegistry registry)` fluent method
- Store in builder state; apply via `builder.computedFields(registry)` in
  `applyToBuilder`
- Add `hasComputedFields()` / `computedFieldRegistry()` accessors
- Add stable-surface assertion and a basic computed-field filter test
- Update `docs/typed.md`

---

### WP-11 — `filterPage()` / `PageResult<T>` on `TypedQuery`  [P3]

**Problem:** The web tier universally needs offset pagination with a total
count. SQL-like exposes `filterPage(…)` → `PageResult<T>`. TypedQuery has
`limit()` and `offset()` but no `filterPage()`, so callers must run two
queries manually and reconstruct the total.

**Work:**
- `TypedQuery.filterPage(List<T>)` → `PageResult<T>`
- `TypedQuery.filterPage(DatasetBundle)` → `PageResult<T>`
- `TypedQuery.filterPage(List<T>, JoinBindings)` → `PageResult<T>`
- Reuse existing `PageResult` from the `sqllike` package or extract to shared
- Run filtered-count pass (no limit/offset) + paginated-result pass, or check
  whether engine exposes total in a single pass
- Add contract tests: total reflects unfiltered count, rows respect limit/offset
- Update `docs/typed.md`

---

### WP-12 — `TimeBucket.HOUR` granularity  [P4]

**Problem:** `TimeBucket` has DAY → YEAR but no HOUR or MINUTE. Event-stream
and session workloads routinely bucket by hour. The engine `TimeBucketUtil` may
already support it.

**Work:**
- Check `TimeBucketUtil` for HOUR/MINUTE support
- If supported: add `TimeBucket.HOUR` (and `MINUTE` if available) to the enum
- Verify `TimeBucketPreset`, `QueryTimeBucket`, and `TimeBucketAggregationTest`
  handle the new granularity
- Add a contract test: bucket by HOUR groups rows into correct hour slots
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

- [x] `2026-05-18`: WP-1 — `TypedPredicate.not()` lowered via DeMorgan;
  1141 tests passed.
- [x] `2026-05-18`: WP-2 — `contains()` / `matches()` on TypedField and
  TypedPredicate; 1153 tests passed.
- [x] `2026-05-18`: WP-3 — `TypedSortOrder` + `orderBy(TypedSortOrder...)`
  vararg; 1160 tests passed.
- [x] `2026-05-18`: WP-4 — `TypedQuery.timeBucket(…)` wired to engine;
  1165 tests passed.
- [x] `2026-05-18`: WP-5 — `TypedField.between(lo, hi)` /
  `TypedPredicate.between(field, lo, hi)`; 1171 tests passed.
