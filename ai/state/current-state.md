# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Checked-in project version is `2026.04.29.1809`; latest published/tagged release is `2026.05.18.1353`.

## Focus
- `2026-05-18`: The extracted local AI runtime now lives in the separate `neon` codebase.
- `2026-05-18`: `TODO.md` was reset from the stale WP roadmap to a cleanup backlog focused on removing extracted local AI assets from PojoLens.
- `2026-05-18`: Completed the extraction cleanup by removing the old Python runtime surface, retained runtime artifacts, and stale references from the surviving repo-memory scripts.
- `2026-05-18`: Repaired the release path after a successful Central publish timed out waiting for `published`; `release-2026.05.18.1353` is now backfilled on `main`, and the workflow wait mode is property-driven instead of hardcoded in the parent POM.
- `2026-05-18`: Consumer-facing install docs now point at published release `2026.05.18.1353`, while in-repo example builds continue to track the checked-in root POM version.

## Verified
- `2026-05-18`: `py -3 -m unittest scripts.tests.test_refresh_ai_memory`, `scripts/docs/check-doc-consistency.ps1`, `scripts/ai/refresh-ai-memory.ps1`, and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after the extraction cleanup and repo-memory refresh.
- `2026-05-18`: `mvn -B -ntp test` and `scripts/docs/check-doc-consistency.ps1` passed after wiring the release wait mode through Maven properties and the workflow input, and after backfilling `release-2026.05.18.1353`.
- `2026-05-18`: `scripts/docs/check-doc-consistency.ps1` and `py -3 scripts/docs/check-doc-consistency.py` passed after switching consumer install docs to the latest release-tag version and keeping local example builds on the checked-in POM version.

## Release
- Latest published cut/tag: `2026.05.18.1353`.
- `2026-05-18`: `release-central` now defaults to `validated`; choose `published` explicitly when the workflow should block for final Central publication.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active extracted-runtime risks remain in the live changelog or hot/warm repo-memory files.

## Next
- `2026-05-18`: Feature audit complete; `TODO.md` reset with WP-1 through WP-5 targeting typed-surface gaps.
- ~~WP-1~~: DONE 2026-05-18 — `TypedPredicate.not()` now lowers via DeMorgan; 1141 tests pass.
- ~~WP-2~~: DONE 2026-05-18 — `contains()` / `matches()` on TypedField and TypedPredicate; 1153 tests pass.
- ~~WP-3~~: DONE 2026-05-18 — `TypedSortOrder` + `orderBy(TypedSortOrder...)` vararg; 1160 tests pass.
- WP-4 (P4): Time bucket entry point on typed surface.
- WP-5 (P5): `between()` convenience on `TypedField` / `TypedPredicate`.
