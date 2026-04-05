# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919`.

## Focus

- Main repo-wide task is still release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` isolated reflection-heavy SQL-like chart mapping.
- `2026-04-05`: Claude orchestration now records prompt sections, enforces prompt budgets, uses true sparse-copy workspaces, serializes overlapping write scopes, audits workspace diffs against protected paths, exposes `review`, exports unified patches, supports `promote`/`retry`/`cleanup`/`validate-run`, has regression coverage for malformed worker output plus fail-fast/dependency blocking, and now preserves structured validation intents plus unknown-vs-empty worker-result semantics in manifests and handoff.
- `2026-04-03`: the natural-query surface now covers vocabulary, grouped aggregates, time buckets/charts, joins, windows/`qualify`, templates, computed fields, report wrappers, and bounded grammar aliases; `docs/natural.md` is the canonical guide.

## Release

- Central namespace is verified; the last publish failed signature verification before key propagation finished.

## Risks

- Central publish status is still not reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Natural-query surface is still narrower than SQL-like: `qualify` is alias-only, running windows use a fixed frame, and joined authoring remains explicit.
- Natural `schema(...)` is still structural, not vocabulary-resolved.
- Natural execution still rebuilds a resolved `SqlLikeQuery` per execution/explain call.
- Claude live sample runs now stay within the current prompt ceilings, but cost is still driven more by worker exploration and verbose structured output than by prompt text alone.
- Claude orchestration now covers review/export/promote/retry/cleanup/validate-run; validation defaults to completed tasks, supports structured `repo-script` / `tool` intents, normalizes safe raw commands onto the same argv path, rejects unsafe raw commands by default, and preserves unknown-vs-empty worker list semantics.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- If natural follow-up resumes, the adjacent gaps are alias-only `qualify`, fixed running windows, structural `schema(...)`, and per-call resolved-delegate rebuilds.
- Keep README onboarding balanced across query styles and push deep recipes into docs.
- For the AI orchestration spike, decide whether raw validation commands should become compatibility-only, then consider widening intent kinds or any further worker-result unknown semantics.
- If limitation-reduction work starts, the current spike recommendation is time-bucket input broadening first, then grouped/aggregate subquery widening.
- If natural-query traffic becomes hot, evaluate caching resolved delegates by execution shape.
