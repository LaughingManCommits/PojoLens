# README Alignment

## Confirmed Alignment

- `README.md` positions the repository correctly as a POJO-first in-memory query library with SQL-like as the primary public query surface, typed DSL as the Java-owned composition path, controlled plain-English as the guided text alternative, and bounded CSV/tree helpers before normal query execution.
- README quick starts for SQL-like queries, plain-English queries, optional CSV boundary loading, tree row shaping, charts, reports, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are covered by tests.
- Public entry points in README (`PojoLensSql`, `TypedQuery`, `PojoLensNatural`, `PojoLensCsv`, `PojoLensTree`, `PojoLensChart`, `PojoLensRuntime`, `ReportDefinition`) match the current SQL-like-first docs direction.

## Process-Doc Alignment

- `CONTRIBUTING.md` uses dynamic benchmark-jar resolution guidance.
- `MIGRATION.md` and `RELEASE.md` match current SQL-like capability constraints and release flow.
- `scripts/check-doc-consistency.ps1` and `scripts/check-doc-consistency.py` enforce key doc invariants.

## Current Gap

- `SURFACE-WP2` keeps natural positioned as guided plain-English text after SQL-like in general public docs.
