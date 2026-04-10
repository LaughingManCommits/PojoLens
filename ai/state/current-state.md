# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-10`: CSV is complete through `CSV-WP5`; cleanup hardening and guarded load benchmarks also landed, and `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP17`.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-10`: CSV load now covers multiline quoted records, runtime-owned defaults, explicit coercion policy, split header diagnostics, logical/data counts, and `CsvLoadException.report()` across preflight plus load failures.
- `2026-04-10`: Shared `ReflectionUtil` now exposes enum leaves, keeping CSV binding aligned with general queryable-field discovery.
- `2026-04-10`: `CsvLoadJmhBenchmark` now measures typed and multiline CSV load cost separately from query execution, `scripts/benchmark-suite-main.args` includes those workloads, and the strict core threshold check passes with the new `LOAD` budgets.
- `2026-04-09`: worker validation hints now mirror approved entrypoints, and retained `WP17` runs proved accepted `tool: mvn ...`.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Reviewer tasks on `summary-only` handoff can still miss newly created upstream files.

## Next

- Orchestration: only revisit for reviewer-visible dependency materialization or another live proof.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: grouped/aggregate subquery widening is the next active engine slice.
