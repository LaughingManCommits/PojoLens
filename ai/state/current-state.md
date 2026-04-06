# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- Main repo-wide task is still release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-06`: live `wp6-live-parser-proof.json` ran end to end; review/export/validate-run/promote worked, and the implementer patch was promoted into the repo.
- `2026-04-06`: live orchestrator workers now require structured `validationIntents`; raw worker `validationCommands` remain only in old manifests or review-time validation.
- `2026-04-03`: `docs/natural.md` is the canonical natural guide; the surface covers grouped aggregates, charts/time buckets, joins, windows, templates, computed fields, reports, and bounded aliases.

## Release

- Central namespace is verified; the last publish failed signature verification before key propagation finished.

## Risks

- Central publish status is still not reconfirmed after key propagation.
- Natural gaps remain around alias-only `qualify`, fixed windows, structural `schema(...)`, and per-call resolved delegate rebuilds.
- Claude orchestration reviewers can still produce false positives against changed files.
- `validate-run` still executes from repo root, so it validates the current repo state rather than an unpromoted worker workspace.
- Claude orchestration cost is still driven more by worker output and cache reads than by prompt size.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- For the AI orchestration spike, `WP4` and `WP6` are complete; `WP5` stays unopened, and `WP7` should use the reviewer-accuracy and repo-root validation findings.
- If natural follow-up resumes, start with the remaining `qualify`/window/`schema(...)`/delegate-cache gaps.
- If limitation-reduction work starts, the current spike recommendation is time-bucket input broadening first, then grouped/aggregate subquery widening.
