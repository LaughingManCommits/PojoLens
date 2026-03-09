# Performance Review And Fix Plan

## Summary

The library already caches some reflection metadata, but there are still several hot paths that rebuild execution state or allocate far more objects than necessary during a single logical query.

The highest-impact problems are:

1. Full row snapshots are deep-copied multiple times per query execution.
2. Output materialization resolves the same reflective field path twice per field.
3. The fluent API eagerly flattens all source beans into `QueryRow` and `QueryField` objects even when the query only needs a few columns.
4. Some stats/HAVING flows rebuild execution plans multiple times in one operation.

## Findings

### 1. Repeated deep-copy of query rows

Impact: high allocation pressure and avoidable CPU time.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`

Details:

- `initFilter()` snapshots the builder.
- `FilterImpl.filter()` snapshots again before execution.
- `FilterImpl.filterGroups()` also snapshots again.
- Stats paths snapshot again for aggregate/HAVING branches.
- `QuerySpec.deepCopy()` deep-copies every `QueryRow` and `QueryField`.

This means a single query can clone the full row payload two or more times before doing useful work.

### 2. Reflection path resolution repeated during result mapping

Impact: high for typed result workloads.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Details:

- `toClassList()` calls `getFieldType()` for each output field.
- `setFieldValue()` resolves the same field path again.
- `resolveFieldPath()` splits the path and rebuilds the field list every time.

So each projected field can pay the reflection-path lookup cost twice in the same operation.

### 3. Eager full flattening of source objects

Impact: high for large datasets, especially when queries touch few fields.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`

Details:

- The builder constructor immediately calls `ReflectionUtil.toDomainRows(pojos)`.
- `toDomainRows()` scans every bean and allocates:
  - a `HashMap` for collected values,
  - an `IdentityHashMap`-backed set for cycle detection,
  - one `QueryRow`,
  - one `QueryField` per queryable field.
- Join sources are flattened the same way.

This is usually a single scan, but it is a heavy eager scan with a lot of object churn.

### 4. HAVING flow rebuilds execution plan on aggregated rows

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`

Details:

- `FilterImpl` builds a plan for aggregated rows.
- `filterHavingFields()` builds another plan again internally.

This is avoidable repeated work inside a single stats operation.

### 5. UUID generation is expensive on hot paths

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Details:

- `newUUID()` uses substring, regex, parse, and replace operations per character.
- It is called for rows and rule ids.

This is more allocation-heavy than necessary.

### 6. Builder-time validation rescans materialized rows

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Details:

- `ensureNumericField()` scans rows to find a non-null sample.
- `ensureDateField()` does the same.
- Repeated metric or bucket configuration can trigger repeated linear scans.

## Recommended Fix Order

1. Remove repeated row deep-copy during execution.
2. Cache resolved reflection paths for output mapping.
3. Stop rebuilding plans inside single-operation stats/HAVING flows.
4. Reduce row/field allocation in the flattening pipeline.
5. Clean up smaller hot spots like `newUUID()` and repeated validation scans.

## Work Packages

### WP1: Remove repeated execution snapshots

Goal: a single logical query should not deep-copy all rows multiple times.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`

Tasks:

- Decide the real mutability boundary for execution.
- Keep config snapshots if needed, but stop cloning row payloads unless a stage truly mutates rows.
- Remove the redundant snapshot between `initFilter()` and `filter()/filterGroups()`.
- Rework stats branches so aggregate/HAVING paths reuse lightweight builders or immutable execution state instead of cloning full row trees.

Acceptance criteria:

- Non-join fluent queries do not deep-copy all `QueryRow` objects more than once per execution.
- Repeated calls to `filter()` on the same builder still behave correctly.
- Existing concurrency behavior tests still pass.

### WP2: Cache field path resolution for output mapping

Goal: resolve each reflective field path once and reuse it within the operation and across operations.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Add a cache keyed by `(rootClass, fieldPath)` that stores:
  - resolved `FieldPath`,
  - target leaf type,
  - optionally a setter plan.
- Update `toClassList()` to resolve the path once per field and reuse it for both type lookup and assignment.
- Reuse the same cached path for `getFieldValue()` and `setFieldValue()` where possible.

Acceptance criteria:

- `toClassList()` no longer calls path resolution twice per field.
- Nested field materialization still works.
- Existing nested path tests still pass.

### WP3: Reuse execution plans within a single stats operation

Goal: avoid rebuilding `FilterExecutionPlan` for the same schema and query shape during one run.

Scope:

- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`

Tasks:

- Add overloads so `filterHavingFields()` accepts a prepared plan.
- Reuse the already-built aggregated-row plan in HAVING and ORDER BY stages.
- Check whether non-stats grouped flows can also pass the same plan through consistently.

Acceptance criteria:

- HAVING filtering does not rebuild the plan when one is already available.
- Stats query behavior is unchanged.

### WP4: Introduce schema-level row extraction plan

Goal: stop rediscovering how to flatten beans for every row.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Build a cached schema descriptor that contains:
  - queryable field names,
  - resolved extraction paths,
  - leaf types,
  - exclusion decisions.
- Replace `collectFieldValueMap()` plus per-row `HashMap` assembly with direct extraction into ordered field slots.
- Avoid building a temporary map when the final output is already an ordered field list.

Acceptance criteria:

- Flattening a bean does not allocate an intermediate `Map<String, Object>` per row.
- Queryable field order remains stable.
- Nested object traversal and cycle detection remain correct.

### WP5: Evaluate lazy or selective materialization for fluent queries

Goal: avoid flattening data that the query will never touch.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- query execution pipeline classes under `src/main/java/laughing/man/commits/filter`

Tasks:

- Decide whether to:
  - keep `QueryRow` but materialize only referenced fields, or
  - keep source beans and use a compiled accessor plan for filtering/grouping/order stages.
- Prototype a minimal selective materialization path for:
  - filter fields,
  - distinct fields,
  - order fields,
  - group fields,
  - return fields,
  - metric fields,
  - join keys.
- Compare complexity against the current eager flattening design.

Acceptance criteria:

- Large-query benchmarks show lower allocation rate when only a subset of fields is used.
- API behavior and result ordering remain unchanged.

### WP6: Replace expensive UUID generation

Goal: reduce avoidable string churn on row/rule id creation.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Replace regex/substrings with a direct character loop, or
- replace the custom format entirely with a cheaper monotonic/request-scoped id strategy if uniqueness requirements allow it.

Acceptance criteria:

- No regex or substring calls inside the hot id generator path.
- Existing assumptions about identifier safety still hold.

### WP7: Cache field type metadata for builder validation

Goal: stop scanning rows repeatedly during metric/date validation.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Expose cached field type metadata from reflection/schema descriptors.
- Use field metadata for `ensureFieldExists()`, `ensureNumericField()`, and `ensureDateField()`.
- Fall back only when runtime-computed fields require runtime inspection.

Acceptance criteria:

- Adding multiple metrics and time buckets does not rescan all rows for each check.
- Validation errors remain correct and deterministic.

## Benchmark Plan

Use the existing JMH benchmarks to validate the changes:

- `PojoLensPipelineJmhBenchmark`
- `PojoLensJmhBenchmark`
- `PojoLensJoinJmhBenchmark`
- `StatsQueryJmhBenchmark`
- `CacheConcurrencyJmhBenchmark`

Track:

- average time,
- allocation rate if available,
- effect of cache warmup,
- differences between flat, grouped, join, and stats workloads.

## Test Plan

At minimum, run:

- existing unit and integration tests,
- nested path query tests,
- join behavior tests,
- concurrency behavior tests,
- SQL-like parity tests for any shared reflection changes,
- benchmarks before and after each major work package.

## Suggested Implementation Sequence

1. WP1
2. WP2
3. WP3
4. WP6
5. WP7
6. WP4
7. WP5

This order gets the safest high-impact wins first before attempting the larger architecture change around selective materialization.
