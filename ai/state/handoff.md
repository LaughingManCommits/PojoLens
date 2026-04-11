# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP18`; fully closed.
- CSV is complete through `CSV-WP5` plus cleanup hardening; `CSV-WP6` remains deferred.
- If engine follow-up resumes, the next limitation slice is time-bucket input broadening.

## Facts

- `2026-04-10`: CSV load now covers multiline quoted records, runtime-owned defaults, explicit coercion policy, split header diagnostics, logical/data counts, and `CsvLoadException.report()` across preflight plus load failures.
- `2026-04-10`: Shared `ReflectionUtil` now exposes enum leaves, keeping CSV binding aligned with general queryable-field discovery.
- `2026-04-10`: `CsvLoadJmhBenchmark` now keeps typed and multiline CSV load overhead in the guarded core suite under the `LOAD` category.
- `2026-04-10`: WP18 closed reviewer-visible new-file materialization: WP17 reviewer uses `apply-reviewed`, docs/csv.md added to readPaths, README + SYSTEM-SPEC updated.
- `2026-04-09`: retained `WP17` runs proved accepted `tool: mvn ...`.
- `2026-04-11`: `scripts/claude-orchestrator.py` now preserves optional agent `skills`; tracked workers pass `caveman` through `--agents` so the skill loads after agent setup.
- `2026-04-11`: the live `spike-limitations-subquery-widening` run completed after fixing aggregate subquery validation against `QueryRow` projections and normalizing grouped-only aliases in the subquery binder.
- `2026-04-11`: focused `pojo-lens` validation/contract tests and the full `pojo-lens` module test suite passed after the subquery widening fix.
- `2026-04-11`: `README.md` now stays at feature-set/routing level; SQL-like subquery/runtime preset detail and fluent builder reuse guidance live in `docs/sql-like.md` and `docs/usecases.md`.
- `2026-04-11`: public docs alignment passed `scripts/check-doc-consistency.ps1` and `SqlLikeDocsExamplesTest`.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- CSV: `TODO.md`, `docs/csv.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`, `pojo-lens/src/test/java/laughing/man/commits/PojoLensCsvTest.java`
- benchmarks: `docs/benchmarking.md`, `benchmarks/thresholds.json`, `scripts/benchmark-suite-main.args`, `pojo-lens-benchmarks/src/main/java/laughing/man/commits/benchmark/CsvLoadJmhBenchmark.java`
- orchestration: `ai/orchestrator/README.md`, `scripts/claude-orchestrator.py`
- limitation spike: `SPIKE-LIMITATIONS.md`
