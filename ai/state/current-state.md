# Current State

memory-built-from-commit: 0ecce5d9ea14e2821e552c5511b366ff81fc86c3

As of 2026-03-12:

## Repository Health

- Verified: `mvn -q test` passes on 2026-03-12 after the latest WP5 builder changes.
- Verified: `scripts/check-doc-consistency.ps1` succeeds in PowerShell.
- Verified: the repository behaves as a stable Java library with strong contract-test coverage.

## Active Work Areas

The primary unfinished work is defined in `TODO.md`.

Key items:

- WP5 selective / lazy materialization remains open mainly for benchmark capture and threshold follow-up.
- Implemented on 2026-03-12: single-join computed-field queries now retain lazy/selective parent and child materialization when the join shape is collision-free.
- Conservative full-materialization fallback still remains for multi-join shapes, open-ended full-row outputs, explicit rule-group queries, and join shapes whose raw or computed field names collide.

## Documentation Risks

Current mismatches:

- Benchmark documentation references version **1.3.0** while `pom.xml` declares **1.0.0**.
- `MIGRATION.md` and `RELEASE.md` describe SQL-like subqueries as unsupported, but code/tests indicate limited support.

The documentation check script does not detect these mismatches.

## Next Verification Opportunities

Future sessions may verify:

- benchmark performance after the widened WP5 computed-field join path
- whether documentation examples should match version `1.0.0`
- whether doc-consistency tooling should be extended to detect API drift
