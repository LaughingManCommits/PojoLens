# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- `2026-04-18`: CI runtime matrix now tests Java `17`, `21`, and `25`; Maven compiler release remains `17`.
- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- `2026-04-14`: limitation work done: bounded windows, aggregate ORDER BY, prepare, bounded subqueries/EXISTS, natural cleanup, SQL-like lowering.
- `2026-04-16`: `ReflectionUtil` and `FastArrayQuerySupport` cleanup complete.
- `2026-04-17`: `PojoLensTree` and ecosystem-positioning follow-up complete.

## Verified

- `2026-04-18`: Java 25 CI matrix update validated locally with `git diff --check`, `scripts/check-doc-consistency.ps1`, and `mvn -B -ntp test` on JDK 17; Java 25 executes in GitHub Actions via `actions/setup-java@v5`.
- `2026-04-10`: CSV WP1-WP5 validated including guarded load benchmarks.
- `2026-04-14`: bounded subquery/existence parity closed across fluent, SQL-like, and natural.
- `2026-04-17`: `PojoLensTree` validated with `mvn -B -ntp -pl pojo-lens test`, `scripts/check-doc-consistency.ps1`, and full `mvn -B -ntp test`.
- `2026-04-17`: positioning/release-readiness docs validated; Checkstyle baseline refreshed to `15691` entries and baseline gate passes with `new=0 fixed=0`.
- `2026-04-17`: CI workflow warnings addressed: Node 24 action majors, quoted deploy flags, validated chart PNG paths, and passing benchmark thresholds.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Fluent/core should lead capability; SQL-like and natural are facades.
- Bounded subquery/existence parity user-facing complete; SQL-like binding uses shared fluent/core path.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only; it must not grow parser syntax, graph algorithms, ORM behavior, or a second query engine.
- User-authored SQL-like/natural text should use params, approved field/source exposure, lint mode, strict typing, and separate authorization before execution.

## Next

- Release: cut next version to ship FA/RU cleanup, `PojoLensTree`, positioning docs, and all April features.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
- `TODO.md` has uncommitted DOC-WP backlog content from earlier plus ASCII punctuation cleanup from this session.
