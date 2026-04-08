# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration now moves to `WP14` run-level budget/artifact governance; `WP12` and `WP13` are complete.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-08`: worker context now excludes implicit coordinator memory; `copy` workspaces hydrate only declared task files, and worker prompts treat the prompt plus declared workspace as the full contract.
- `2026-04-08`: prompt/topology re-evaluation trimmed duplicated worker prompt scaffolding, moved output-discipline back into the selected agent definition, and changed planner guidance to prefer the smallest viable actor set.
- `2026-04-08`: after the explicit worker-contract pass, the tracked `WP13` dry-run prompt estimate is `2745` total tokens.
- `2026-04-07`: `WP11` is complete; `resume`, `inventory`, and `prune` are live, and retained runs resume from their run-local `selected-plan.json` snapshot.
- `2026-04-07`: same-run `resume` preserves completed records and reuses the original run id/runtime directories; `prune` skips incomplete runs by default.
- `2026-04-07`: retained live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved same-file `apply-reviewed` chaining and downstream-only promotion.
- `2026-04-07`: `WP9` and `WP10` are complete; explicit `readPaths`/`writePaths` and opt-in `dependencyMaterialization = "apply-reviewed"` are live.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration same-run `resume` is run continuity, not partial sandbox recovery; resumed `copy` or `worktree` tasks rebuild fresh workspaces before rerun.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Claude orchestration still has no run-level budget stop; cache reads and worker output remain the main cost drivers.

## Next

- For the AI orchestration spike, start with `WP14` run-level budget/artifact governance.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
