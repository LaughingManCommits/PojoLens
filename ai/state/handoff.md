# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP23 -> WP24 -> WP18.
4. Treat `Release Gate` as the final roadmap package and cut from `RELEASE.md` when the user wants it.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 static-analysis gate is clean.
- `2026-04-29`: WP26 set `ReportDefinition` / `SavedReport` as the default reusable-contract story; preset wrappers remain advanced convenience.
- `2026-04-29`: WP21 completed the authoring-first/docs-layering pass, including `docs/output-helpers.md`.
- `2026-04-29`: WP25 is complete. `PojoLensFiles` / `runtime.files()` are the single file-boundary loader route for CSV, TSV, JSON, and JSONL; Excel is an explicit non-goal.
- `2026-04-29`: WP22 is complete. `laughing.man.commits.metamodel` owns single-model and batch metamodel generation, `laughing.man.commits.tooling` stays validation-only, and `docs/build-tooling.md` documents the staged library-first shape.
- `2026-04-29`: WP23 is in progress. `TypedQuery.join(...)` plus `JoinBindings` / `DatasetBundle` filter, explain, and schema overloads now cover the first typed multi-source slice.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-27`: `scripts/checkstyle-baseline.txt` is intentionally empty because the lint report is clean.
- `2026-04-27`: The latest recorded window rerun at `size=10000` landed at `0.623 ms/op` baseline, `1.825 ms/op` rank, and `1.843 ms/op` running total.
- `2026-04-29`: `TODO.md` order is WP23, WP24, WP18, then `Release Gate`.
- `2026-04-29`: WP25 outcome: file onboarding now routes through `docs/files.md`, `PojoLensFiles` owns CSV/TSV/JSON/JSONL, `PojoLensRuntime` owns both delimited-text and JSON defaults, and no peer `PojoLensJson` surface was added.
- `2026-04-29`: WP22 outcome: build tooling stays library-first. `MetamodelBatchGenerator` now lives under `metamodel`, `SavedReportCatalogValidator` stays in `tooling`, and natural/raw fallback uses `PLT-SAVED-008`.
- `2026-04-29`: WP23 outcome so far: typed joins reuse `JoinBindings` and `DatasetBundle`, missing join sources fail with the same SQL-like error code path, and grouped metrics/windows/subqueries remain deferred.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
