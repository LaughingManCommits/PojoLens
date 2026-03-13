# Repository Agent Instructions

This repository uses persistent AI memory under `/ai`.

Read `ai/AGENTS.md` before modifying `/ai`.

## Default Load Order

Load only this hot context before starting work:

1. `ai/core/agent-invariants.md`
2. `ai/core/repo-purpose.md`
3. `ai/state/current-state.md`
4. `ai/state/handoff.md`

These files must stay short and task-oriented.

## Load Additional Context Only When Relevant

- Architecture, package layout, or code navigation:
  `ai/core/module-index.md`, `ai/core/architecture-map.md`, `ai/core/system-boundaries.md`, `ai/indexes/files-index.json`, `ai/indexes/symbols-index.json`
- Documentation work or drift checks:
  `ai/core/readme-alignment.md`, `ai/core/documentation-index.md`, `ai/indexes/docs-index.json`
- Validation, CI, tests, or benchmark workflows:
  `ai/core/runbook.md`, `ai/core/test-strategy.md`, `ai/indexes/test-index.json`, `ai/indexes/config-index.json`
- Repository discovery or uncommon repo facts:
  `ai/core/discovery-notes.md`
- Historical memory changes:
  `ai/log/events.jsonl`

Do not auto-load cold context unless the task needs it.

## Memory Freshness

`ai/memory-state.json` records the last full memory rebuild.

If repository structure or durable behavior changes:

1. update affected core files
2. regenerate affected files in `ai/indexes/`
3. refresh `ai/state/current-state.md`
4. log the significant event in `ai/log/events.jsonl`

## Trust Order For Facts

When evidence conflicts, prefer:

1. runtime code in `src/main/java`
2. build and CI config
3. tests in `src/test/java` and `src/test/resources`
4. repository markdown docs
5. `/ai` memory

If `/ai` contradicts code or tests, fix `/ai`.

## Repository Rules

Treat this repository as a single Java library artifact (`jar`), not a deployable service.

- Recheck public-doc claims against code and tests.
- Use `TODO.md` as the planning file.
- Ignore `target/` when deriving repository truth except for generated-artifact validation.

## Session Completion

Before ending work:

1. update `ai/state/current-state.md`
2. update `ai/state/handoff.md`
3. append only significant events to `ai/log/events.jsonl`
4. regenerate indexes if structure changed
5. ensure hot and cold memory still match repository reality
