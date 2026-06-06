# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Use `TODO.md` for the active backlog.
4. Use `feature-audit.md` for broader product-surface findings.

## Focus
- `2026-05-18`: The extracted local AI runtime moved to `neon`; PojoLens now keeps only the Java library plus repo-memory helpers, and release `2026.05.18.1353` was backfilled on `main`.
- `2026-05-19`: WP-12, WP-13, and WP-16 are done; typed/natural pagination parity landed and hourly time buckets work across query surfaces.
- `2026-05-19`: Quick fixes are done for the README JDK 25 requirement, typed mixed-sort messaging, and stale repo-memory facts.
- `2026-06-06`: WP-14, WP-15, and WP-17 are done; mixed-direction ORDER BY, typed report definitions, and typed diagnostics/plan preview are now live.

## Facts
- `2026-05-18`: Surviving `scripts/ai/` tools are repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: Doc-consistency scripts validate consumer install snippets against the latest `release-*` tag while local examples stay POM-backed.
- `2026-05-18`: Feature audit found no P0 product blocker.
- `2026-06-06`: Remaining product-surface backlog is WP-18 stream loader overloads and WP-19 prefix/suffix matching.

## Validate
- After Java/runtime behavior changes: `mvn -B -ntp test`.
- After docs changes: `scripts/docs/check-doc-consistency.ps1`.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.
- `2026-06-06`: WP-17 focused validation passed with `TypedQueryContractTest`, `PublicApiEcosystemCoverageTest`, and `StablePublicApiContractTest`.
- `2026-06-06`: Full validation passed with `mvn -B -ntp test` and `scripts/docs/check-doc-consistency.ps1`; rerun AI memory refresh/check after any state edits.

## Cold Pointers
- `feature-audit.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `docs/product-surface.md`, `docs/typed.md`, `docs/sql-like.md`, `docs/natural.md`, `docs/time-buckets.md`
- `ai/core/documentation-index.md`, `ai/state/recent-validations.md`
