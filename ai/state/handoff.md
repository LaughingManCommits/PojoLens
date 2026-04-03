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
- Natural resolution is late-bound against source fields plus computed-field names; exact matches beat aliases and ambiguity stays deterministic.
- Natural queries now support grouped aggregates, time buckets/chart phrases, explicit `from ... join ... on ...`, deterministic windows, alias-based `qualify`, and reusable `NaturalTemplate` schemas.
- Natural joins reuse `JoinBindings` / `DatasetBundle` and support optional source labels via `from <source> as <label>`.
- Natural chart phrases drive deterministic no-spec `chart(...)` / bound `chart()` inference from `show` outputs.
- Natural explain adds resolved field/sql-like metadata plus chart metadata; joined explains also carry `joinSourceBindings`.
- Direct `PojoLensNatural.parse(...)` / `template(...)` stay runtime-vocabulary-free; runtime-owned vocabulary and computed fields apply through `PojoLensRuntime.natural()`.
- `docs/natural.md` is the canonical natural-query guide, now aligned with README/entry-point/use-case docs and join/window examples/tests.
- Next natural-query slice is likely reusable natural preset/report wrappers.
- Latest SQL-like scatter profile still points at reflection-heavy chart mapping in `target/benchmarks/2026-04-01-sqllike-profile/`.
- Claude orchestration uses repo-local `.claude-orchestrator/`; live `claude -p` execution is still unverified.

## Validate

- After code changes: `mvn -q test`
- Natural template/computed-field slice last validated with `mvn -q -pl pojo-lens test`
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
