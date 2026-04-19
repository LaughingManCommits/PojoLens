# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: `QOL-WP4` page result helper or next release.

## Focus

- Java 25 CI, DOC-WP1 through DOC-WP10, `PojoLensTree`, and CI warnings are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `SURFACE-WP1` through `SURFACE-WP6` are complete except the release cut.
- SQL-like is public default; natural is guided text; fluent is internal engine DSL.
- Active backlog is `QOL-WP1` through `QOL-WP5` plus release.
- `QOL-WP1` query diagnostics is implemented and review-hardened.
- `QOL-WP2` query exposure policy is implemented.
- `QOL-WP3` SQL-like plan preview is implemented and review-hardened.

## Facts

- `2026-04-18`: No public users; SQL-like primary, natural guided, fluent internal. `PojoLensCore` and `PojoLensRuntime.newQueryBuilder(...)` removed from public surface.
- `2026-04-18`: Mutable fluent planning types under `laughing.man.commits.internal.builder`; public-surface guards reject internal APIs.
- `2026-04-19`: `docs/internal-fluent-engine.md` is the maintainer reference for fluent lifecycle, method groups, and usage examples.
- `2026-04-19`: `TODO.md` now tracks QoL packages: diagnostics, exposure, preview, page result, and suggestions.
- `2026-04-19`: Query diagnostics reports params, fields, sources, lint, multiple unknown `WHERE` fields, and natural diagnostics after vocabulary resolution.
- `2026-04-19`: `QueryExposurePolicy` allowlists fields/sources for SQL-like, natural lowering, and runtime defaults.
- `2026-04-19`: `SqlLikePlanPreview` exposes AST-only execution shape from `SqlLikeQuery.planPreview()`, including grouped predicates through `PlanPreviewPredicate` and nested subqueries through `PlanPreviewFilter.subqueryPreview()`.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with optional `TreeEntry<T>` metadata.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and external authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then benchmark guardrails when performance changes.
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-19` QOL-WP3 hardening passed 28 preview tests, 799-test Maven suite, doc consistency, and diff check.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
