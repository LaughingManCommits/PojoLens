# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is reopened; `WP10` is complete, so start with `WP13` chained live proof and keep release work closed.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-07`: `WP10` is complete; downstream `copy` or `worktree` tasks can opt into `dependencyMaterialization = "apply-reviewed"`, reviewed dependency layers replay into downstream workspaces, and task/manifests/review output now record `dependency_layers_applied`.
- `2026-04-07`: added tracked sample plan `ai/orchestrator/tasks/example-materialized-chain.json`; the guide/spec now document when to stay summary-only versus when to opt into reviewed dependency materialization.
- `2026-04-07`: `WP9` is complete; tracked plans now use `readPaths`/`writePaths`, copy-mode scope validation fails missing or directory read inputs, overlapping write tasks serialize by declared `writePaths`, and workspace audit now fails out-of-scope edits.
- `SPIKE-AI-MULTI-AGENT.md` now records `WP10` complete and moves the next open orchestration slice to `WP13`, then `WP11`, then `WP12`.
- The current coordinator is proven for isolated implementer/review/promotion flows, but chained implementer proof is still missing.
- Claude orchestration uses repo-local `.claude-orchestrator/`; review/export/promote/retry/cleanup and `validate-run` are live.
- Live workers are structured-intent-only; raw worker `validationCommands` survive only in old manifests or review-time `validate-run`.
- `WP6` proved the live end-to-end path, `WP7` added reviewer diff previews plus task-workspace `validate-run`, and `WP8` added deterministic invalid-plan and worktree failure coverage.

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
