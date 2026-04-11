# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP18`; fully closed.
- CSV is complete through `CSV-WP5` plus cleanup hardening; `CSV-WP6` remains deferred.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-10`: `SPIKE-CSV.md` now stages CSV follow-up as `CSV-WP1` through `CSV-WP6`; dynamic schema stays deferred.
- `2026-04-10`: CSV load now covers multiline quoted records, runtime-owned defaults, explicit coercion policy, split header diagnostics, logical/data counts, and `CsvLoadException.report()` across preflight plus load failures.
- `2026-04-10`: Shared `ReflectionUtil` now exposes enum leaves, keeping CSV binding aligned with general queryable-field discovery.
- `2026-04-10`: `CsvLoadJmhBenchmark` now keeps typed and multiline CSV load overhead in the guarded core suite under the `LOAD` category.
- `2026-04-09`: `PojoLensCsv` plus `CsvOptions` landed as a CSV adapter with strict typed loading, row/column-aware errors, nested-path support, and public API/docs coverage.
- `2026-04-10`: WP18 closed reviewer-visible new-file materialization: WP17 reviewer uses `apply-reviewed`, docs/csv.md added to readPaths, README + SYSTEM-SPEC updated.
- `2026-04-09`: retained `WP17` runs proved accepted `tool: mvn ...`.
- `2026-04-11`: `scripts/claude-orchestrator.py` now preserves optional agent `skills`; tracked workers pass `caveman` through `--agents` so the skill loads after agent setup.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- CSV: `SPIKE-CSV.md`, `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`, `pojo-lens/src/test/java/laughing/man/commits/PojoLensCsvTest.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`, `pojo-lens-benchmarks/src/main/java/laughing/man/commits/benchmark/CsvLoadJmhBenchmark.java`
- orchestration: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
- limitation spike: `SPIKE-LIMITATIONS.md`
