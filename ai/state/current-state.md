# Current State

## Repository Health

- Repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- Branch is `main` and the working tree is clean at `b074644` (`latest benchmarks result`).
- `TODO.md` currently reports: `No active work items.`
- Latest local validation on `2026-03-20`: `mvn -q test` passed with `488` tests.
- Java lint baseline was refreshed on `2026-03-20` to `11,513` entries in `scripts/checkstyle-baseline.txt`; baseline gate now passes with `new=0` and `fixed=0`.
- Core benchmark thresholds were rebaselined from CI on `2026-03-19` using `ceil(score * 1.5, 0.1ms)` and are still the active guardrail source in `benchmarks/thresholds.json`.

## Latest Landed Work (2026-03-20)

- Benchmark sources and docs were updated in `b074644` to execution-only methodology:
  - benchmark query/setup construction now happens in `@Setup`
  - `@Benchmark` methods measure execution (`filter`, `filterGroups`, `join`, `chart`, etc.) rather than setup + execution together
- `docs/benchmarking.md` now documents this methodology explicitly and updates representative strict-suite context.
- Existing warmed `-prof gc` computed-join comparison numbers in docs are marked as old-methodology values and should be refreshed before reuse as profiling baselines.

## Active Work

- No active implementation work is currently in progress.

## Next Tasks

- If performance work resumes, keep WP19 parked unless there is a materially new structural hypothesis.
- Reopen WP18 only with fresh scatter/chart profiling that isolates a chart-specific bottleneck.
- Refresh warmed `-prof gc` computed-join baselines under the new execution-only methodology before using those values for profiling guidance.
- After any code change, rerun focused regressions plus `mvn -q test`.

## Current Risks

- Hot memory previously referenced uncommitted 2026-03-19 follow-ups; those are now committed and should no longer be treated as pending.
- Benchmark interpretation can drift if old setup-bundled and new execution-only numbers are mixed in the same decision.
