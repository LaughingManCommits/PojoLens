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

## WP22 — Cold filter pipeline overhead

**Problem:** Cold `fluentFilterProjection|size=10000` comes in at 120.522 ms/op vs 0.124 ms/op for the Streams baseline (972x ratio). Legacy `pojoLensFilter|size=10000` at 118.385 ms/op vs 0.015 ms/op manual (7892x). These numbers reflect real cold-path setup cost, not just JMH overhead.

**Candidates to investigate:**
- Rule compilation and field-index resolution on first call
- `FilterExecutionPlan` construction cost (schema derivation, rule compile)
- Whether a lighter-weight warm-up / plan-reuse path exists for the filter-only (no group/join) shape

**Gate:** Use `fullFilterPipeline|size=10000` (current 113.933 ms/op vs 750 ms budget) as the regression gate. Winning means moving that number, not the streams ratio.

---

## WP23 — Stats plan build concurrency throughput

**Problem:** `statsPlanBuildHotSetConcurrent` measures 173.254 ops/s at 8 threads — a known sharp split from the parse-cache path (439M ops/s). Any concurrent query that requires plan building hits this ceiling.

**Candidates to investigate:**
- Lock contention or synchronization in stats plan construction
- Whether partial plan reuse or a finer-grained lock scope is viable

**Gate:** Run `CacheConcurrencyJmhBenchmark` before and after any change.

---

## Notes

- WP17 (selective single-join fast-path) and WP18 (chart/scatter mapping) are parked — reopen only with fresh profile evidence of a specific remaining bottleneck.
- Do not merge specialized bean, `QueryRow`, or `Object[]` execution loops for consolidation; hot-path specialization is intentional.
- Consolidation thread is largely exhausted after 8 passes; revisit only after the hot paths change.
