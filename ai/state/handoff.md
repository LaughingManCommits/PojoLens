# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: `QOL-WP5` better error suggestions or next release.

## Focus

- Java 25 CI, DOC-WP1 through DOC-WP10, `PojoLensTree`, and CI warnings are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `SURFACE-WP1` through `SURFACE-WP6` are complete except the release cut.
- SQL-like is public default; natural is guided text; fluent is internal engine DSL.
- Active backlog is `QOL-WP5` plus release (`QOL-WP1`–`WP4` complete).

## Facts

- `2026-04-18`: No public users; SQL-like primary, natural guided, fluent internal. `PojoLensCore` removed from public surface.
- `2026-04-18`: Fluent planning types under `laughing.man.commits.internal.builder`; public-surface guards reject internal APIs.
- `2026-04-19`: `docs/internal-fluent-engine.md` is the maintainer reference for fluent lifecycle and usage.
- `2026-04-19`: `QueryDiagnostics`: params, fields, sources, lint, multi-unknown WHERE, natural vocabulary resolution.
- `2026-04-19`: `QueryExposurePolicy` allowlists fields/sources for SQL-like, natural lowering, and runtime defaults.
- `2026-04-19`: `SqlLikePlanPreview` from `planPreview()`: grouped predicates via `PlanPreviewPredicate`, nested subquery via `subqueryPreview()`.
- `2026-04-20`: `PageResult<T>` from `filterPage(...)`: rows, hasMore, nextCursor; requires positive static LIMIT + ORDER BY, rejects OFFSET, uses limit+1 lookahead.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with `TreeEntry<T>` metadata.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and external authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then benchmark guardrails when performance changes.
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-20` QOL-WP4 review hardening passed targeted page/API tests, full Maven suite, doc consistency, diff check, and memory refresh/check.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
