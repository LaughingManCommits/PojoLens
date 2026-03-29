# Current State

## Repo

- Multi-module Maven Java 17 library: runtime `pojo-lens`, Spring Boot integration modules, and benchmark tooling.
- Runtime consumer artifact remains `io.github.laughingmancommits:pojo-lens:2026.03.28.1919`; `TODO.md` tracks context-loading baseline and operational follow-up.
- Release versioning is date-based: Maven versions use `YYYY.MM.DD.HHmm` and Git tags use `release-<version>`.

## Focus

- `WP9` context-loading P0 work is complete: trigger matrix, conditional load rules, and summarization guardrails are live.
- Highest-priority operational repo task remains Maven Central release retry or verification for `2026.03.28.1919`.

## Verified

- `2026-03-29`: split Spring Boot starter examples into a minimal onboarding app (`examples/spring-boot-starter-quickstart`) and an advanced dashboard reference (`examples/spring-boot-starter-basic`); quickstart tests and docs/memory validations passed.
- `2026-03-29`: clarified context-loading policy to avoid recursive/extra reloads when `ai/state/*` is edited in the current task; cross-references between `AGENTS.md` and `ai/AGENTS.md` are now explicitly non-recursive.
- `2026-03-29`: removed internal process docs from `docs/` (`consolidation-review.md`) and rewrote remaining benchmark `WP*` references to user-facing wording.
- `2026-03-29`: added conditional cold-load matrix and routing fallback guidance to `AGENTS.md` and `ai/AGENTS.md`.
- `2026-03-29`: validated routing on representative release, validation-history, archive-history, and module-routing queries (module routing uses `-Kind ai-core`).
- `2026-03-29`: `scripts/refresh-ai-memory.ps1`, `scripts/refresh-ai-memory.ps1 -Check`, and `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` passed (`top1=1.0`, `top3=1.0`).
- `2026-03-28`: pre-first-release surface cleanup is complete (`PojoLens` facade and compatibility-only overlap removed) and full test/doc checks passed.

## Release

- Central namespace is verified for `io.github.laughingmancommits`.
- The last publish attempt uploaded the bundle but failed signature verification because Central could not find the signer public key; the public key upload is done and retry remains next.

## Risks

- Central publish status is not yet reconfirmed after key propagation.
- Query quality for module-routing intent is sensitive without facet-constrained lookup; use `scripts/query-ai-memory.ps1 -Kind ai-core` for module/architecture retrieval.

## Next

- Retry the release workflow or a manual release dispatch for `2026.03.28.1919`.
- Keep memory indexes fresh after memory/doc changes:
  `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`.

