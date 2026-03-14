# Handoff

## Next Work Tasks

- The current warmed baseline after the newest landed WP17 change is about `0.654 ms/op` at `size=10000` versus a current manual baseline rerun around `0.111 ms/op`.
- The current allocation-focused rerun is about `0.632 ms/op` / `2,307,857 B/op`, down materially from the prior local pre-change rerun around `3,107,785.502 B/op`.
- A broader 2026-03-14 benchmark sweep is summarized in `BENCHMARKS.md` and `ai/state/benchmark-state.md`: all core/chart thresholds passed, but chart parity failed in `5/15` fluent-vs-SQL-like comparisons and the hotspot suite still points to computed join / reflection conversion allocation pressure.
- The benchmark memory now also records the recurring warmed JFR hotspot clusters in `BENCHMARKS.md` and `ai/core/benchmark-context.md`; the common class-level stress is concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.
- The 2026-03-14 change reused one parent read buffer across `tryBuildJoinedState()` and changed `buildChildIndex()` to store single-child matches directly in the hash index, only promoting to `List<Object[]>` on actual fan-out.
- The current warmed JFR artifact is `target/wp17-after-parent-buffer.jfr`.
- Follow the refreshed `TODO.md` order: finish the class-level WP17 work on `ReflectionUtil` and `FastArrayQuerySupport`, then address chart parity, then the broader reflection/conversion hotspot suite, and rebaseline budgets last.
- Prefer a deeper internal redesign over compatibility-preserving tweaks: compiled accessor chains or method handles, narrower predicate plans, and cheaper projection materialization are all on the table.
- The first speculative WP17 code experiments in this session regressed the warmed benchmark and were reverted before the current benchmark-backed pass landed; keep driving changes from fresh profiles and rebuilt benchmark jars.
- The matcher/read-path/parent-buffer changes that did land are in `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java` and `src/main/java/laughing/man/commits/util/ReflectionUtil.java`.
- The newest warmed profile is `target/wp17-after-parent-buffer.jfr`. Its current first-repo-frame CPU hotspots are `ReflectionUtil$ResolvedFieldPath.read` (`925`), `FastArrayQuerySupport$ComputedFieldPlan.resolveValue` (`331`), `FastArrayQuerySupport.tryBuildJoinedState` (`246`), `FastArrayQuerySupport.buildChildIndex` (`193`), and `FastArrayQuerySupport.filterRows` (`122`).
- The newest warmed profile's first-repo-frame allocation hotspots are `FastArrayQuerySupport.buildChildIndex` (`5353`), `ReflectionUtil$ResolvedFieldPath.read` (`3878`), `FastArrayQuerySupport.materializeJoinedRow` (`3491`), `FastArrayQuerySupport.castNumericValue` (`2132`), and `ReflectionUtil.readFlatRowValues` (`697`).
- The next WP17 change should target residual reflection reads or computed dependency lookup before reopening broad projection or source-copy work.
- If the task is about broader profiling rather than immediate code changes, load `ai/core/benchmark-context.md` for the cross-JFR hotspot clusters before choosing the next package.
- Keep process docs aligned if benchmark commands, release steps, or SQL-like capability boundaries change again.

## Relevant Files

- `TODO.md`
- `docs/benchmarking.md`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `ai/core/benchmark-context.md`
- `benchmarks/thresholds.json`
- `scripts/benchmark-suite-main.args`
- `scripts/benchmark-suite-hotspots.args`
- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java`
- `target/pojolens-fastpath-current.jfr`
- `target/wp17-after-readpath.jfr`
- `target/wp17-after-parent-buffer.jfr`
- `src/test/java/laughing/man/commits/filter/FilterImplFastPathTest.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`

## Unresolved Questions

- Should broader multi-join, collision-heavy, or grouped flows get their own array-based path, or should the legacy `QueryRow` engine remain the long-tail fallback?
- Is the next best gain in compiled field access for `ReflectionUtil$ResolvedFieldPath.read`, or in reducing computed dependency lookup inside `FastArrayQuerySupport$ComputedFieldPlan.resolveValue()`?
- Should projection stay on no-arg construction plus setter writes, or move to a cheaper constructor or record-style path for common benchmark projections?
- Can `FilterQueryBuilder.copySourceBeans()` be bypassed safely on the fast path now that internal compatibility constraints are relaxed?
