# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed on 2026-03-13 after the first WP15 clone-reduction changes.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP15 is now in progress: `ComputedFieldSupport` and `JoinEngine` were changed to reuse existing `QueryField` objects on the common path and only allocate replacement field objects when computed outputs or renamed child collisions require them.
- Short smoke validation on 2026-03-13 improved the 1k end-to-end computed-field join path and the hotspot microbenchmark time, but allocation per op remained effectively flat.
- Remaining open backlog areas are warmed WP15 acceptance, then WP16 joined-schema reuse, WP17 residual row-model churn reduction, and WP18 benchmark rebaselining.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Rerun a warmed JFR and benchmark comparison for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` to judge WP14/WP15 together on the real hot path.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
