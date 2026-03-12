# Current State

memory-built-from-commit: 90602abe66a3355095e34b843549f6a55008f26c

As of 2026-03-12:

## Repository Health

- Verified: `mvn -q test` passes on 2026-03-12 after the latest WP5 builder changes.
- Verified: a quick non-forked JMH smoke run for `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` succeeds on 2026-03-12.
- Verified: a forked `-prof gc` run of `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` succeeded on 2026-03-12 at both `size=1000` and `size=10000`.
- Verified: a forked `-prof gc` run of `PojoLensJoinJmhBenchmark.(pojoLensJoinLeftComputedField|manualHashJoinLeftComputedField)` succeeded on 2026-03-12 at both `size=1000` and `size=10000`.
- Verified: repeated cold guardrail-style runs of `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` stayed around `57.6-57.9 ms/op` at `size=1000` and `166.9-169.8 ms/op` at `size=10000` on 2026-03-12.
- Verified: the full core benchmark suite and strict threshold checker both passed on 2026-03-12 after adding `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`, with suite scores of about `109.706 ms/op` at `size=1000` and about `160.863 ms/op` at `size=10000`.
- Verified: `scripts/check-doc-consistency.ps1` succeeds in PowerShell.
- Verified: the repository behaves as a stable Java library with strong contract-test coverage.

## Active Work Areas

The primary unfinished work is defined in `TODO.md`.

Key items:

- WP5 selective / lazy materialization is complete for its accepted scope.
- The remaining backlog has moved to post-WP5 benchmark hardening, mainly hotspot threshold follow-up and possible future tightening of the computed-field join guardrail.
- Implemented on 2026-03-12: single-join computed-field queries now retain lazy/selective parent and child materialization when the join shape is collision-free.
- Implemented on 2026-03-12: `HotspotMicroJmhBenchmark` now includes `computedFieldJoinSelectiveMaterialization`, and the hotspot suite/docs were wired to expose it.
- Implemented on 2026-03-12: `PojoLensJoinJmhBenchmark` now includes end-to-end computed-field single-join comparison coverage plus a parity test for the manual baseline.
- Implemented on 2026-03-12: `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` is now included in `scripts/benchmark-suite-main.args` with conservative thresholds of `250 ms/op` at `size=1000` and `500 ms/op` at `size=10000`.
- Measured on 2026-03-12: the first forked `-prof gc` run reported about `37.9 us/op` / `363,824 B/op` at `size=1000` and about `361.5 us/op` / `3,531,826 B/op` at `size=10000`.
- Measured on 2026-03-12: the first forked end-to-end `-prof gc` comparison reported `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `0.335 ms/op` / `2,138,298 B/op` for `size=1000` and about `3.750 ms/op` / `20,981,581 B/op` for `size=10000`.
- Measured on 2026-03-12: the same end-to-end run reported `manualHashJoinLeftComputedField` at about `0.009 ms/op` / `84,512 B/op` for `size=1000` and about `0.097 ms/op` / `927,128 B/op` for `size=10000`.
- Measured on 2026-03-12: the second forked end-to-end `-prof gc` comparison stayed at about `0.335 ms/op` / `2,138,338 B/op` for `size=1000` and about `3.589 ms/op` / `20,981,552 B/op` for `size=10000`.
- Accepted WP5 stopping point: conservative full-materialization fallback remains the intentional stable behavior for multi-join shapes, open-ended full-row outputs, explicit rule-group queries, and join shapes whose raw or computed field names collide.

## Documentation Risks

Current mismatches:

- `CONTRIBUTING.md` and `RELEASE.md` still reference benchmark version/tag examples for **1.3.0** while `pom.xml` declares **1.0.0**.
- `MIGRATION.md` and `RELEASE.md` describe SQL-like subqueries as unsupported, but code/tests indicate limited support.

The documentation check script does not detect these mismatches.

## Next Verification Opportunities

Future sessions may verify:

- whether WP14 should leave the hotspot computed-field join benchmark diagnostic-only or promote it into a stable threshold
- repeated forked `-prof gc` runs to see whether the current computed-field hotspot numbers are stable enough for a threshold
- whether the new computed-field join guardrail budgets (`250 ms/op` at `1k`, `500 ms/op` at `10k`) should be tightened after more machines and runs are sampled
- whether `CONTRIBUTING.md` and `RELEASE.md` examples should match version `1.0.0`
- whether doc-consistency tooling should be extended to detect API drift
