# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 `-Pstatic-analysis` gate is clean.
- `2026-04-29`: Surface/tooling cleanup packages are complete: wrapper guidance is `ReportDefinition`/`SavedReport` first, docs are authoring-first with dedicated SQL-like, natural, and typed guides, `PojoLensFiles` owns CSV/TSV/JSON/JSONL with Excel as a non-goal, and build tooling is split between `metamodel` generation and `tooling` validation.
- `2026-04-29`: WP23 completed. `TypedQuery` now supports join declarations, grouped aggregates, totals-style metrics, aggregate-output ordering, and `JoinBindings` / `DatasetBundle` execution, explain, and schema reuse on the same surface.
- `2026-04-29`: WP24 completed. `TypedQuery`/`TypedPredicate` now cover grouped `HAVING`, bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries, rank windows, aggregate window frames via `QueryWindowFrame`, and `QUALIFY` on the same immutable surface.
- `2026-04-29`: `README.md` is now trimmed to one onboarding route table, quick starts, non-goals, and a short docs map so developers do not have to scan repeated product-surface taxonomy before reaching the correct guide.
- `2026-04-29`: `README.md` now also includes a short `Quick Integration` section that points AI agents to `AGENTS.md` and keeps the default validation command visible near the top.

## Verified
- `2026-04-27`: Full reactor, lint, and static-analysis gates passed after the window fix.
- `2026-04-29`: WP21/WP25/WP22 all passed their focused public-surface or tooling coverage plus `mvn -B -ntp test` and `scripts/check-doc-consistency.ps1`.
- `2026-04-29`: WP23 and WP24 passed focused typed/public-API coverage, full reactor `mvn -B -ntp test`, `mvn -B -ntp -Pstatic-analysis verify -DskipTests`, and `scripts/check-doc-consistency.ps1`.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-29`: Roadmap order is WP26 -> WP18 -> Release Gate.
- `2026-04-29`: Next roadmap item is WP26 Typed Authoring Compiler Integration.
