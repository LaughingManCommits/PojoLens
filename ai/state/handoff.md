# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- Pending repo-wide task is still release retry or verification.

## Facts

- `PojoLensRuntime` owns natural vocabulary; direct `PojoLensNatural.parse(...)` / `template(...)` stay runtime-vocabulary-free.
- Natural queries now support grouped aggregates, charts/time buckets, joins, windows/`qualify`, templates, computed fields, report wrappers, and bounded aliases; resolution stays deterministic.
- `docs/natural.md` is the canonical natural-query guide.
- Live `example-review.json` now proves the orchestrator end to end in `copy` mode after fixing `claude_command(...)` so variadic tool flags cannot consume the prompt.
- `SPIKE-LIMITATIONS.md` is the root decision doc for reducing current limits; start with time-bucket input broadening, then grouped/aggregate subquery widening.
- Claude orchestration uses repo-local `.claude-orchestrator/`; next on the spike is prompt-budget/output-discipline hardening, then sparse-copy and overlap/path safety.

## Validate

- After code changes: `mvn -q test`
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
