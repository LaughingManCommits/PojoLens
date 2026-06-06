# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Repo focus reset to the Java library plus repo-memory helpers after the extracted local AI runtime moved to `neon`; release `2026.05.18.1353` was backfilled on `main`.
- `2026-05-19`: WP-12, WP-13, and WP-16 are done; typed/natural pagination parity landed and hourly time buckets work across fluent, SQL-like, natural, and typed paths.
- `2026-05-19`: Quick fixes are done for the README JDK 25 requirement, typed mixed-sort messaging, and stale repo-memory facts.
- `2026-06-06`: WP-14, WP-15, and WP-17 are done; mixed-direction ORDER BY, typed report definitions, and typed diagnostics/plan preview are now live.

## Verified
- `2026-06-06`: Focused WP-17 slice passed: `TypedQueryContractTest`, `PublicApiEcosystemCoverageTest`, and `StablePublicApiContractTest` (182 tests).
- `2026-06-06`: `scripts/docs/check-doc-consistency.ps1` passed.
- `2026-06-06`: Full `mvn -B -ntp test` passed across the reactor: 1238 runtime tests, 5 autoconfigure tests, 4 starter tests, and 16 benchmark-module tests.

## Release
- Latest published cut/tag: `2026.05.18.1353`.
- `release-central` defaults to `validated`; choose `published` to block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active risks.

## Next
- WP-18 (P3): File loader `Reader`/`InputStream` overloads.
- WP-19 (P4): `startsWith`/`endsWith` on typed and SQL-like.
