# TODO

## Current Goal

Close typed-surface gaps identified in the 2026-05-18 feature audit.
Keep PojoLens focused on the Java library, benchmarks, release flow, docs,
and repo-memory helpers.

---

## Work Packages

### ~~WP-1 — Fix `TypedPredicate.not()` footgun~~ ✓ DONE 2026-05-18

**Problem:** `TypedPredicate.not()` compiles and builds a valid descriptor, but
`TypedQuery` throws `UnsupportedOperationException` at execution time
(`TypedQuery.java:898`). Silent API trap.

**Options (pick one):**
- Implement lowering via DeMorgan: flip EQ→NE, NE→EQ, GT→LTE, GTE→LT,
  LT→GTE, LTE→GT, IN→multi-NE, IS_NULL→IS_NOT_NULL, etc.
- Remove `TypedPredicate.not()` and `Operator.NOT` from the public surface
  until a full implementation is ready; update docs.

**Files:**
- `TypedPredicate.java` — `not()` / `Operator.NOT`
- `TypedQuery.java` — lowering switch ~line 898, `toDisjunctiveNormalForm`
- `TypedPredicateContractTest.java`, `TypedQueryContractTest.java`
- `docs/typed.md` — update boundaries section

---

### ~~WP-2 — `contains()` and `matches()` on typed surface~~ ✓ DONE 2026-05-18

**Problem:** Engine (`Clauses.CONTAINS`, `Clauses.MATCHES`) and SQL-like both
support string containment and regex matching; the typed surface has neither.

**Work:**
- Add `Operator.CONTAINS` and `Operator.MATCHES` to `TypedPredicate.Operator`
- Add static factories `TypedPredicate.contains(field, value)` and
  `TypedPredicate.matches(field, pattern)`
- Add instance methods `TypedField.contains(String)` and
  `TypedField.matches(String)`
- Wire into lowering switch in `TypedQuery.java` → `Clauses.CONTAINS` /
  `Clauses.MATCHES`
- Add contract tests and fluent/sql-like parity coverage
- Update `docs/typed.md`

---

### ~~WP-3 — Per-field sort direction on `TypedQuery`~~ ✓ DONE 2026-05-18

**Problem:** `orderBy(field)` forces `Sort.ASC` globally; `orderByDesc(field)`
forces `Sort.DESC` globally. Last call wins for all order fields. Cannot
express `ORDER BY salary DESC, name ASC`. `TypedWindowOrder` already models
per-field direction — sort needs the same.

**Work:**
- Introduce `TypedSortOrder` (field + direction) mirroring `TypedWindowOrder`
- Add `TypedQuery.orderBy(TypedSortOrder...)` vararg overload
- Keep existing `orderBy(field)` / `orderByDesc(field)` for single-field
  backward compat, but deprecate or document the global-direction limitation
- Update lowering in `TypedQuery.java` to pass per-field directions to the
  engine
- Add parity tests vs SQL-like `ORDER BY a DESC, b ASC`
- Update `docs/typed.md`

---

### WP-4 — Time bucket on typed surface  [P4]

**Problem:** SQL-like and natural support `bucket(dateField, 'day|week|...')`;
the typed surface has no equivalent. `QueryTimeBucket` exists internally with
no typed entry point.

**Work:**
- Add `TypedQuery.timeBucket(TypedField<T,?> dateField, TimeBucket unit,
  String alias)` fluent method
- Optionally accept a zone and week-start via overloads matching
  `TimeBucketPreset` capabilities
- Wire into `TypedQuery` builder state and lowering
- Add typed time-bucket tests and fluent/sql-like parity coverage
- Update `docs/typed.md`

---

### WP-5 — `between()` convenience on `TypedField` / `TypedPredicate`  [P5]

**Problem:** Common range check requires `field.gte(a).and(field.lte(b))`.
No first-class `BETWEEN` operator exists on any surface. Engine can express it
as two rules already.

**Work:**
- Add `TypedField.between(V lo, V hi)` returning `gte(lo).and(lte(hi))`
- Add static `TypedPredicate.between(field, lo, hi)` factory for symmetry
- Add contract tests
- Update `docs/typed.md` with a `between(...)` example

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

- [x] `2026-05-18`: Replaced stale WP roadmap with `neon` extraction cleanup
  backlog.
- [x] `2026-05-18`: Completed `neon` extraction — removed agents package, CLI
  shims, orchestrator tests, retained artifacts, and stale repo-memory
  references.
- [x] `2026-05-18`: Repaired `release-2026.05.18.1353` after Central publish
  timed out; wired wait mode through Maven properties.
- [x] `2026-05-18`: Consumer install docs now point at published release tag;
  in-repo example builds track checked-in POM version.
