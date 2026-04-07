# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is reopened; `WP13` is complete, so start with `WP11` resume/inventory/retention and keep release work closed.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-07`: worker requests are leaner; minimal prompts omit shared `readPaths`, pass only the selected agent definition, and use stable execution-context labels.
- `2026-04-07`: the tracked `WP13` dry-run prompt estimate fell from `3790` to `3193`; the worker-rules block is now compact and untruncated.
- `2026-04-07`: `WP13` is complete; live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved a chained same-file `apply-reviewed` flow and downstream-only promotion.
- `2026-04-07`: `WP13` added dependency-materialization prompt plus validate/manifest visibility tests in `scripts/tests/test_claude_orchestrator.py`.
- `2026-04-07`: task-workspace `validate-run` still fails when sparse copy hydration omits runtime-loaded files needed by validation.
- `2026-04-07`: reviewer reliability is still a live operational risk; the reviewer falsely claimed the new regression tests were missing.
- `2026-04-07`: `WP9` and `WP10` are complete; explicit `readPaths`/`writePaths` plus opt-in `dependencyMaterialization = "apply-reviewed"` are live.
- Claude orchestration uses repo-local `.claude-orchestrator/`; review/export/promote/retry/cleanup and `validate-run` are live.
- Live workers are structured-intent-only; raw worker `validationCommands` survive only in old manifests or review-time `validate-run`.
- `WP6` proved the live end-to-end path, `WP7` added reviewer diff previews plus task-workspace `validate-run`, `WP8` added deterministic invalid-plan and worktree failure coverage, and `WP13` proved downstream-only promotion from a materialized same-file chain.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- limitation spike: `SPIKE-LIMITATIONS.md`
- AI orchestration spike: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`, `scripts/claude-orchestrator.py`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- natural docs/examples guide: `docs/natural.md`, `pojo-lens/src/test/java/laughing/man/commits/natural/NaturalDocsExamplesTest.java`
