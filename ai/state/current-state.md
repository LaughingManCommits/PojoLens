# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Current date-based release is `2026.03.28.1919` (`release-2026.03.28.1919`).

## Focus

- Main repo-wide task is still release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` isolated reflection-heavy SQL-like chart mapping.
- `2026-04-01`: Claude orchestration now defaults to repo-local `.claude-orchestrator/`.
- `2026-04-02`: `PojoLensNatural` and `PojoLensRuntime.natural()` now cover vocabulary, grouped aggregates, time buckets/chart phrases, explicit joins, and deterministic windows plus alias-based `qualify`.
- `2026-04-02`: natural resolution stays deterministic: exact matches beat aliases, explain adds resolved field/sql-like metadata, and `docs/natural.md` remains the canonical guide.
- `2026-04-02`: `mvn -q -pl pojo-lens test` and `scripts/check-doc-consistency.ps1` passed after the natural window/`qualify` slice and doc refresh.
- `2026-04-03`: `PojoLensNatural.template(...)` and `NaturalRuntime.template(...)` now add reusable natural parameter-schema binding, and runtime-scoped computed fields are documented and covered in plain wording.
- `2026-04-03`: `mvn -q -pl pojo-lens test` and `scripts/check-doc-consistency.ps1` passed after the natural template/computed-field slice and surface-doc updates.
- `2026-04-03`: `README.md` quick start is rebalanced around one short example each for fluent, SQL-like, and natural queries; broader recipes now route to the docs guides.

## Release

- Central namespace is verified; the last publish failed signature verification before key propagation finished.

## Risks

- Central publish status is still not reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Natural-query surface is still narrower than SQL-like: reusable preset wrappers are not in place, `qualify` is alias-only, running windows use a fixed frame, and joined authoring remains explicit.
- Natural `schema(...)` is still structural, not vocabulary-resolved.
- Natural execution still rebuilds a resolved `SqlLikeQuery` per execution/explain call.
- Live non-interactive Claude worker execution is still unverified.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- Next natural-query slice should probably be reusable natural preset/report-style wrappers now that computed-field phrasing and templates are in place.
- Keep README onboarding balanced across query styles and push deep recipes into the dedicated docs guides.
- If natural-query traffic becomes hot, evaluate caching resolved delegates by execution shape.
- After AI memory edits, rerun `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.
