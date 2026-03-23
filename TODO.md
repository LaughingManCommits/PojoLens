# TODO

## Current Roadmap: SQL Window Analytics

### 1) Window Functions MVP (`OVER`)

Problem:
- SQL-like queries cannot express ranking and row-aware analytics without collapsing rows.

Spike goal:
- Add first-class window-function support with a minimal, stable MVP scope.

Spike steps:
1. [ ] Extend parser/AST for `... OVER (...)` with `PARTITION BY` and `ORDER BY`.
2. [ ] Support initial functions: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`.
3. [ ] Add execution stage to compute window values after `WHERE` and before final projection.
4. [ ] Validate determinism rules when `ORDER BY` is missing or non-unique.

Acceptance criteria:
- SQL-like queries can compute rank-style window values per partition.
- Window output can be projected and sorted like normal select fields.
- Behavior is covered by parser + execution contract tests.

### 2) `QUALIFY` Clause

Problem:
- Filtering by window-function results currently requires awkward post-processing.

Spike goal:
- Add `QUALIFY` so callers can filter rows based on computed window values.

Spike steps:
1. [ ] Extend grammar with `QUALIFY` and add AST model.
2. [ ] Enforce clause ordering (`WHERE` -> window compute -> `QUALIFY` -> `ORDER/LIMIT/OFFSET`).
3. [ ] Support predicates against window aliases and direct window expressions.
4. [ ] Add explain metadata for qualify-stage row counts.

Acceptance criteria:
- Query pattern `... ROW_NUMBER() ... QUALIFY rn <= N` works end-to-end.
- Invalid clause ordering or unknown aliases return clear SQL-like errors.
- Explain includes a `qualify` stage when present.

### 3) Aggregate Windows (Phase 2)

Problem:
- Analytics use cases also need running totals and cumulative aggregates.

Spike goal:
- Add aggregate window functions with bounded initial frame semantics.

Spike steps:
1. [ ] Add `SUM/AVG/MIN/MAX/COUNT(...) OVER (...)` in parser and execution model.
2. [ ] Start with one frame mode: `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
3. [ ] Validate null-handling and type coercion parity with existing aggregate behavior.
4. [ ] Add guardrails for unsupported frame expressions.

Acceptance criteria:
- Running total style queries execute correctly and deterministically.
- Unsupported frame syntax fails fast with actionable errors.
- Performance remains acceptable on large in-memory datasets.

### 4) API and Docs Hardening

Problem:
- New SQL-like capabilities need discoverability and stability guarantees.

Spike goal:
- Ship window features with clear docs, examples, and compatibility tests.

Spike steps:
1. [ ] Add docs section with supported syntax and limitations.
2. [ ] Add sample query recipes (`top N per group`, `dense rank`, `running total`).
3. [ ] Add public API coverage tests for `PojoLens.parse(...).filter/explain`.
4. [ ] Add at least one benchmark comparing windowed vs non-windowed query costs.

Acceptance criteria:
- README/docs show practical examples for window + qualify usage.
- Regression tests lock parser, execution, and explain behavior.
- Benchmark notes document expected overhead and tradeoffs.

### 5) Predefined Stats Views (Easy Usage)

Problem:
- Most consumers want ready-made table/stat views and should not have to handwrite every aggregate query.

Spike goal:
- Add predefined view presets that generate common stats tables with minimal configuration.

Spike steps:
1. [ ] Define preset API for standard views (for example: `summary()`, `by(field)`, `topNBy(field, metric, n)`).
2. [ ] Implement preset compilation to fluent/SQL-like queries without introducing a separate execution engine.
3. [ ] Add output model for table-friendly payloads (`rows`, optional totals, schema metadata).
4. [ ] Add docs and examples for dashboard-style usage.
5. [ ] Add regression tests for preset correctness and stable output columns.

Acceptance criteria:
- Developers can generate common stats tables in a few lines with predefined presets.
- Preset output is deterministic and aligned with existing aggregate semantics.
- Docs include at least one end-to-end example for a grouped stats table and leaderboard table.
