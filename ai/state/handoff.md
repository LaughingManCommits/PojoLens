# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- Pending repo-wide task is still release retry or verification.

## Facts

- `PojoLensNatural` and `PojoLensRuntime.natural()` now provide the natural-query MVP over the shared engine.
- `PojoLensRuntime` owns runtime vocabulary through `setNaturalVocabulary(...)` and `getNaturalVocabulary()`.
- `NaturalVocabulary.builder().field(actualField, aliases...)` is the current alias-registration contract.
- Natural field resolution happens at execution/explain time because the source type is known then.
- Resolution uses source fields plus computed-field names from the attached registry.
- Natural queries now support metric phrases (`count of`, `sum of`, `average of`, `min of`, `max of`) plus `group by` and `having`.
- Exact field matches win before alias lookup.
- Ambiguous and unknown natural terms fail deterministically.
- Contextual natural explain adds `resolvedNaturalFields` and `resolvedEquivalentSqlLike` while preserving structural `equivalentSqlLike`.
- Direct `PojoLensNatural.parse(...)` remains vocabulary-free; runtime-owned vocabulary applies only through `PojoLensRuntime.natural()`.
- Grouped natural queries are covered by parser/runtime/public-API/telemetry tests.
- The next natural-query slice should probably be time-bucket phrases rather than heuristic guessing.
- The latest SQL-like scatter profile still points at reflection-heavy chart mapping in `target/benchmarks/2026-04-01-sqllike-profile/`.
- Claude orchestration uses repo-local `.claude-orchestrator/`; live `claude -p` execution is still unverified.

## Validate

- After code changes: `mvn -q test`
- Grouped natural slice last validated with `mvn -q -pl pojo-lens test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For release-path changes: `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- grouped natural telemetry coverage: `pojo-lens/src/test/java/laughing/man/commits/query/QueryTelemetryTest.java`
- latest SQL-like scatter profile: `target/benchmarks/2026-04-01-sqllike-profile/`
