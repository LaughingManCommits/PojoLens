# Current State

memory-built-from-commit: e27ca800d83924e8232cfb0240715ef705c4c5c7

As of 2026-03-12:

## Repository Health

- Verified: `mvn -q test` passes on 2026-03-12 after the WP14 expression-compilation changes, with `406` tests discovered in Surefire reports.
- Verified: `scripts/check-doc-consistency.ps1` had already passed earlier on 2026-03-12 and was not impacted by WP14.
- Verified: the repository still behaves as a stable Java library with strong contract-test coverage.

## Active Work Areas

- WP5 selective / lazy materialization remains closed at the accepted selective single-join scope boundary.
- WP14 is now in progress in `TODO.md`.
- Implemented on 2026-03-12: `SqlExpressionEvaluator` now compiles numeric expressions into reusable cached ASTs keyed by expression string instead of instantiating a fresh parser for each row evaluation.
- Implemented on 2026-03-12: `ComputedFieldSupport.materializeRows()` now compiles the applicable computed-field definitions once per materialization call and reuses those compiled expressions for every row.
- Implemented on 2026-03-12: added `SqlExpressionEvaluatorTest` plus dependent computed-field integer-coercion coverage in `ComputedFieldRegistryTest`.
- Remaining open packages after the WP14 start are WP15 row/field cloning removal, WP16 joined-schema reuse, WP17 residual row-model churn reduction, and WP18 benchmark rebaselining.

## Latest Measurements

- Historical warm baseline before WP14: `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` averaged about `5.505 ms/op` at `size=10000` with `-wi 5 -i 10 -r 300ms`; the matching manual baseline averaged about `0.148 ms/op`.
- Historical warm JFR before WP14 showed heavy allocation churn and prominent `SqlExpressionEvaluator$Parser.*`, `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes` hot spots.
- Post-change short smoke on 2026-03-12: `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` ran at about `0.464 ms/op` for `size=1000` and about `6.184 ms/op` for `size=10000` using `-wi 1 -i 2 -r 100ms`.
- Post-change short smoke on 2026-03-12: `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` ran at about `0.012 ms/op` for `size=1000` with the same short configuration.
- Post-change allocation smoke on 2026-03-12: `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` at `size=1000` measured about `60.147 us/op` and `363,856 B/op` with `-wi 1 -i 2 -r 100ms -prof gc`.
- Interpretation: WP14 correctness is verified, but the package is not benchmark-accepted yet because no comparable warmed profile/JFR has been rerun and the short hotspot smoke did not materially change the allocation-heavy signature.

## Documentation Risks

- `CONTRIBUTING.md` and `RELEASE.md` still reference benchmark version/tag examples for `1.3.0` while `pom.xml` declares `1.0.0`.
- `MIGRATION.md` and `RELEASE.md` still describe SQL-like subqueries more narrowly than the code/tests now support.
- The doc-consistency script does not currently detect those mismatches.

## Next Verification Opportunities

- rerun a comparable warmed JFR for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000` to confirm the old parser hot spots disappeared after WP14
- verify whether WP14 changed end-to-end warm throughput in a statistically meaningful way instead of the short `-wi 1 -i 2 -r 100ms` smoke runs
- move to WP15 and remove `QueryRow` / `QueryField` cloning from computed and join materialization
- move to WP16 and eliminate the post-join `collectQueryRowFieldTypes(rows)` rescan
- revisit `CONTRIBUTING.md`, `RELEASE.md`, and doc-consistency tooling when performance work settles
