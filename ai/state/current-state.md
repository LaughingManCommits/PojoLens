# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and JMH modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-10`: CSV is complete through `CSV-WP5`; cleanup hardening and guarded load benchmarks also landed, and `CSV-WP6` stays deferred.
- AI orchestration tracked spike work is complete through `WP18`; spike is fully closed.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-10`: CSV load now covers multiline quoted records, runtime-owned defaults, explicit coercion policy, split header diagnostics, logical/data counts, and `CsvLoadException.report()` across preflight plus load failures.
- `2026-04-10`: Shared `ReflectionUtil` now exposes enum leaves, keeping CSV binding aligned with general queryable-field discovery.
- `2026-04-10`: `CsvLoadJmhBenchmark` now measures typed and multiline CSV load cost separately from query execution, `scripts/benchmark-suite-main.args` includes those workloads, and the strict core threshold check passes with the new `LOAD` budgets.
- `2026-04-10`: WP18 closed reviewer-visible new-file materialization: WP17 reviewer now uses `apply-reviewed` with `docs/csv.md` in readPaths.
- `2026-04-09`: worker validation hints now mirror approved entrypoints, and retained `WP17` runs proved accepted `tool: mvn ...`.
- `2026-04-11`: Claude subagent definitions now preserve optional `skills`, and tracked orchestrator agents preload repo-local `caveman` through the generated `--agents` payload.

## Release

- `2026.03.28.1919` is complete.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- No known orchestration gaps; reviewer materialization of new files is now covered by `apply-reviewed`.

## Next

- Orchestration: spike closed through WP18; revisit only if a new product slice reveals an uncovered gap.
- CSV: keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- Limitations: grouped/aggregate subquery widening is the next active engine slice.
