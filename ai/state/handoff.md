# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Use `TODO.md` for the active backlog.
4. Use `feature-audit.md` for broader product-surface findings.

## Focus
- `2026-05-18`: The extracted local AI runtime moved to `neon`; PojoLens now keeps only the Java library plus repo-memory helpers.
- `2026-05-18`: Release `2026.05.18.1353` was backfilled on `main`; Central wait mode is now selected by workflow input.
- `2026-05-18`: Consumer install docs point at `2026.05.18.1353`; example builds still track checked-in root POM version `2026.04.29.1809`.
- `2026-05-18`: WP-7 through WP-11 are DONE; WP-11 hardening fixed typed computed-field ordering and sentinel static factory laws.
- `2026-05-19`: WP-12 and WP-16 are DONE; typed and natural queries expose `filterPage(...)`, and `PageResult` has `totalRows()`.
- `2026-05-19`: WP-13 is DONE; `TimeBucket.HOUR` and `TimeBucketPreset.hour()` work across fluent, SQL-like, natural, and typed paths.
- `2026-05-19`: Quick fixes are DONE for README JDK 25, typed mixed-sort error text, and stale repo-memory facade/time-bucket facts.

## Facts
- `2026-05-18`: Surviving `scripts/ai/` tools are repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: Doc-consistency scripts validate consumer install snippets against the latest `release-*` tag while local examples stay POM-backed.
- `2026-05-18`: Feature audit found no P0 product blocker.
- `2026-05-19`: Remaining product-surface backlog is WP-14 mixed-direction sorting, WP-15 typed report definitions, WP-17 typed diagnostics, WP-18 stream loader overloads, and WP-19 prefix/suffix matching.

## Validate
- After Java/runtime behavior changes: `mvn -B -ntp test`.
- After docs changes: `scripts/docs/check-doc-consistency.ps1`.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `feature-audit.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `docs/product-surface.md`, `docs/typed.md`, `docs/sql-like.md`, `docs/natural.md`, `docs/time-buckets.md`
- `ai/core/documentation-index.md`, `ai/state/recent-validations.md`
