# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed on 2026-03-13 after the latest WP15 computed-field materialization changes.
- Warmed benchmark reruns were captured on 2026-03-13 for the computed-field join path; the post-change warmed JFR still needs to be rerun.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 is still in progress but materially improved again: the warmed join benchmark now measures about `2.605 ms/op` at `size=10000` versus the earlier `3.029 ms/op`, after replacing per-row computed-field maps/upsert scans with a schema-aware plan and reusing joined `QueryRow` objects on the final materialization step.
- WP16 is still not fully accepted: the manual warmed baseline remains about `0.108 ms/op`, and the latest implementation pass did not yet rerun a warmed JFR to confirm whether the remaining `ReflectionUtil.collectQueryRowFieldTypes` samples have actually dropped out.
- WP17 still looks like the most likely next implementation target if the post-change JFR confirms the remaining gap has shifted into `ReflectionUtil.extractQueryFields`, `ReflectionUtil.toDomainRows`, `FilterCore.filterFields`, and `FilterCore.filterDisplayFields`.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Rerun a warmed JFR on the new `~2.605 ms/op` computed-field join baseline to confirm whether `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes` have materially dropped.
- If the new JFR shifts the hot path away from computed-field materialization, move directly into WP17 work on `ReflectionUtil` / `FilterCore` row-model churn.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
