# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed on 2026-03-13 after the WP15/WP16 code changes.
- A warmed JFR plus warmed benchmark comparison was captured on 2026-03-13 for the computed-field join path.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 is still in progress: the warmed join benchmark improved to about `3.029 ms/op` at `size=10000` from the earlier `~5.505 ms/op`, but `ComputedFieldSupport.materializeRow` remains the hottest repository frame and `JoinEngine.mergeFields` is still sampled.
- WP16 is still in progress: the manual warmed baseline improved to about `0.108 ms/op`, but `ReflectionUtil.collectQueryRowFieldTypes` still appears heavily in the warmed end-to-end profile, so the derived-schema fast path is not yet complete in practice.
- WP17 is now the clearest next implementation target because warmed allocation samples still cluster around `ReflectionUtil.extractQueryFields`, `ReflectionUtil.toDomainRows`, `FilterCore.filterFields`, and `FilterCore.filterDisplayFields`.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- After the next implementation pass, rerun warmed JFR plus warmed `-prof gc` benchmarks for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`.
- Add or extend regression coverage for the remaining WP16 call path that still reaches `ReflectionUtil.collectQueryRowFieldTypes`.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
