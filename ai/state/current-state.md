# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration spike is reopened; `WP13` chained live proof is complete and `WP11` resume/inventory/retention is next.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-07`: orchestration `WP13` is complete; live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved a chained same-file `apply-reviewed` flow and downstream-only promotion back into the repo.
- `2026-04-07`: the promoted `WP13` slice added dependency-materialization prompt plus validate/manifest visibility coverage in `scripts/tests/test_claude_orchestrator.py`, and the repo Python suite passed after promotion.
- `2026-04-07`: `WP13` showed two active operator findings: task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files, and reviewer false positives remain a live risk.
- `2026-04-07`: orchestration `WP9` and `WP10` are complete; explicit `readPaths`/`writePaths` plus opt-in `dependencyMaterialization = "apply-reviewed"` are now live.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide; the surface covers grouped aggregates, charts/time buckets, joins, windows, templates, computed fields, reports, and bounded aliases.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration still lacks same-run resume plus inventory for retained runs; operators still have to pivot through retry/new-run flow or manual filesystem inspection.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs; undeclared runtime-loaded files can make pre-promotion validation fail even when the promoted repo patch is healthy.
- Claude orchestration still has no run-level budget stop; worker output and cache reads remain the main cost drivers.

## Next

- For the AI orchestration spike, start with `WP11` resume/inventory/retention, then run-level budget governance in `WP12`.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
