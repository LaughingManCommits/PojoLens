# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.04.17.1834`.

## Focus

- `2026-04-18`: CI runtime matrix now tests Java `17`, `21`, and `25`; Maven compiler release remains `17`.
- `2026-04-18`: Latest-release docs/examples/checks align to `2026.04.17.1834`.
- `2026-04-18`: `SURFACE-WP1` through `SURFACE-WP6` are complete except the actual release cut.
- `2026-04-18`: SQL-like is the primary public query API; natural is guided controlled text after SQL-like.
- `2026-04-18`: Fluent planning is internal engine infrastructure under `laughing.man.commits.internal`.
- `2026-04-19`: `docs/internal-fluent-engine.md` now documents maintainer-only fluent method groups and usage examples.
- `2026-04-19`: `TODO.md` now tracks `QOL-WP1` through `QOL-WP5` for developer-experience improvements.
- `2026-04-19`: `QOL-WP1` complete — `QueryDiagnostics` and `QueryDiagnosticsError` added to `sqllike` package; `SqlLikeDiagnosticsSupport` added to `internal.diagnostics`; `SqlLikeQuery.diagnostics()`, `diagnostics(Class,Class)`, `diagnostics(Class,Class,JoinBindings)` and `NaturalQuery.diagnostics()`, `diagnostics(Class,Class)` added; 18 contract tests pass.
- `2026-04-18`: Documentation backlog `DOC-WP1` through `DOC-WP10` complete.
- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- April feature work through `PojoLensTree`, FA/RU cleanup, docs, and limitations is complete.

## Verified

- `2026-04-19`: QOL-WP1 diagnostics passed full Maven tests, doc consistency, and diff whitespace checks.
- `2026-04-19`: Internal fluent docs passed doc consistency and diff whitespace checks.
- `2026-04-18`: Java 25 CI, DOC-WP1 through DOC-WP10, and release alignment passed doc checks and Maven tests.
- `2026-04-18`: SQL-like-first public-surface reset passed doc checks, full Maven tests, benchmark module tests, lint baseline verify, binary compatibility reset verify, and `git diff --check`.
- `2026-04-17`: `PojoLensTree`, positioning docs, Checkstyle baseline, CI warnings, and benchmark thresholds validated.
- `2026-04-10` to `2026-04-14`: CSV WP1-WP5 and bounded subquery/existence parity validated.

## Release

- `2026.04.17.1834` is complete.

## Risks

- SQL-like is the primary public query API; fluent is internal.
- Bounded subquery/existence parity is user-facing complete.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only; it must not grow parser syntax, graph algorithms, ORM behavior, or a second query engine.
- User-authored SQL-like/natural text should use params, approved field/source exposure, lint mode, strict typing, and separate authorization before execution.

## Next

- Release: cut a later version after final release guardrails are run.
- QoL: `QOL-WP2` field/source exposure policy is the recommended next developer-experience package.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
- `TODO.md` active backlog is the developer-experience QoL roadmap plus release follow-up.
