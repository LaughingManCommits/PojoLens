# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.04.17.1834`.

## Focus

- `2026-04-18`: CI, docs, and surface reset complete; SQL-like primary, natural guided, fluent internal.
- `2026-04-18`: `SURFACE-WP1` through `SURFACE-WP6` complete except release cut.
- `2026-04-19`: `docs/internal-fluent-engine.md` documents maintainer-only fluent method groups and examples.
- `2026-04-19`: `TODO.md` tracks `QOL-WP1` through `QOL-WP5`.
- `2026-04-19`: `QOL-WP1` query diagnostics is implemented and review-hardened.
- `2026-04-19`: `QOL-WP2` query exposure policy is implemented.
- `2026-04-19`: `QOL-WP3` SQL-like dry-run plan preview is implemented and review-hardened.
- `2026-04-19`: `QOL-WP4` page result helper is implemented.
- `2026-04-18`: Documentation backlog `DOC-WP1` through `DOC-WP10` complete.
- `2026-04-10`: CSV complete through `CSV-WP5`; `CSV-WP6` deferred.
- April feature work through `PojoLensTree`, FA/RU cleanup, docs, and limitations is complete.

## Verified

- `2026-04-19`: QOL-WP1 (diagnostics) and QOL-WP2 (exposure) passed full Maven tests, doc consistency, diff check, and memory refresh/check.
- `2026-04-19`: QOL-WP3 hardening passed 28 preview tests, full 799-test Maven suite, doc consistency, and diff check.
- `2026-04-19`: QOL-WP4 page result helper passed 16 page tests + full Maven suite, doc consistency, diff check.
- `2026-04-19`: Internal fluent docs passed doc consistency and diff whitespace checks.
- `2026-04-18`: Java 25 CI, DOC-WP1–WP10, surface reset, and release alignment passed full Maven tests, doc checks, lint, and binary compat verify.
- `2026-04-17`: `PojoLensTree`, positioning docs, Checkstyle baseline, CI warnings, and benchmark thresholds validated.

## Release

- `2026.04.17.1834` is complete.

## Risks

- SQL-like is the primary public query API; fluent is internal.
- Bounded subquery/existence parity is user-facing complete.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only; it must not grow parser syntax, graph algorithms, ORM behavior, or a second query engine.
- User-authored query text needs params, exposure policy, lint, strict typing, and external authorization.

## Next

- Release: cut a later version after final release guardrails are run.
- QoL: `QOL-WP5` better error suggestions is next.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: correlated/scalar subqueries and broad window-frame parity stay opt-in only.
