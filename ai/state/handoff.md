# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP17`.
- CSV now has a `CSV-WP1` through `CSV-WP6` board; next is `CSV-WP4` coercion policy.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-10`: `SPIKE-CSV.md` now stages CSV follow-up as `CSV-WP1` through `CSV-WP6`; dynamic schema stays deferred.
- `2026-04-10`: CSV loading now supports multiline quoted fields and logical start-line diagnostics for multiline failures.
- `2026-04-10`: `PojoLensRuntime` now owns CSV defaults, exposes `runtime.csv().read(...)`, and keeps one-off overrides layered through `CsvOptions.toBuilder()`.
- `2026-04-09`: `PojoLensCsv` plus `CsvOptions` landed as a CSV adapter with strict typed loading, row/column-aware errors, nested-path support, and public API/docs coverage.
- `2026-04-09`: header-mode CSV loading now rejects missing primitive-backed mapped columns; nullable object fields may still be omitted and resolve to `null`.
- `2026-04-09`: workers must mirror approved validation entrypoints.
- `2026-04-09`: retained `WP17` runs proved accepted `tool: mvn ...`; the remaining orchestration gap is reviewer-visible new-file materialization.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- limitation spike: `SPIKE-LIMITATIONS.md`
- AI orchestration spike: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`, `scripts/claude-orchestrator.py`
- CSV spike and starter surface: `SPIKE-CSV.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`, `pojo-lens/src/test/java/laughing/man/commits/PojoLensCsvTest.java`
- natural surfaces: `docs/natural.md`, `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
