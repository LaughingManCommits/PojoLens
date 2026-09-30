# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Use `TODO.md` for the active backlog.
4. Use `feature-audit.md` for broader product-surface findings.

## Focus
- `2026-05-18`: The extracted local AI runtime moved to `neon`; PojoLens now keeps only the Java library plus repo-memory helpers, and release `2026.05.18.1353` was backfilled on `main`.
- `2026-06-06`: WP-12 to WP-17 and the README/mixed-sort/memory quick fixes are done.
- `2026-09-28`: WP-29 (all 4 slices) is done but uncommitted; user reviews and commits.

## Facts
- `2026-05-18`: Surviving `scripts/ai/` tools are repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: Doc-consistency scripts validate consumer install snippets against the latest `release-*` tag while local examples stay POM-backed.
- `2026-05-18`: Feature audit found no P0 product blocker.
- `2026-09-27`: `feature-audit.md` was refreshed from `docs/`; its findings are `TODO.md` WP-24 to WP-33 (SQL-like/natural filtering parity first).
- `2026-09-27`: Single owners for comparison, precision, join/group keys, keysets, records, and loaders are listed in `ai/core/architecture-map.md`.
- `2026-09-27`: SQL-like `LIKE`/`ILIKE`/`ESCAPE` (WP-27) are contextual words, not reserved; `:pattern` params lower at bind time via `PatternParameterValue` + `internal.TextPattern`.
- `2026-09-28`: Expressions stay strings in the AST; `SqlExpressionEvaluator` owns parse/type/eval/coerce (`ExpressionNode`, `ExpressionFunction` table, `ExpressionTypes`), and `SqlLikeValidator.validateForExecution` returns computed SELECT types for projection.

## Validate
- After Java/runtime behavior changes: `mvn -B -ntp test`.
- After docs changes: `scripts/docs/check-doc-consistency.ps1`.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.
- SpotBugs: `mvn -B -ntp -pl pojo-lens -Pstatic-analysis verify -DskipTests`; japicmp: `mvn -B -ntp -pl pojo-lens -Pbinary-compat -DskipTests -Dcompat.baseline.version=2026.05.18.1353 verify`.

## Cold Pointers
- `feature-audit.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `docs/product-surface.md`, `docs/typed.md`, `docs/sql-like.md`, `docs/natural.md`, `docs/time-buckets.md`
- `ai/core/documentation-index.md`, `ai/state/recent-validations.md`
