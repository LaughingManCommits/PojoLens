# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Use `TODO.md` for the active typed-surface backlog.
4. Use `feature-audit.md` for broader product-surface findings.

## Focus
- `2026-05-18`: The extracted local AI runtime moved to `neon`; PojoLens now keeps only the Java library plus repo-memory helpers.
- `2026-05-18`: Release `2026.05.18.1353` was backfilled on `main`; Central wait mode is now selected by workflow input.
- `2026-05-18`: Consumer install docs point at `2026.05.18.1353`; example builds still track checked-in root POM version `2026.04.29.1809`.
- `2026-05-18`: WP-7 through WP-10 are DONE (containsIgnoreCase, stream(), any()/none() sentinels, computedFields); 1223 tests pass after WP-11 hardening.
- `2026-05-18`: WP-11 hardening DONE: computedFields ordering fixed, allOf/anyOf sentinel laws, metric/HAVING computed-field tests, StablePublicApiContractTest updated.
- `2026-05-18`: `TODO.md` tracks WP-12 (filterPage) through WP-19; WP-12 is P2 next priority.
- `2026-05-18`: `feature-audit.md` records the current product-surface audit and recommended roadmap.

## Facts
- `2026-05-18`: Surviving `scripts/ai/` tools are repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: Doc-consistency scripts validate consumer install snippets against the latest `release-*` tag while local examples stay POM-backed.
- `2026-05-18`: Feature audit found no P0 product blocker.
- `2026-05-18`: Feature audit P1/P2 gaps are README Java requirement drift, typed parity APIs, case-insensitive matching, hour buckets, mixed-direction sorting, typed reusable reports, and natural pagination parity.
- `2026-05-18`: AI memory still needs stale-fact cleanup for removed `PojoLens` facade references and old Date-only time-bucket guidance.

## Validate
- After Java/runtime behavior changes: `mvn -B -ntp test`.
- After docs changes: `scripts/docs/check-doc-consistency.ps1`.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `feature-audit.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `docs/product-surface.md`, `docs/typed.md`, `docs/sql-like.md`, `docs/natural.md`
- `ai/core/documentation-index.md`, `ai/state/recent-validations.md`
