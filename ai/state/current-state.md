# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Repo focus reset to the Java library plus repo-memory helpers after the extracted local AI runtime moved to `neon`.
- `2026-06-06`: WP-12 to WP-17 are done (pagination parity, HOUR buckets, mixed-direction ORDER BY, typed reports, typed diagnostics).
- `2026-09-27`: WP-18 to WP-21 are done (uncommitted): stream file loaders, typed `startsWith`/`endsWith`, literal-pattern fixes, `TypedQuery.iterator(...)`.
- `2026-09-27`: WP-22/WP-23 core correctness passes are done (uncommitted); null never matches a value comparison, and date values compare exactly while text literals compare at written precision.
- `2026-09-27`: WP-24 to WP-27 are done (uncommitted): SQL-like lists, `IS [NOT] NULL`, `[NOT] BETWEEN`, `NOT`, `NOT CONTAINS/MATCHES`, `[NOT] LIKE/ILIKE`; natural `is [not] one of`, `is [not] between`, groups, `not (...)`, `does not ...`, `ignoring case`; typed text `not()`.

## Verified
- `2026-09-27`: `scripts/docs/check-doc-consistency.ps1` passed after WP-30 docs edits.
- `2026-09-27`: Full `mvn -B -ntp test` passed after WP-30: 1393 runtime, 5 autoconfigure, 4 starter, 24 benchmark-module tests.

## Release
- `2026-09-27`: Lint baseline gate (0 violations) and SpotBugs (0 bugs) pass; MIGRATION and public-api-stability docs cover WP-18 to WP-23.
- `2026-09-27`: Benchmark guardrails passed; before/after JMH vs `85b842e` shows no regressions (see `ai/state/benchmark-state.md`).
- `2026-09-27`: japicmp vs `2026.05.18.1353` passed (0 removed/incompatible) with all documented stable types included.
- Latest published cut/tag: `2026.05.18.1353`.
- `release-central` defaults to `validated`; choose `published` to block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active risks.

## Next
- `2026-09-27`: WP-28 and WP-30 done (uncommitted): `SELECT DISTINCT`, `COUNT(DISTINCT)`, MEDIAN/PERCENTILE/STDDEV/VARIANCE.
- WP-29, WP-31 to WP-33 (P2/P3): text/date functions (design note first), LAG/LEAD, joined typed validation, lazy typed stream.
- Release candidate: WP-7 to WP-30 are unreleased (P1 filtering parity complete); MIGRATION and CHANGELOG cover them.
