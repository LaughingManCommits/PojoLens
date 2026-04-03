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
- Natural queries now support grouped aggregates, chart/time-bucket phrases, explicit joins, deterministic windows, alias-based `qualify`, and `NaturalTemplate`.
- Natural joins reuse `JoinBindings` / `DatasetBundle` with source labels.
- Natural explain adds resolved field/sql-like and chart metadata.
- Natural grammar now tolerates bounded sugar: `show me`, articles, and `containing`/`starting with`/`ending with`.
- Direct `PojoLensNatural.parse(...)` / `template(...)` stay runtime-vocabulary-free; runtime-owned vocabulary/computed fields apply through `PojoLensRuntime.natural()`.
- `docs/natural.md` is the canonical natural-query guide.
- `SPIKE.md` is complete on planned scope; reusable natural report wrappers now exist through `ReportDefinition.natural(...)`.
- Start `SPIKE-AI-MULTI-AGENT.md` with one small live `copy`-mode proof run.
- `SPIKE-LIMITATIONS.md` is the root decision doc for reducing current limits; it recommends time-bucket input broadening first, then grouped/aggregate subquery widening.
- Latest SQL-like scatter profile points at reflection-heavy chart mapping.
- Claude orchestration uses repo-local `.claude-orchestrator/`; live `claude -p` execution is still unverified.

## Validate

- After code changes: `mvn -q test`
- Natural template/computed-field slice last validated with `mvn -q -pl pojo-lens test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`
- For release-path changes: `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- limitation spike: `SPIKE-LIMITATIONS.md`
- AI orchestration spike: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`, `scripts/claude-orchestrator.py`
- release/process: `ai/core/runbook.md`, `RELEASE.md`, `ai/state/recent-validations.md`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- natural docs/examples guide: `docs/natural.md`, `pojo-lens/src/test/java/laughing/man/commits/natural/NaturalDocsExamplesTest.java`
