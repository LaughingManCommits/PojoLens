# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-18`: SQL-like is the primary public API; natural is guided text; fluent is internal.
- `2026-04-18`: `SURFACE-WP1` through `SURFACE-WP6` and the docs reset are complete except the release cut.
- `2026-04-22`: Product direction is embedded reporting plus governed in-memory query execution, not a general Java query stack.
- `2026-04-22`: `TODO.md` now tracks `STRAT-WP1` through `STRAT-WP5` plus a release gate.
- `2026-04-22`: `STRAT-WP1` complete — SavedReport + SavedReportKind delivered; TabularColumn.typeName() added; 25 new tests; all 5 TODO tasks checked.
- `2026-04-22`: `STRAT-WP2` complete — QueryExecutionGuard, QueryGuardOutcome, QueryComplexitySummary, QueryExecutionGuardException delivered; wired into SqlLikeQuery + NaturalQuery; GUARD_REJECTED telemetry stage; 23 new tests; all 5 TODO tasks checked.
- `2026-04-20`: `QOL-WP1` through `QOL-WP5` are complete and review-hardened.
- `2026-04-10`: CSV is complete through `CSV-WP5`; `CSV-WP6` stays deferred.
- `2026-04-17`: `PojoLensTree` and April docs/cleanup work are complete.

## Verified

- `2026-04-22`: Repo-vs-value review passed `mvn -B -ntp test`; the strategic `TODO.md` rewrite was diff-reviewed.
- `2026-04-22`: STRAT-WP2 passed full `mvn test` on `pojo-lens` module with 0 failures.
- `2026-04-20`: `QOL-WP1` through `QOL-WP5` passed Maven, docs, and diff validation across their hardening work.
- `2026-04-18`: Java 25 CI, docs reset, release alignment, lint, and binary compat verify passed.
- `2026-04-17`: `PojoLensTree`, positioning docs, Checkstyle baseline, and benchmark thresholds validated.

## Release

- `2026.04.17.1834` is complete.

## Risks

- SQL-like is the primary public API; fluent is internal.
- Natural remains controlled grammar; static parse/template stay vocabulary-free.
- `PojoLensTree` is row shaping only, not a second query engine.
- User-authored query text still needs params, exposure policy, lint, strict typing, and host-owned authorization.

## Next

- `STRAT-WP1` and `STRAT-WP2` are done; next is `STRAT-WP3` (typed DSL) or release preparation.
- Two strategic packages delivered; conditions for release gate are partially met.
- Do not cut the next release until at least one strategic package lands.
- Keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Keep correlated/scalar subqueries and broad window-frame parity out of scope for now.
