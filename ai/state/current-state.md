# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.17.1834`.

## Focus
- `2026-04-27`: `FluentWindowSupport` now computes window values before wrapping `RawQueryRow`s, fixing fluent and SQL-like window and `QUALIFY` rows.
- `2026-04-27`: `-Pstatic-analysis` uses SpotBugs `check` with `failThreshold=High`, and the current run is clean.
- `2026-04-27`: Window snapshot schema lookup now keeps computed-field-aware names on materialized rows while reusing execution source types for temporary snapshots; the rerun window suite landed at `0.623/1.825/1.843 ms/op` for baseline/rank/running total at `size=10000`.
- `2026-04-29`: Release cut remains user-controlled and now sits as the last roadmap package after WP26.
- `2026-04-29`: WP26 wrapper guidance landed: `ReportDefinition` / `SavedReport` are now the default reusable-contract story in first-read docs, while chart/stats preset wrappers remain advanced convenience sugar without deprecation in this release.

## Verified
- `2026-04-27`: `mvn -B -ntp test`, `mvn -B -ntp -Plint verify -DskipTests`, and `mvn -B -ntp -Pstatic-analysis verify -DskipTests` all passed after the window fix.

## Release
- Latest cut is `2026.04.17.1834`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-04-29`: TODO roadmap is now ordered by dependency: WP21 -> WP25 -> WP22 -> WP23 -> WP24 -> WP18 -> Release Gate.
- `2026-04-29`: Wrapper guidance is settled; the next active package is WP21 for broader surface consolidation.
