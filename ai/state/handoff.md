# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: `QOL-WP2` field/source exposure policy or the next date-based release.

## Focus

- Java 25 CI, DOC-WP1 through DOC-WP10, `PojoLensTree`, CI warnings, and Checkstyle baseline are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `SURFACE-WP1` through `SURFACE-WP6` are complete except the release cut.
- SQL-like is public default; natural is guided text; fluent is internal engine DSL.
- Active backlog is `QOL-WP1` through `QOL-WP5` plus release follow-up.
- `QOL-WP1` query diagnostics is implemented and review-hardened.

## Facts

- `2026-04-18`: No public users yet; fluent can leave stable public API as a compatibility reset.
- `2026-04-18`: Product direction is SQL-like primary, natural guided, fluent internal.
- `2026-04-18`: Public docs say SQL-like/natural lower into the shared execution engine.
- `2026-04-18`: `PojoLensCore`, `PojoLensRuntime.newQueryBuilder(...)`, and `ReportDefinition.fluent(...)` were removed from public entry points.
- `2026-04-18`: Mutable fluent planning types live under `laughing.man.commits.internal.builder`.
- `2026-04-18`: Public-surface guards reject internal APIs in public entry-point docs.
- `2026-04-19`: `docs/internal-fluent-engine.md` is the maintainer reference for fluent lifecycle, method groups, and usage examples.
- `2026-04-19`: `TODO.md` replaced completed surface-reset work with scoped QoL packages: diagnostics, exposure policy, dry-run preview, page result helper, and error suggestions.
- `2026-04-19`: Query diagnostics reports params, fields, sources, lint, multiple unknown `WHERE` fields, and natural diagnostics after vocabulary resolution.
- `2026-04-18`: Latest-release docs/examples/checks align to `2026.04.17.1834`.
- `2026-04-18`: CI runs full Maven tests on Temurin Java `17`, `21`, and `25`.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with optional `TreeEntry<T>` metadata.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and external authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then benchmark guardrails when performance changes.
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-19` query diagnostics hardening passed full Maven tests, doc check, diff check, and AI memory refresh/check.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
