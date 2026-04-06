# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- No repo-wide release work is pending.
- Current follow-up focus is AI orchestration `WP8`.

## Verified

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

- For the AI orchestration spike, `WP4`, `WP6`, and `WP7` are complete; `WP5` stays unopened, and `WP8` is now the next package.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work starts, the current spike recommendation is time-bucket input broadening first, then grouped/aggregate subquery widening.
