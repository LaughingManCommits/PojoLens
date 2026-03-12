# Current State

memory-built-from-commit: 90602abe66a3355095e34b843549f6a55008f26c

As of 2026-03-12:

## Repository Health

- Verified: `mvn -q test` passes on 2026-03-12 after the latest WP5 builder changes.
- Verified: a quick non-forked JMH smoke run for `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` succeeds on 2026-03-12.
- Verified: a forked `-prof gc` run of `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` succeeded on 2026-03-12 at both `size=1000` and `size=10000`.
- Verified: `scripts/check-doc-consistency.ps1` succeeds in PowerShell.
- Verified: the repository behaves as a stable Java library with strong contract-test coverage.

## Active Work Areas

The primary unfinished work is defined in `TODO.md`.

Key items:

- WP5 selective / lazy materialization remains open mainly for benchmark capture and threshold follow-up.
- Implemented on 2026-03-12: single-join computed-field queries now retain lazy/selective parent and child materialization when the join shape is collision-free.
- Implemented on 2026-03-12: `HotspotMicroJmhBenchmark` now includes `computedFieldJoinSelectiveMaterialization`, and the hotspot suite/docs were wired to expose it.
- Measured on 2026-03-12: the first forked `-prof gc` run reported about `37.9 us/op` / `363,824 B/op` at `size=1000` and about `361.5 us/op` / `3,531,826 B/op` at `size=10000`.
- Conservative full-materialization fallback still remains for multi-join shapes, open-ended full-row outputs, explicit rule-group queries, and join shapes whose raw or computed field names collide.

## Documentation Risks

Current mismatches:

- `CONTRIBUTING.md` and `RELEASE.md` still reference benchmark version/tag examples for **1.3.0** while `pom.xml` declares **1.0.0**.
- `MIGRATION.md` and `RELEASE.md` describe SQL-like subqueries as unsupported, but code/tests indicate limited support.

The documentation check script does not detect these mismatches.

## Next Verification Opportunities

Future sessions may verify:

- repeated forked `-prof gc` runs to see whether the current computed-field hotspot numbers are stable enough for a threshold
- whether the new computed-field hotspot deserves a threshold or should remain an explicit non-threshold diagnostic
- whether `CONTRIBUTING.md` and `RELEASE.md` examples should match version `1.0.0`
- whether doc-consistency tooling should be extended to detect API drift
