# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` when memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP18`.
- CSV is complete through `CSV-WP5`; `CSV-WP6` remains deferred.
- Engine limitation work done: bounded windows, aggregate `ORDER BY`, fluent
  prepare, joined/`EXISTS` subqueries, natural cleanup, and SQL-like lowering.
- Scatter allocation follow-up has a narrow `size=1000` improvement; broader
  `10k`/`100k` scatter allocation checks are still optional follow-up work.

## Facts

- `2026-04-13`: `PojoLensCore.prepare(...)` returns immutable `FluentQueryDefinition<T>` with rows/schema/explain and `ReportDefinition` promotion.
- `2026-04-14`: bounded subquery/existence parity is closed across fluent,
  SQL-like, and natural; natural uses bounded `query ... end query` grammar
  with nested runtime-vocabulary resolution.
- `2026-04-14`: grouped fluent `QueryRule.inSubquery(...)`, `exists(...)`,
  and `notExists(...)` are done; SQL-like boolean `OR`/DNF subqueries lower
  onto fluent/core, and public docs are aligned.
- `2026-04-15`: `ReflectionUtil.DirectFieldReadPlan` owns direct POJO field
  reflection for chart fast paths; final `size=1000` scatter spot check
  measured fluent `259,400 B/op`, direct SQL-like `284,273 B/op`, and bound
  SQL-like `283,737 B/op`.
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
