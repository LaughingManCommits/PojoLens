# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Repo focus reset to the Java library plus repo-memory helpers after the extracted local AI runtime moved to `neon`.
- `2026-06-06`: WP-12 to WP-17 are done (pagination parity, HOUR buckets, mixed-direction ORDER BY, typed reports, typed diagnostics).
- `2026-09-28`: WP-18 to WP-28 and WP-30 are committed in `53c6453` (stream loaders, typed text predicates, null/date correctness, SQL-like/natural filtering parity, DISTINCT, statistical aggregates).
- `2026-09-28`: WP-29 is done (uncommitted, 4 slices; `docs/design/wp-29-expression-functions.md`): text/null/date-part functions, typed validation, non-numeric computed fields, and expressions in GROUP BY/ORDER BY.

## Verified
- `2026-09-28`: WP-29 (all slices): full `mvn -B -ntp test` passed (1463 runtime, 5 autoconfigure, 4 starter, 26 benchmark-module tests); doc check, lint gate (0), SpotBugs (0), and japicmp vs `2026.05.18.1353` passed.

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
- WP-31 to WP-33 (P2/P3): LAG/LEAD, joined typed validation, lazy typed stream.
- Release candidate: WP-7 to WP-30 (incl. WP-29) are unreleased; MIGRATION and CHANGELOG cover them.
