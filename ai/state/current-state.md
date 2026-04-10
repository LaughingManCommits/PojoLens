# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-10`: CSV now tracks `CSV-WP1` through `CSV-WP6`; `CSV-WP3` is complete and `CSV-WP4` coercion policy is next.
- AI orchestration tracked spike work is complete through `WP17`; live proof now confirms accepted `tool: mvn` intents and in-scope CSV doc creation on the representative plan.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-09`: `PojoLensCsv` and `CsvOptions` landed with strict typed loading, nested paths, row/column errors, and aligned docs/tests.
- `2026-04-09`: header-mode CSV loading now rejects missing primitive-backed mapped columns instead of silently defaulting them; nullable object fields may still be omitted and resolve to `null`.
- `2026-04-10`: CSV loading now supports multiline quoted fields across CRLF/LF input, keeps blank lines inside quoted values, normalizes embedded breaks to `\n`, and reports logical record start lines for multiline failures.
- `2026-04-10`: `PojoLensRuntime` now owns CSV defaults via `set/getCsvDefaults(...)`, exposes `runtime.csv().read(...)`, and supports layered overrides through `CsvOptions.toBuilder()`.
- `2026-04-09`: worker validation hints now mirror approved entrypoints, and retained `WP17` runs proved accepted `tool: mvn ...`.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Reviewer tasks on `summary-only` handoff can still miss newly created upstream files.

## Next

- For AI orchestration follow-up, only revisit this area if you want reviewer-visible dependency materialization for new files, or run another representative live plan.
- If CSV follow-up resumes, do `CSV-WP4` next, then `CSV-WP5`; keep `CSV-WP6` deferred unless demand proves otherwise.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
