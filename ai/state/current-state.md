# Current State

## Repo

- Java 17 multi-module library build with runtime, Spring Boot, and JMH modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-22`: Product direction is embedded reporting plus governed in-memory query execution.
- `2026-04-22`: `STRAT-WP1` shipped the saved-report contract.
- `2026-04-23`: `STRAT-WP2` hardening now enforces guards on lazy/bound execution and counts join-source rows in scan budgets.
- `2026-04-23`: `TODO.md` tracks remaining WP2 follow-up for cooperative cancellation and aborted-query metadata.
- `2026-04-20`: `QOL-WP1` through `QOL-WP5` are complete.

## Verified

- `2026-04-23`: WP2 hardening passed targeted guard tests, the broad `*Policy*/*Exposure*/*Telemetry*/*Natural*/*SqlLike*` Maven slice, and `scripts/check-doc-consistency.ps1`.
- `2026-04-22`: Repo-vs-value review passed `mvn -B -ntp test`.

## Release

- `2026.04.17.1834` is the latest cut.

## Risks

- SQL-like is the public default; natural remains controlled grammar; fluent stays internal.
- WP2 still needs cancellation/aborted-query follow-up before governance is fully closed.

## Next

- Finish the remaining WP2 follow-up before treating governance as complete.
- Then move to `STRAT-WP3` or release preparation.
- Keep `CSV-WP6`, correlated/scalar subqueries, and broad window-frame parity out of scope.
