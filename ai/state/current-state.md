# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration spike is reopened; `WP10` dependency-state materialization is complete and `WP13` chained live proof is next.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-07`: orchestration `WP10` is complete; downstream `copy` or `worktree` tasks can opt into `dependencyMaterialization = "apply-reviewed"`, reviewed dependency layers are replayed into downstream workspaces, applied layers are recorded in task/manifests/review surfaces, and ambiguous direct-dependency overlaps are rejected.
- `2026-04-07`: added tracked sample plan `ai/orchestrator/tasks/example-materialized-chain.json` and updated the orchestrator guide/spec to document summary-only versus `apply-reviewed` dependency handoff.
- `2026-04-07`: orchestration `WP9` is complete; task plans now use explicit `readPaths`/`writePaths`, copy-mode scope validation fails missing or directory read inputs, write-scope conflicts serialize by declared `writePaths`, and workspace audit now fails out-of-scope edits.
- `2026-04-06`: time buckets now accept `java.util.Date` plus common `java.time` inputs, and `ReflectionUtil` exposes those fields as queryable schema leaves.
- `2026-04-06`: orchestration baseline is proven through the `WP6` live proof, `WP7` reviewer diff previews plus task-workspace `validate-run`, and `WP8` deterministic failure-path coverage.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide; the surface covers grouped aggregates, charts/time buckets, joins, windows, templates, computed fields, reports, and bounded aliases.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration still lacks a stronger live proof for chained implementer work that exercises reviewed dependency materialization end to end.
- Claude orchestration still has no run-level budget stop; worker output and cache reads remain the main cost drivers.

## Next

- For the AI orchestration spike, start with `WP13` chained live proof, then resume/inventory/retention in `WP11`, then run-level budget governance in `WP12`.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
