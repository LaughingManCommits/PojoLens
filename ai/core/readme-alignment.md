# README Alignment

## Confirmed Alignment

- `README.md` positions the repository correctly as a POJO-first in-memory query library with SQL-like as the primary public query surface, typed DSL as the Java-owned composition path, controlled plain-English as the guided text alternative, advisory pushdown-readiness metadata for host-owned adapters, and bounded CSV/tree helpers before normal query execution.
- `README.md`, `docs/entry-points.md`, and `docs/usecases.md` now separate the three primary authoring modes (`PojoLensSql`, `PojoLensNatural`, `TypedQuery`) from reusable contracts, runtime policy, and boundary/workflow helpers.
- `README.md`, `docs/files.md`, and `docs/csv.md` now keep file-boundary onboarding under one `PojoLensFiles` story while leaving `PojoLensCsv` as CSV-only convenience; that shared loader surface now covers CSV, TSV, JSON, and JSONL.
- README quick starts for SQL-like queries, plain-English queries, optional CSV boundary loading, tree row shaping, charts, reports, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are covered by tests.
- Public entry points in README keep SQL-like, natural, and typed as first-read authoring defaults while documenting `PojoLensRuntime`, `ReportDefinition`, file/tree/chart helpers, and dataset helpers as added layers.
- `docs/files.md` now documents the explicit Excel non-goal on the shared file-loader surface instead of leaving that decision implicit.

## Process-Doc Alignment

- `CONTRIBUTING.md` uses dynamic benchmark-jar resolution guidance.
- `MIGRATION.md` and `RELEASE.md` match current SQL-like capability constraints and release flow.
- `scripts/check-doc-consistency.ps1` and `scripts/check-doc-consistency.py` enforce key doc invariants.

## Current Gap

- `WP24` is closed. The typed surface now covers joins, grouped aggregates,
  grouped `HAVING`, bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries, rank
  windows, aggregate window frames, and `QUALIFY`. The remaining deliberate
  typed boundary is that correlated/scalar subqueries and broader
  user-authored text flows stay on SQL-like or natural entry points.
