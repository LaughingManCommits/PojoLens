# TODO

## Current Roadmap: SQL Window Analytics

### 1) Window Functions MVP (`OVER`)

Problem:
- SQL-like queries cannot express ranking and row-aware analytics without collapsing rows.

Spike goal:
- Add first-class window-function support with a minimal, stable MVP scope.

Spike steps:
1. [x] Extend parser/AST for `... OVER (...)` with `PARTITION BY` and `ORDER BY`.
2. [x] Support initial functions: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`.
3. [x] Add execution stage to compute window values after `WHERE` and before final projection.
4. [x] Validate determinism rules when `ORDER BY` is missing or non-unique.

Acceptance criteria:
- [x] SQL-like queries can compute rank-style window values per partition.
- [x] Window output can be projected and sorted like normal select fields.
- [x] Behavior is covered by parser + execution contract tests.

### 2) `QUALIFY` Clause

Problem:
- Filtering by window-function results currently requires awkward post-processing.

Spike goal:
- Add `QUALIFY` so callers can filter rows based on computed window values.

Spike steps:
1. [x] Extend grammar with `QUALIFY` and add AST model.
2. [x] Enforce clause ordering (`WHERE` -> window compute -> `QUALIFY` -> `ORDER/LIMIT/OFFSET`).
3. [x] Support predicates against window aliases and direct window expressions.
4. [x] Add explain metadata for qualify-stage row counts.

Acceptance criteria:
- [x] Query pattern `... ROW_NUMBER() ... QUALIFY rn <= N` works end-to-end.
- [x] Invalid clause ordering or unknown aliases return clear SQL-like errors.
- [x] Explain includes a `qualify` stage when present.

### 3) Aggregate Windows (Phase 2)

Problem:
- Analytics use cases also need running totals and cumulative aggregates.

Spike goal:
- Add aggregate window functions with bounded initial frame semantics.

Spike steps:
1. [x] Add `SUM/AVG/MIN/MAX/COUNT(...) OVER (...)` in parser and execution model.
2. [x] Start with one frame mode: `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
3. [x] Validate null-handling and type coercion parity with existing aggregate behavior.
4. [x] Add guardrails for unsupported frame expressions.

Acceptance criteria:
- [x] Running total style queries execute correctly and deterministically.
- [x] Unsupported frame syntax fails fast with actionable errors.
- [x] Performance remains acceptable on large in-memory datasets.

### 4) API and Docs Hardening

Problem:
- New SQL-like capabilities need discoverability and stability guarantees.

Spike goal:
- Ship window features with clear docs, examples, and compatibility tests.

Spike steps:
1. [x] Add docs section with supported syntax and limitations.
2. [x] Add sample query recipes (`top N per group`, `dense rank`, `running total`).
3. [x] Add public API coverage tests for `PojoLens.parse(...).filter/explain`.
4. [x] Add at least one benchmark comparing windowed vs non-windowed query costs.

Acceptance criteria:
- [x] README/docs show practical examples for window + qualify usage.
- [x] Regression tests lock parser, execution, and explain behavior.
- [x] Benchmark notes document expected overhead and tradeoffs.

### 5) Predefined Stats Views (Easy Usage)

Problem:
- Most consumers want ready-made table/stat views and should not have to handwrite every aggregate query.

Spike goal:
- Add predefined view presets that generate common stats tables with minimal configuration.

Spike steps:
1. [x] Define preset API for standard views (for example: `summary()`, `by(field)`, `topNBy(field, metric, n)`).
2. [x] Implement preset compilation to fluent/SQL-like queries without introducing a separate execution engine.
3. [x] Add output model for table-friendly payloads (`rows`, optional totals, schema metadata).
4. [x] Add docs and examples for dashboard-style usage.
5. [x] Add regression tests for preset correctness and stable output columns.

Acceptance criteria:
- [x] Developers can generate common stats tables in a few lines with predefined presets.
- [x] Preset output is deterministic and aligned with existing aggregate semantics.
- [x] Docs include at least one end-to-end example for a grouped stats table and leaderboard table.
