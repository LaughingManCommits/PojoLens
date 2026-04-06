# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- Main repo-wide task is still release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` isolated reflection-heavy SQL-like chart mapping.
- `2026-04-05`: Claude orchestration now has prompt budgets, sparse-copy workspaces, overlap serialization, protected-path audits, review/export/promote/retry/cleanup/validate-run, structured validation intents, unknown-vs-empty worker lists, slop-status progress, agent-level intent-only defaults for tracked worker roles, compat-task reporting plus strict `--require-intents-only-workers` gates, and intent-only schemas that block raw legacy validation-command items.
- `2026-04-03`: the natural-query surface now covers grouped aggregates, time buckets/charts, joins, windows/`qualify`, templates, computed fields, report wrappers, and bounded aliases; `docs/natural.md` is the canonical guide.

## Release

- Central namespace is verified; the last publish failed signature verification before key propagation finished.

## Risks

- Central publish status is still not reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Natural-query surface is still narrower than SQL-like: `qualify` is alias-only, running windows use a fixed frame, and joined authoring remains explicit.
- Natural `schema(...)` is still structural, not vocabulary-resolved.
- Natural execution still rebuilds a resolved `SqlLikeQuery` per execution/explain call.
- Claude live sample runs stay within the current prompt ceilings, but cost is still driven more by worker exploration and verbose structured output than by prompt text alone.
- Claude orchestration still only supports `repo-script` / `tool` validation intents, so task-level `compat` remains the escape hatch when an edit task cannot express its validation suggestion as a structured intent.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If natural follow-up resumes, the adjacent gaps are alias-only `qualify`, fixed running windows, structural `schema(...)`, and per-call resolved-delegate rebuilds.
- Keep README onboarding balanced across query styles and push deep recipes into docs.
- For the AI orchestration spike, execute the concrete work packages in `SPIKE-AI-MULTI-AGENT.md`: `WP4` remove raw worker `validationCommands` from the live contract, `WP6` run a non-trivial live workflow proof, and touch `WP5` only if that live proof exposes a real missing intent shape.
- If limitation-reduction work starts, the current spike recommendation is time-bucket input broadening first, then grouped/aggregate subquery widening.
- If natural-query traffic becomes hot, evaluate caching resolved delegates by execution shape.
