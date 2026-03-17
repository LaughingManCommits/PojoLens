# Performance Backlog

Last updated: 2026-03-17. Benchmarks from full sweep at that date.

---

## WP19 — Reflection/conversion allocation (PARKED — needs new hypothesis)

**Problem:** `reflectionToDomainRows|size=10000` allocates 2,840,026 B/op and `reflectionToClassList|size=10000` allocates 1,400,236 B/op. Latency has improved (826/413 us/op vs 1115/557 us/op baseline) but allocation is flat across all recorded snapshots.

**Warmed JFR** (`target/wp19-current-2026-03-16.jfr`) shows first-repo-frame CPU centered in:
- `ReflectionUtil$ResolvedFieldPath.read` (837)
- `FastArrayQuerySupport.applyComputedValues` (399)
- `ReflectionUtil.applyProjectionWritePlan` (270)

And allocation centered in:
- `ReflectionUtil$ResolvedFieldPath.read` (4220)
- `FastArrayQuerySupport.materializeJoinedRow` (3684)
- `FastArrayQuerySupport.buildChildIndex` (3117)

**Guardrails:** Do not reopen without a structurally different hypothesis. Two spikes already reverted:
1. Prefiltered fast-state during `tryBuildJoinedState()` — regressed warmed target from 0.584 to 0.700 ms/op
2. Deferred materialization / projected-output path — allocation improved but latency regressed to 0.712 ms/op at size=10000

---

## WP21 — Computed-field join allocation (diagnostic-only)

**Problem:** `computedFieldJoinSelectiveMaterialization|size=10000` measures 330.570 us/op / 3,532,314 B/op. Largest absolute `B/op` in the hotspot suite. `pojoLensJoinLeftComputedField|size=10000` warmed at ~0.603 ms/op vs manual at ~0.098 ms/op.

**Hotspot cluster** is the same as WP19 — joined-row materialization and child index building dominate.

**Policy:** Keep `pojoLensJoinLeftComputedField` thresholds at 25/200 ms/op (cold). Hotspot stays diagnostic-only until allocation moves materially. Refresh warmed JFR before any new structural attempt.

---

## WP22 — Cold filter pipeline overhead (FAST PATH LANDED)

**Status:** `FastPojoFilterSupport` fast path now lives in `FilterImpl.filterRows()`. For POJO-source simple filter queries (no joins, stats, computed fields, or explicit rule groups), filter rules are evaluated directly against POJO objects using the cached `FlatRowReadPlan`, and only matching rows are materialized as `QueryRow`. This reduces O(n) materialization to O(matched) allocations.

**Root cause (from investigation):** The dominant cold cost was `ReflectionUtil.toDomainRows` creating 10,000 QueryRow + ArrayList + QueryField objects for all input beans before any filtering. With the fast path, a reused `Object[]` buffer is filled per bean and only matching beans become QueryRow objects.

**Benchmark results after fast path (2026-03-17):**
- Cold guardrail (`-wi 0 -i 1`): 6.093 / 115.338 ms/op — within normal drift from 3.523 / 113.933 baseline; 42/42 thresholds pass.
- Warmed (`-wi 2 -i 3 -prof gc`): 0.082 ms/op / 71,648 B/op at size=1000 and 0.751 ms/op / 557,211 B/op at size=10000.

**Finding:** Cold cost (~115ms at size=10000) is dominated by JVM JIT compilation overhead on the first call, not by O(n) POJO materialization. The fast path reduces warmed allocation (fewer QueryRow/QueryField objects for non-matching rows) but cannot reduce first-call JIT compilation cost. No further WP22 work planned.

---

## WP23 — Stats plan build concurrency throughput (PARKED — inherent O(n) workload)

**Investigation finding (2026-03-17):**
- The 173 ops/s cold number is JVM startup / JIT compilation noise, not a real throughput ceiling.
- Warmed (`-wi 3 -i 3 -r 250ms`) measures ~18,834 ops/s at 8 threads — 109x better.
- The remaining warmed gap vs `sqlLikeParse` (439M ops/s) is fundamental: `statsPlanBuild` does O(n=20K) reflection reads per call; `sqlLikeParse` returns a cached plan in O(1).
- No synchronization bottleneck found; Caffeine cache uses fine-grained segment locks, plan cache hits are lock-free.
- **Map over-allocation fixed**: `aggregateSingleGroup` and `aggregateGrouped` now cap initial `LinkedHashMap` capacity at `Math.min(source.size(), 1024)` to avoid pre-allocating ~214KB for typical low-cardinality group queries. Cold score unchanged (177 ops/s, within drift).

**Policy:** Do not reopen unless a warmed profile shows lock contention or a structural O(n) reduction is found.

---

## Notes

- WP17 (selective single-join fast-path) and WP18 (chart/scatter mapping) are parked — reopen only with fresh profile evidence of a specific remaining bottleneck.
- Do not merge specialized bean, `QueryRow`, or `Object[]` execution loops for consolidation; hot-path specialization is intentional.
- Consolidation thread is largely exhausted after 8 passes; revisit only after the hot paths change.
