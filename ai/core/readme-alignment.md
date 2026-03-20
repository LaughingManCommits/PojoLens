# README Alignment

## Confirmed Alignment

- `README.md` positions the repository correctly as an in-memory POJO query library with fluent and SQL-like APIs.
- README quick starts for fluent queries, SQL-like queries, parameters, charts, reports, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are covered by tests.
- Public entry points in README (`PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`) match current code.

## Process-Doc Alignment

- `CONTRIBUTING.md` uses dynamic benchmark-jar resolution guidance.
- `MIGRATION.md` and `RELEASE.md` match current SQL-like capability constraints and release flow.
- `scripts/check-doc-consistency.ps1` and `scripts/check-doc-consistency.py` enforce key doc invariants.

## Current Gap

- No known high-impact README drift is currently open.
