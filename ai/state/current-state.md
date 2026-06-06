# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Completed neon extraction, repaired release path (`release-2026.05.18.1353` backfilled on `main`), and wired Central wait mode through Maven properties.
- `2026-05-18`: WP-7 through WP-11 typed-surface hardening is done.
- `2026-05-18`: Wild-comparison audit complete; source-backed feature audit in `feature-audit.md`; no P0 product blocker found.
- `2026-05-19`: WP-12 typed pagination and WP-16 natural pagination parity are done; `PageResult` now exposes `totalRows()`.
- `2026-05-19`: WP-13 hourly time buckets are done across fluent, SQL-like, natural, and typed paths.
- `2026-05-19`: Quick fixes done: README JDK 25 requirement, typed mixed-sort error text, and stale repo-memory facade/time-bucket facts.
- `2026-06-06`: WP-14 mixed-direction ORDER BY is done across fluent engine, typed lowering, and SQL-like binding.

## Verified
- `2026-05-19`: Focused Maven slice passed: `TypedQueryContractTest`, `NaturalQueryContractTest`, `SqlLikePageResultTest`, `SqlLikeParserTest`, `TimeBucketAggregationTest`, `TimeBucketUtilTest`, `StablePublicApiContractTest`, and `PublicApiEcosystemCoverageTest`.
- `2026-05-19`: `scripts/docs/check-doc-consistency.ps1` passed.
- `2026-05-19`: `mvn -B -ntp test` passed with 1231 runtime tests plus integration and benchmark module tests.
- `2026-06-06`: Focused Maven slice passed: `TypedQueryContractTest`, `SqlLikeMappingParityTest`, `SqlLikeErrorCodesContractTest`, and `StablePublicApiContractTest` (171 tests).
- `2026-06-06`: `scripts/docs/check-doc-consistency.ps1` passed.
- `2026-06-06`: `mvn -B -ntp test` passed across the full reactor: 1230 runtime tests, 5 autoconfigure tests, 4 starter tests, and 16 benchmark-module tests.

## Release
- Latest published cut/tag: `2026.05.18.1353`.
- `release-central` defaults to `validated`; choose `published` to block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active risks.

## Next
- WP-15 (P2): `ReportDefinition.typed(...)` reusable typed workflow.
- WP-17 (P3): Typed diagnostics / plan preview.
- WP-18 (P3): File loader `Reader`/`InputStream` overloads.
- WP-19 (P4): `startsWith`/`endsWith` on typed and SQL-like.
