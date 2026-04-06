# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- AI orchestration spike hardening packages are complete; `WP5` remains evidence-driven and deferred unless a later live run exposes an unsupported validation intent.
- Limitation-reduction follow-up has moved from time-bucket input broadening to grouped/aggregate subquery widening.

## Verified

- `2026-04-06`: time buckets now accept `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, and `ZonedDateTime` across fluent and SQL-like execution, and `ReflectionUtil` now exposes those `java.time` fields as queryable schema leaves.
- `2026-04-06`: orchestration `WP8` expanded regression coverage with invalid-agent/task-plan negatives plus worktree creation and cleanup failure paths, and explicitly deferred a default live CLI smoke test because the existing `WP6` live proof is the stronger real-path guard.
- `2026-04-06`: orchestration `WP7` implemented reviewer dependency diff previews plus `validate-run --execution-scope task-workspace`, and the spike now explicitly defers overlap-override, same-run resume, prune, and promotion refinements until a later live run justifies them.
- `2026-04-06`: user confirmed the `2026.03.28.1919` release work is done and should not remain in the active task list.
- `2026-04-06`: live `wp6-live-parser-proof.json` ran end to end; review/export/validate-run/promote worked, and the implementer patch was promoted into the repo.
- `2026-04-06`: live orchestrator workers now require structured `validationIntents`; raw worker `validationCommands` remain only in old manifests or review-time validation.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide; the surface covers grouped aggregates, charts/time buckets, joins, windows, templates, computed fields, reports, and bounded aliases.

## Release

- `2026.03.28.1919` is complete; no active release follow-up is tracked in repo memory.

## Risks

- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration reviewer accuracy is improved by diff previews but still needs another live proof beyond the `WP6` sample.
- Claude orchestration cost is still driven more by worker output and cache reads than by prompt size.

## Next

- For the AI orchestration spike, `WP4`, `WP6`, `WP7`, and `WP8` are complete; only `WP5` remains deferred pending a later live run that exposes a real unsupported validation-intent shape.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work resumes, start with grouped/aggregate subquery widening; the time-bucket input broadening slice is complete.
