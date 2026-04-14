# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP18`.
- CSV is complete through `CSV-WP5`; `CSV-WP6` remains deferred.
- Engine limitation work done: bounded windows, aggregate `ORDER BY`, immutable
  fluent prepare, joined/`EXISTS` subqueries, and bounded parity cleanup.

## Facts

- `2026-04-10`: CSV WP1-WP5 is validated; `CSV-WP6` remains deferred.
- `2026-04-11`: orchestration WP18, worker validation hints, and `caveman` skill propagation are validated.
- `2026-04-12`: bounded aggregate windows and aggregate `ORDER BY` diagnostic polish passed module tests and docs consistency.
- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- `2026-04-13`: SQL-like uncorrelated `WHERE ... IN (select ...)` subqueries
  can now use `JOIN` clauses backed by `JoinBindings`.
- `2026-04-13`: SQL-like bounded `WHERE [NOT] EXISTS (select ...)` works for
  self/named/joined sources; correlated/scalar/broad nested SQL remains unsupported.
- `2026-04-13`: limitation policy requires fluent-led parity; SQL-like/natural
  are facades, and precomputed user filters are not the parity answer.
- `2026-04-13`: natural schema vocabulary, `qualify` window phrases,
  broader window phrasing/frames, and resolved-delegate caching are done.
- `2026-04-14`: bounded subquery/existence parity is closed across fluent,
  SQL-like, and natural; natural uses bounded `query ... end query` grammar
  with nested runtime-vocabulary resolution.
- No default bounded subquery parity slice remains; keep correlated/scalar
  subqueries and broad SQL planning opt-in only.

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
