# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: implement `QOL-WP2` field/source exposure policy or decide whether to cut the next date-based release first.

## Focus

- Java 25 CI support and DOC-WP1 through DOC-WP10 are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `PojoLensTree`, ecosystem-positioning docs, CI warnings, and Checkstyle baseline are complete.
- `SURFACE-WP1` through `SURFACE-WP6` are complete except the actual release cut.
- SQL-like is the public default; natural is guided text; fluent is internal engine DSL.
- Active backlog is now `QOL-WP1` through `QOL-WP5` plus release follow-up.

## Facts

- `2026-04-18`: No public users yet; fluent can leave stable public API as a compatibility reset.
- `2026-04-18`: Product direction is SQL-like primary, natural guided, fluent internal.
- `2026-04-18`: Public docs say SQL-like/natural lower into the shared execution engine, not the fluent pipeline.
- `2026-04-18`: `PojoLensCore`, `PojoLensRuntime.newQueryBuilder(...)`, and `ReportDefinition.fluent(...)` were removed from public entry points.
- `2026-04-18`: Mutable fluent planning types live under `laughing.man.commits.internal.builder`.
- `2026-04-18`: Public-surface guards reject internal APIs in public entry-point docs.
- `2026-04-19`: `docs/internal-fluent-engine.md` is the maintainer reference for fluent lifecycle, method groups, and usage examples.
- `2026-04-19`: `TODO.md` replaced completed surface-reset work with scoped QoL packages: diagnostics, exposure policy, dry-run preview, page result helper, and error suggestions.
- `2026-04-19`: `QOL-WP1` complete — `QueryDiagnostics`, `QueryDiagnosticsError` public; `SqlLikeDiagnosticsSupport` internal; diagnostics entry points on `SqlLikeQuery` (3 overloads) and `NaturalQuery` (2 overloads); 18 tests pass.
- `2026-04-18`: Latest-release docs/examples/checks align to `2026.04.17.1834`.
- `2026-04-18`: CI runs full Maven tests on Temurin Java `17`, `21`, and `25`.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with optional `TreeEntry<T>` metadata.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and external authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then core guardrail suite + threshold checker (see `docs/benchmarking.md`)
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-19` QoL backlog refresh passed `scripts/check-doc-consistency.ps1`.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`, `benchmarks/thresholds.json`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
