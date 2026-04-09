# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-09`: bounded CSV support started with a typed loader slice; `PojoLensCsv` now loads UTF-8 CSV into typed rows without changing the POJO-first engine story.
- AI orchestration tracked spike work is complete through `WP16`; optional `WP17` now tracks a practical CSV slice with dry-run-calibrated `runPolicy`, but no live run yet.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-09`: `PojoLensCsv` and `CsvOptions` landed with strict header-based typed loading, row/column-aware coercion errors, nested-path projection reuse, public API coverage, and aligned README/entry-point/product-surface docs.
- `2026-04-09`: tracked plan `wp17-csv-typed-loader-slice.json` validates cleanly as a lean `implementer -> reviewer` DAG with `runBudgetUsd = 2.0` and a `1482`-token dry-run prompt estimate.
- `2026-04-08`: retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe` proved between-batch `runPolicy` stop on real usage; the first task cost `$0.079901` and blocked the second.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Claude orchestration `runPolicy` stop is between-batch only; `WP17` gives a practical dry-run baseline, but live cost ceilings still are not empirically tuned on a write-capable plan.

## Next

- For AI orchestration follow-up, only run or tune `wp17-csv-typed-loader-slice.json` live if the extra empirical budget calibration is worth the cost.
- If CSV follow-up resumes, keep it in Phase 1 adapter territory: narrow coercion policy or explicit schema only after the typed loader settles.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
