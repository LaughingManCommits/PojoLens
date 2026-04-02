# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- Pending repo-wide task is still release retry or verification.

## Facts

- `PojoLensRuntime` owns natural vocabulary; `NaturalVocabulary.builder().field(actualField, aliases...)` is the alias-registration contract.
- Natural resolution is late-bound against source fields plus computed-field names; exact matches win before aliases.
- Natural queries now support grouped aggregates, time-bucket phrases, and terminal chart phrases.
- Natural chart phrases drive deterministic no-spec `chart(...)` / bound `chart()` inference from `show` outputs.
- Ambiguous and unknown natural terms fail deterministically.
- Contextual natural explain adds resolved field/sql-like metadata plus chart metadata when chart phrases are present.
- Direct `PojoLensNatural.parse(...)` remains vocabulary-free; runtime-owned vocabulary applies only through `PojoLensRuntime.natural()`.
- `docs/natural.md` is now the canonical natural-query guide, with parser/runtime/public-API/docs/telemetry coverage in tests.
- The next natural-query slice should probably be joins/multi-source phrasing.
- The latest SQL-like scatter profile still points at reflection-heavy chart mapping in `target/benchmarks/2026-04-01-sqllike-profile/`.
- Claude orchestration uses repo-local `.claude-orchestrator/`; live `claude -p` execution is still unverified.

## Validate

- After code changes: `mvn -q test`
- Natural time-bucket/chart slice last validated with `mvn -q -pl pojo-lens test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For release-path changes: `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- natural telemetry coverage: `pojo-lens/src/test/java/laughing/man/commits/query/QueryTelemetryTest.java`
- natural docs/examples guide: `docs/natural.md`, `pojo-lens/src/test/java/laughing/man/commits/natural/NaturalDocsExamplesTest.java`
- latest SQL-like scatter profile: `target/benchmarks/2026-04-01-sqllike-profile/`
