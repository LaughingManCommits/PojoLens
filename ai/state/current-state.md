# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- `2026-04-10`: CSV follow-up now has a `CSV-WP1` through `CSV-WP6` board; `CSV-WP2` multiline quoted-record hardening is next.
- AI orchestration tracked spike work is complete through `WP17`; live proof now confirms accepted `tool: mvn` intents and in-scope CSV doc creation on the representative plan.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-09`: `PojoLensCsv` and `CsvOptions` landed with strict typed loading, row/column-aware errors, nested-path projection reuse, public API coverage, and aligned docs.
- `2026-04-09`: header-mode CSV loading now rejects missing primitive-backed mapped columns instead of silently defaulting them; nullable object fields may still be omitted and resolve to `null`.
- `2026-04-09`: worker validation-intent guidance is now explicit in `scripts/claude-orchestrator.py` and `ai/orchestrator/agents.json`: mirror approved hints, keep `repo-script` to `scripts/...` or `mvnw(.cmd)`, use `tool` for approved executables, and emit `[]` instead of invented scripts.
- `2026-04-09`: retained runs `20260409T150845Z-wp17-csv-typed-loader-slice-e16f357e` and `20260409T160647Z-wp17-csv-typed-loader-slice-f939dec7` proved the repaired validation path and widened `WP17` write scope: coordinator `validate-run` accepted `tool: mvn ...`, PATH-backed wrappers resolved cleanly, and the later run's doc changes were promoted into the repo.
- `2026-04-08`: retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe` proved between-batch `runPolicy` stop on real usage; the first task cost `$0.079901` and blocked the second.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Reviewer tasks on `summary-only` handoff can still miss newly created upstream files; the latest `WP17` proof left the reviewer `blocked` because `docs/csv.md` was not materialized downstream.

## Next

- For AI orchestration follow-up, only revisit this area if you want reviewer-visible dependency materialization for new files, or run another representative live plan.
- If CSV follow-up resumes, do `CSV-WP2` next, then `CSV-WP3` through `CSV-WP5`; keep `CSV-WP6` deferred unless typed-first demand proves insufficient.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
