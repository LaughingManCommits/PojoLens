# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP18`.
- CSV is complete through `CSV-WP5`; `CSV-WP6` remains deferred.
- If engine follow-up resumes, bounded aggregate windows, aggregate `ORDER BY` diagnostics, immutable fluent prepared definitions, uncorrelated joined subqueries, and bounded natural cleanup are done.

## Facts

- `2026-04-10`: CSV WP1-WP5 is validated; `CSV-WP6` remains deferred.
- `2026-04-11`: orchestration WP18, worker validation hints, and `caveman` skill propagation are validated.
- `2026-04-11`: SQL-like subqueries support grouped and aggregate outputs.
- `2026-04-11`: limitation scan found time-bucket input broadening done.
- `2026-04-11`: `README.md` now stays at feature-set/routing level; SQL-like subquery/runtime preset detail and fluent builder reuse guidance live in `docs/sql-like.md` and `docs/usecases.md`.
- `2026-04-12`: bounded aggregate windows and aggregate `ORDER BY` diagnostic polish passed module tests and docs consistency.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- `2026-04-13`: SQL-like uncorrelated `WHERE ... IN (select ...)` subqueries can now use `JOIN` clauses backed by `JoinBindings`; correlated, `EXISTS`, scalar, and broad nested SQL subqueries remain unsupported.
- `2026-04-13`: natural `schema(...)` resolves runtime vocabulary for non-join and join-aware overloads; `qualify` supports controlled inline window phrases; window phrasing supports multiple partitions and supported aggregate frames; resolved delegates are cached by execution shape.

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
