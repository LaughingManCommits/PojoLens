# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike moves to `WP14` run-level budget/artifact governance; keep release work closed.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-08`: `WP12` is complete; workers no longer inherit `AGENTS.md` / `ai/AGENTS.md` implicitly, and the prompt plus declared workspace is now the worker contract.
- `2026-04-08`: planner guidance now prefers the smallest viable actor set, and per-task worker prompts keep only coordinator/workspace rules while role-stable output discipline lives in the selected agent definition.
- `2026-04-08`: after the explicit worker-contract pass, the tracked `WP13` dry-run prompt estimate is `2745` total tokens.
- `2026-04-07`: `WP11` is complete; `resume`, `inventory`, and `prune` are live for retained `.claude-orchestrator` runs.
- `2026-04-07`: same-run `resume` preserves completed records and reuses the original run id/runtime directories, but resumed `copy` and `worktree` task sandboxes are rebuilt before rerun.
- `2026-04-07`: retained live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved same-file `apply-reviewed` chaining and downstream-only promotion.
- `2026-04-07`: task-workspace `validate-run` still fails when sparse copy hydration omits runtime-loaded files.
- `2026-04-07`: reviewer false positives remain a live risk.
- `2026-04-07`: `WP9` and `WP10` are complete; explicit `readPaths`/`writePaths` and opt-in `dependencyMaterialization = "apply-reviewed"` are live.
- Claude orchestration uses repo-local `.claude-orchestrator/`; review/export/promote/retry/resume/inventory/prune/cleanup and `validate-run` are live.
- Live workers are structured-intent-only; raw worker `validationCommands` survive only in old manifests or review-time `validate-run`.

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
