# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-09`: bounded CSV support started with a typed loader slice; `PojoLensCsv` now loads UTF-8 CSV into typed rows without changing the POJO-first engine story.
- AI orchestration tracked spike work is complete through `WP17`; post-`WP17` live proof now confirms accepted `tool: mvn` validation intents on the representative CSV plan.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-09`: `PojoLensCsv` and `CsvOptions` landed with strict typed loading, row/column-aware errors, nested-path projection reuse, public API coverage, and aligned docs.
- `2026-04-09`: retained live run `20260409T125416Z-wp17-csv-typed-loader-slice-b41544f3` completed the lean `implementer -> reviewer` CSV slice with `governanceStatus = ok`, `totalCostUsd = 0.93547`, `1599` estimated prompt tokens, and `10987 B` total artifacts.
- `2026-04-09`: worker validation-intent guidance is now explicit in `scripts/claude-orchestrator.py` and `ai/orchestrator/agents.json`: mirror approved hints, keep `repo-script` to `scripts/...` or `mvnw(.cmd)`, use `tool` for approved executables, and emit `[]` instead of invented scripts.
- `2026-04-09`: retained live run `20260409T150845Z-wp17-csv-typed-loader-slice-e16f357e` plus coordinator `validate-run --include-status failed --intents-only` proved the repaired validation path: workers now mirror the CSV plan's Maven hint as `tool: mvn ...`, and coordinator-side execution resolves PATH-backed wrappers like `mvn.cmd`.
- `2026-04-08`: retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe` proved between-batch `runPolicy` stop on real usage; the first task cost `$0.079901` and blocked the second.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Representative live plans can still underdeclare write scope; the latest `WP17` proof failed on out-of-scope `docs/csv.md` even though the validation intent was accepted.

## Next

- For AI orchestration follow-up, only revisit this area if you want to widen tracked write scopes like `WP17`'s missing `docs/csv.md`, or run another representative live plan.
- If CSV follow-up resumes, keep it in Phase 1 adapter territory: narrow coercion policy or explicit schema only after the typed loader settles.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
