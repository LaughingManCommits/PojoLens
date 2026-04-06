# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration spike is reopened; start with `WP9` scope contract and enforcement.
- Limitation-reduction follow-up now starts with grouped/aggregate subquery widening.

## Verified

- `2026-04-06`: `SPIKE-AI-MULTI-AGENT.md` now tracks active orchestration packages `WP9` through `WP13`.
- `2026-04-06`: time buckets now accept `java.util.Date` plus common `java.time` inputs, and `ReflectionUtil` exposes those fields as queryable schema leaves.
- `2026-04-06`: orchestration baseline is proven through the `WP6` live proof, `WP7` reviewer diff previews plus task-workspace `validate-run`, and `WP8` deterministic failure-path coverage.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide; the surface covers grouped aggregates, charts/time buckets, joins, windows, templates, computed fields, reports, and bounded aliases.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration still overloads task `files` as read hints, copy hydration inputs, and write scopes.
- Claude orchestration still lacks dependency-state materialization for downstream implementers.
- Claude orchestration still has no run-level budget stop; worker output and cache reads remain the main cost drivers.

## Next

- For the AI orchestration spike, start with `WP9` scope contract and enforcement, then `WP10` dependency-state materialization, then a stronger live proof in `WP13`.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
