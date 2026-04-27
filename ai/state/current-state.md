# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.17.1834`.

## Focus
- `2026-04-27`: `FluentWindowSupport` now computes window values before wrapping `RawQueryRow`s, fixing fluent and SQL-like window and `QUALIFY` rows.
- `2026-04-27`: `-Pstatic-analysis` uses SpotBugs `check` with `failThreshold=High`, and the current run is clean.
- `2026-04-27`: Release cut remains user-controlled; WP18 is next after release.

## Verified
- `2026-04-27`: `mvn -B -ntp test`, `mvn -B -ntp -Plint verify -DskipTests`, and `mvn -B -ntp -Pstatic-analysis verify -DskipTests` all passed after the window fix.

## Release
- Latest cut is `2026.04.17.1834`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- Wait for a release request, then move to WP18.
