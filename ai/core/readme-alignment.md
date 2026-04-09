# README Alignment

## Confirmed Alignment

- `README.md` positions the repository correctly as a POJO-first in-memory query library with fluent, SQL-like, plain-English, and bounded CSV boundary-adapter surfaces.
- README quick starts for fluent queries, SQL-like queries, plain-English queries, optional CSV boundary loading, charts, reports, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are covered by tests.
- Public entry points in README (`PojoLensCore`, `PojoLensNatural`, `PojoLensSql`, `PojoLensCsv`, `PojoLensChart`, `PojoLensRuntime`) match current code.

## Process-Doc Alignment

- `CONTRIBUTING.md` uses dynamic benchmark-jar resolution guidance.
- `MIGRATION.md` and `RELEASE.md` match current SQL-like capability constraints and release flow.
- `scripts/check-doc-consistency.ps1` and `scripts/check-doc-consistency.py` enforce key doc invariants.

## Current Gap

- No known high-impact README drift is currently open.
