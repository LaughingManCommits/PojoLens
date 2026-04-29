# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.17.1834`.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 `-Pstatic-analysis` gate is clean.
- `2026-04-29`: Surface/tooling cleanup packages are complete: wrapper guidance is `ReportDefinition`/`SavedReport` first, docs are authoring-first, `PojoLensFiles` owns CSV/TSV/JSON/JSONL with Excel as a non-goal, and build tooling is split between `metamodel` generation and `tooling` validation.
- `2026-04-29`: WP23 completed. `TypedQuery` now supports join declarations, grouped aggregates, totals-style metrics, aggregate-output ordering, and `JoinBindings` / `DatasetBundle` execution, explain, and schema reuse on the same surface.
- `2026-04-29`: WP24 is in progress. `TypedQuery` now covers grouped `HAVING`, rank/running window outputs, and `QUALIFY` on the same immutable surface; bounded window frames and typed subqueries remain open.

## Verified
- `2026-04-27`: Full reactor, lint, and static-analysis gates passed after the window fix.
- `2026-04-29`: WP21/WP25/WP22 all passed their focused public-surface or tooling coverage plus `mvn -B -ntp test` and `scripts/check-doc-consistency.ps1`.
- `2026-04-29`: WP23 plus the WP24 `HAVING` and window/`QUALIFY` slices passed focused typed/public-API coverage, full reactor `mvn -B -ntp test`, `mvn -B -ntp -Pstatic-analysis verify -DskipTests`, and `scripts/check-doc-consistency.ps1`.

## Release
- Latest cut is `2026.04.17.1834`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-29`: Roadmap order is WP24 -> WP18 -> Release Gate.
- `2026-04-29`: Continue WP24 with bounded/public typed window-frame design and typed subquery/existence composition.
