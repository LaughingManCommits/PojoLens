# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: Completed neon extraction, repaired release path (`release-2026.05.18.1353` backfilled on `main`), wired wait mode through Maven properties. Consumer install docs point at published release tag.
- `2026-05-18`: Closed all five typed-surface WPs (NOT lowering, contains/matches, TypedSortOrder, timeBucket, between).
- `2026-05-18`: Wild-comparison audit complete; source-backed feature audit in `feature-audit.md`; no P0 product blocker found.
- `2026-05-18`: `TODO.md` reset with WP-7 through WP-18 plus quick fixes from both audits; WP-6 done.

## Verified
- `2026-05-18`: `mvn -B -ntp test` passes (1197 tests) after WP-1 through WP-8.
- `2026-05-18`: `scripts/docs/check-doc-consistency.ps1` passes after all WP doc updates.
- `2026-05-18`: all three scripts (`check-doc-consistency.ps1`, `refresh-ai-memory.ps1`, `refresh-ai-memory.ps1 -Check`) pass after WP-7.

## Release
- Latest published cut/tag: `2026.05.18.1353`.
- `release-central` defaults to `validated`; choose `published` to block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active risks.

## Next
- Quick fix: README JDK requirement (17+ → Java 25).
- Quick fix: mixed-sort error text in `TypedQuery.resolveGlobalSort()`.
- Quick fix: repo-memory drift in `ai/core/module-index.md`, `ai/core/architecture-map.md`, `ai/core/system-boundaries.md`.
- ~~WP-7~~: DONE 2026-05-18 — `containsIgnoreCase` on TypedField/TypedPredicate; lowers to MATCHES(?i); 1191 tests pass.
- ~~WP-8~~: DONE 2026-05-18 — `stream()` overloads on TypedQuery (4 overloads, wraps filter); 1197 tests pass.
- WP-9 (P2): `TypedPredicate.any()` / `.none()` sentinels.
- WP-10 (P2): `computedFields(ComputedFieldRegistry)` on TypedQuery.
- WP-11 (P2): `filterPage()` / `PageResult<T>` on TypedQuery + assess NaturalQuery parity.
- WP-12 (P2): `TimeBucket.HOUR` granularity.
- WP-13 (P2): Mixed-direction sort — engine-level per-field direction.
- WP-14 (P2): `ReportDefinition.typed(...)` reusable typed workflow.
- WP-15 (P2): `NaturalQuery.filterPage(...)` pagination parity.
- WP-16 (P3): Typed diagnostics / plan preview.
- WP-17 (P3): File loader `Reader`/`InputStream` overloads.
- WP-18 (P4): `startsWith`/`endsWith` on typed and SQL-like.
