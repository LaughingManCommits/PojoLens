# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Continue normal PojoLens work; the `neon` extraction cleanup is complete.
4. Keep the repo-memory helper scripts unless the memory workflow is being replaced too.

## Focus
- `2026-05-18`: `TODO.md` no longer tracks the stale WP roadmap; it now tracks the `neon` extraction cleanup.
- `2026-05-18`: The extracted local AI runtime, retained runtime artifacts, and stale live-history references are removed from PojoLens.
- `2026-05-18`: Active changelog and repo-memory history were scrubbed so only the current `neon` boundary remains in the live repo surface.
- `2026-05-18`: `release-2026.05.18.1353` was backfilled on `main` after Central publish succeeded but the workflow timed out before it could push the tag.
- `2026-05-18`: The parent `release-central` profile now takes `central.publish.waitUntil` and `central.publish.skipPublishing` from Maven properties, so the workflow only waits for the final `published` state when that input is explicitly selected.
- `2026-05-18`: `README.md`, `RELEASE.md`, `docs/modules.md`, and `docs/jdbc.md` now use the latest published release tag for install snippets; in-repo example builds still use the checked-in root POM version.
- `2026-05-18`: `ai/core/repo-purpose.md` still describes PojoLens as a Java library; the stale part was the old state and backlog, not the core product definition.

## Facts
- `2026-05-18`: The surviving `scripts/ai/` tools are the repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: The repo-memory refresh/query helpers no longer index or classify the removed extracted-runtime paths.
- `2026-05-18`: `scripts/tests/test_refresh_ai_memory.py` is the remaining Python test coverage for the surviving repo-memory helpers.
- `2026-05-18`: The checked-in POM version is still `2026.04.29.1809` because the failed release workflow never reached its version-bump commit step, but the latest published/tagged baseline is now `release-2026.05.18.1353`.
- `2026-05-18`: The doc-consistency scripts now validate consumer install snippets against the latest `release-*` tag instead of forcing them to match the checked-in root POM version.

## Validate
- After removal work that changes Java/runtime behavior: `mvn -B -ntp test`.
- Re-run only the Python validations that still apply to surviving repo-memory tooling.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `scripts/ai/*`, `ai/state/recent-validations.md`
