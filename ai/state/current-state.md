# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.17.1834`.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 `-Pstatic-analysis` gate is clean.
- `2026-04-29`: WP26 settled reusable-wrapper guidance: `ReportDefinition` / `SavedReport` are the defaults; chart/stats presets remain advanced convenience.
- `2026-04-29`: WP21 completed the authoring-first docs pass and grouped chart/table/schema guidance under `docs/output-helpers.md`.
- `2026-04-29`: WP25 completed the shared file-boundary loader story: `PojoLensFiles` / `runtime.files()` cover CSV, TSV, JSON, and JSONL, and Excel is an explicit non-goal.
- `2026-04-29`: WP22 completed: `laughing.man.commits.metamodel` owns single-model and batch metamodel generation, `laughing.man.commits.tooling` owns saved-report/query validation, and `docs/build-tooling.md` documents the Maven wiring.
- `2026-04-29`: WP23 completed. `TypedQuery` now supports join declarations, grouped aggregates, totals-style metrics, aggregate-output ordering, and `JoinBindings` / `DatasetBundle` execution, explain, and schema reuse on the same surface.

## Verified
- `2026-04-27`: Full reactor, lint, and static-analysis gates passed after the window fix.
- `2026-04-29`: WP21 and the first WP25 file-loader slice both passed doc consistency plus focused public-surface/public-API test runs.
- `2026-04-29`: WP25 completion passed `mvn -B -ntp test`, `mvn -B -ntp -pl pojo-lens "-Dtest=PojoLensFilesTest,PojoLensCsvTest,StablePublicApiContractTest,PublicApiEcosystemCoverageTest" test`, and `scripts/check-doc-consistency.ps1`.
- `2026-04-29`: WP22 completion passed `mvn -B -ntp -pl pojo-lens "-Dtest=MetamodelBatchGeneratorTest,SavedReportCatalogValidatorTest,FieldMetamodelGeneratorTest,SavedReportTest,PublicApiEcosystemCoverageTest" test`, `mvn -B -ntp test`, and `scripts/check-doc-consistency.ps1`.
- `2026-04-29`: WP22 surface consolidation passed `mvn -B -ntp -pl pojo-lens "-Dtest=MetamodelBatchGeneratorTest,SavedReportCatalogValidatorTest,PublicApiEcosystemCoverageTest" test`, `mvn -B -ntp test`, and `scripts/check-doc-consistency.ps1` after moving batch metamodel APIs out of `tooling` and into `metamodel`.
- `2026-04-29`: WP23 completion passed focused typed/public-API coverage, `mvn -B -ntp -pl pojo-lens test`, `mvn -B -ntp -Pstatic-analysis verify -DskipTests`, and `scripts/check-doc-consistency.ps1`.

## Release
- Latest cut is `2026.04.17.1834`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-29`: Roadmap order is WP24 -> WP18 -> Release Gate.
- `2026-04-29`: Next package is WP24: decide which advanced typed shapes (`HAVING`, windows, bounded subqueries) should graduate onto `TypedQuery` and which should remain text-only.
