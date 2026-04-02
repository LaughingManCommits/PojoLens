# Current State

## Repo

- Java 17 library with runtime, Spring Boot, and benchmark modules.
- Runtime artifact: `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`.
- Date-based releases use Maven `YYYY.MM.DD.HHmm` and Git `release-<version>`.

## Focus

- Main repo-wide task is still release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-04-01`: scatter profiling in `target/benchmarks/2026-04-01-sqllike-profile/` isolated reflection-heavy SQL-like chart mapping.
- `2026-04-01`: Claude orchestration now defaults to repo-local `.claude-orchestrator/`.
- `2026-04-02`: `PojoLensNatural` and `PojoLensRuntime.natural()` expose the first controlled natural-query MVP.
- `2026-04-02`: runtime-scoped `NaturalVocabulary` now resolves aliases late against source fields and computed-field names.
- `2026-04-02`: exact field matches win before alias lookup; ambiguous and unknown terms fail deterministically.
- `2026-04-02`: contextual natural explain now adds `resolvedNaturalFields` and `resolvedEquivalentSqlLike`.
- `2026-04-02`: README plus entry-point/product-surface/use-case/API-stability docs were aligned for runtime vocabulary.
- `2026-04-02`: `mvn -q -pl pojo-lens test` and `scripts/check-doc-consistency.ps1` passed after the vocabulary slice.

## Release

- Central namespace is verified; the last publish failed signature verification before key propagation finished.

## Risks

- Central publish status is still not reconfirmed after key propagation.
- SQL-like scatter still allocates more than fluent.
- Natural-query surface is still narrow: no grouped queries, joins, or chart phrases yet.
- Natural `schema(...)` is still structural, not vocabulary-resolved.
- Natural execution still rebuilds a resolved `SqlLikeQuery` per execution/explain call.
- Live non-interactive Claude worker execution is still unverified.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- Next natural-query slice should be grouped queries (`group by`, aggregates, `having`) on top of late-bound vocabulary.
- If natural-query traffic becomes hot, evaluate caching resolved delegates by execution shape.
- After AI memory edits, rerun `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.
