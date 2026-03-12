# README Alignment

## Mostly Aligned

- Verified: README positioning as a POJO-first in-memory query library matches `pom.xml`, `PojoLens`, and the major contract tests.
- Verified: README quick starts for fluent queries, SQL-like queries, named parameters, chart mapping, chart presets, report definitions, dataset bundles, computed fields, snapshot comparison, runtime presets, and typed join bindings are covered by tests such as `SqlLikeDocsExamplesTest`, `StatsDocsExamplesTest`, `ReportDefinitionTest`, `DatasetBundleExecutionContextTest`, `SnapshotComparisonTest`, and `RuntimePolicyPresetTest`.
- Verified: README claim that chart support is payload mapping rather than native rendering matches `docs/charts.md`, `ChartValidation`, `ChartResultMapperValidationTest`, and the absence of renderer code outside benchmark/test XChart usage.
- Verified: README claim of staged entry points matches the concrete facade split in `PojoLens`, `PojoLensCore`, `PojoLensSql`, and `PojoLensChart`.
- Verified: README claim of deterministic multi-join support matches `SqlLikeJoinTest` and `docs/sql-like.md`.
- Verified: `docs/benchmarking.md` now uses the current `1.0.0` benchmark runner examples and documents both hotspot diagnostics and the budgeted end-to-end computed-field join benchmark path that match `HotspotMicroJmhBenchmark`, `PojoLensJoinJmhBenchmark`, the main benchmark suite args, and parity coverage.

## Verified Mismatches

- Mismatch: `MIGRATION.md` and `RELEASE.md` still say SQL-like does not support subqueries or multi-join SQL plans.
  Evidence: `SqlLikeParserTest` and `SqlLikeValidationTest` confirm limited `WHERE ... IN (select ...)` subqueries; `SqlLikeJoinTest` confirms chained join execution; `docs/sql-like.md` documents both behaviors.

- Mismatch: `CONTRIBUTING.md` and `RELEASE.md` still hardcode `1.3.0` benchmark jar or tag examples while `pom.xml` declares project version `1.0.0`.
  Evidence: `pom.xml` version versus hardcoded `target/pojo-lens-1.3.0-benchmarks.jar`, `Release 1.3.0`, and `v1.3.0` examples still present in those docs after `docs/benchmarking.md` was corrected on 2026-03-12.

- Mismatch: The repository doc-consistency script is narrower than the repo's actual drift surface.
  Evidence: `scripts/check-doc-consistency.ps1` passed on 2026-03-12, but it does not check the stale version strings above or the outdated subquery/multi-join limitation notes.

## Unknown Or Unverified

- Unknown: The README dependency snippet does not say where artifacts are published; repository scan found no `distributionManagement` block or publishing workflow.
