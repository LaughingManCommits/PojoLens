# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration tracked spike work is complete through `WP15`; follow-up should be bounded live `runPolicy` calibration or smaller plans.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-08`: `WP15` is complete; `validate`, run payloads, manifests, and retained-run summaries now expose compact `topology` data and conservative lean-plan warnings.
- `2026-04-08`: tracked `example-review.json` is now a one-task reviewer sample, and `example-parallel.json` now demonstrates parallel analysts without an automatic review hop.
- `2026-04-08`: `WP14` is complete; plans may now declare top-level `runPolicy`, and the coordinator can warn or stop between batches on spend and oversized stdout/stderr/result artifacts while surfacing `runGovernance`.
- `2026-04-08`: worker context now excludes implicit coordinator memory; `copy` workspaces hydrate only declared task files, and worker prompts treat the prompt plus declared workspace as the full contract.
- `2026-04-08`: planner guidance now prefers the smallest viable actor set, and per-task prompts no longer repeat role-stable output discipline.
- `2026-04-07`: `WP11` is complete; `resume`, `inventory`, and `prune` are live, and retained runs resume from their run-local `selected-plan.json` snapshot.
- `2026-04-07`: same-run `resume` preserves completed records and reuses the original run id/runtime directories; `prune` skips incomplete runs by default.
- `2026-04-07`: retained live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved same-file `apply-reviewed` chaining and downstream-only promotion.
- `2026-04-07`: `WP9` and `WP10` are complete; explicit `readPaths`/`writePaths` and opt-in `dependencyMaterialization = "apply-reviewed"` are live.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration same-run `resume` is run continuity, not partial sandbox recovery; resumed `copy` or `worktree` tasks rebuild fresh workspaces before rerun.
- Claude orchestration task-workspace validation still depends on declared sparse-copy inputs for runtime-loaded files.
- Claude orchestration `runPolicy` stop is between-batch only; already-running tasks finish before later tasks are blocked.

## Next

- For AI orchestration follow-up, run one bounded live plan with explicit `runPolicy` thresholds now that governance and topology signals are in place.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
