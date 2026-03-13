# README Alignment

Aligned with code and tests:

- `README.md` correctly positions the repo as a POJO-first in-memory query library with fluent and SQL-like entry points.
- README quick starts for fluent queries, SQL-like queries, parameters, charts, reports, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are backed by contract or docs-example tests.
- README staging around `PojoLens`, `PojoLensCore`, `PojoLensSql`, and `PojoLensChart` matches the codebase.

Current documentation alignment outside the README:

- `CONTRIBUTING.md` now resolves benchmark jars dynamically instead of hardcoding a versioned filename.
- `MIGRATION.md` and `RELEASE.md` now describe limited `WHERE ... IN (select oneField ...)` subquery support and chained SQL-like joins consistently with code and tests.
- `scripts/check-doc-consistency.ps1` and `scripts/check-doc-consistency.py` now check for benchmark-version drift and stale SQL-like capability wording in those process docs.

Open documentation gap:

- no artifact publication target is documented or declared in `pom.xml`
