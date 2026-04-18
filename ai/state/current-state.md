# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.04.17.1834`.

## Focus

- `2026-04-18`: CI runtime matrix now tests Java `17`, `21`, and `25`; Maven compiler release remains `17`.
- `2026-04-18`: Latest-release docs/examples/checks align to `2026.04.17.1834`.
- `2026-04-18`: `TODO.md` now tracks a SQL-like-first public-surface reset with fluent internal.
- `2026-04-18`: `SURFACE-WP1` complete; README/path-selection docs now make SQL-like the public default.
- `2026-04-18`: `SURFACE-WP2` complete; natural is positioned as guided controlled text after SQL-like.
- `2026-04-18`: Documentation backlog `DOC-WP1` through `DOC-WP10` complete.
- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- April feature work through `PojoLensTree`, FA/RU cleanup, docs, and limitations is complete.

## Verified

- `2026-04-18`: Java 25 CI, DOC-WP1 through DOC-WP10, and release alignment passed doc checks and Maven tests.
- `2026-04-18`: `SURFACE-WP1` passed doc checks, `git diff --check`, and focused SQL-like/natural/tree docs tests.
- `2026-04-17`: `PojoLensTree`, positioning docs, Checkstyle baseline, CI warnings, and benchmark thresholds validated.
- `2026-04-10` to `2026-04-14`: CSV WP1-WP5 and bounded subquery/existence parity validated.

## Release

- `2026.04.17.1834` is complete.

## Risks

- SQL-like is becoming the primary public query API; fluent is moving internal.
- Bounded subquery/existence parity is user-facing complete.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only; it must not grow parser syntax, graph algorithms, ORM behavior, or a second query engine.
- User-authored SQL-like/natural text should use params, approved field/source exposure, lint mode, strict typing, and separate authorization before execution.

## Next

- Public API: execute `SURFACE-WP3` through `SURFACE-WP6`.
- Release: cut a later version after the fluent reset is validated.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
- `TODO.md` active backlog is the SQL-like-first public-surface reset.
