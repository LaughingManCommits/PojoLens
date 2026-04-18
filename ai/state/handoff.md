# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.
5. Next likely task: start `SURFACE-WP4`.

## Focus

- Java 25 CI support and DOC-WP1 through DOC-WP10 are complete.
- Latest release alignment is complete for `2026.04.17.1834`.
- `PojoLensTree`, ecosystem-positioning docs, CI warnings, and Checkstyle baseline are complete.
- `TODO.md` tracks `SURFACE-WP1` through `SURFACE-WP6`.
- `SURFACE-WP1` is complete; SQL-like is now the README and path-selection default.
- `SURFACE-WP2` is complete; natural is guided controlled text after SQL-like.
- `SURFACE-WP3` is complete; fluent is documented as internal engine DSL only.

## Facts

- `2026-04-18`: No public users yet; fluent can leave stable public API as a compatibility reset.
- `2026-04-18`: Product direction is SQL-like primary, natural guided, fluent internal.
- `2026-04-18`: Public docs now say SQL-like/natural lower into the shared execution engine, not the fluent pipeline.
- `2026-04-18`: Natural docs cover controlled grammar, vocabulary, params, diagnostics, lint, strict typing, and authorization.
- `2026-04-18`: `PojoLensCore` remains an internal bridge pending guard/package reset.
- `2026-04-18`: Latest-release docs/examples/checks align to `2026.04.17.1834`.
- `2026-04-18`: CI runs full Maven tests on Temurin Java `17`, `21`, and `25`.
- `2026-04-18`: Benchmark docs use dynamic `BENCHMARK_JAR` resolution and doc consistency scripts forbid hardcoded versioned benchmark jar paths.
- `2026-04-17`: `PojoLensTree` supports deterministic flat parent-ID subtree shaping with optional `TreeEntry<T>` metadata.
- `2026-04-17`: binary compatibility includes `PojoLensTree`, `TreeTraversalBuilder`, and `TreeEntry`.
- User-authored query text needs params, approved fields/sources, lint, strict typing, and external authorization.

## Validate

- After code changes: `mvn -B -ntp test`, then core guardrail suite + threshold checker (see `docs/benchmarking.md`)
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- Last validation: `2026-04-18` `SURFACE-WP3` passed doc checks, POM validate, memory refresh/check, and `git diff --check`.

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- CI: `.github/workflows/ci.yml`, `CHANGELOG.md`
- docs/public API: `README.md`, `docs/**`, `scripts/check-doc-consistency.*`
- tree: `docs/tree.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensTree.java`
- CSV: `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
