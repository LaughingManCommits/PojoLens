# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Completed neon extraction, repaired release path (`release-2026.05.18.1353` backfilled on `main`), wired wait mode through Maven properties. Consumer install docs point at published release tag.
- `2026-05-18`: Closed all five typed-surface WPs (NOT lowering, contains/matches, TypedSortOrder, timeBucket, between).
- `2026-05-18`: Wild-comparison audit complete; `TODO.md` reset with WP-6 through WP-12.

## Verified
- `2026-05-18`: `mvn -B -ntp test` passes (1171 tests) after WP-1 through WP-5.
- `2026-05-18`: `scripts/docs/check-doc-consistency.ps1` passes after all WP doc updates.

## Release
- Latest published cut/tag: `2026.05.18.1353`.
- `release-central` defaults to `validated`; choose `published` to block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active risks.

## Next
- ~~WP-6~~: DONE 2026-05-18 — `count`, `exists`, `findFirst`, `findOne` on TypedQuery; 1183 tests pass.
- WP-7 (P2): Case-insensitive string matching — `containsIgnoreCase`.
- WP-8 (P2): `stream()` lazy execution on TypedQuery.
- WP-9 (P3): `TypedPredicate.any()` / `.none()` sentinels.
- WP-10 (P3): `computedFields(ComputedFieldRegistry)` on TypedQuery.
- WP-11 (P3): `filterPage()` / `PageResult<T>` on TypedQuery.
- WP-12 (P4): `TimeBucket.HOUR` granularity.
