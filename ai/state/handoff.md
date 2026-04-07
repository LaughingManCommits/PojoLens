# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is reopened; `WP9` is complete, so start with `WP10` dependency-state materialization and keep release work closed.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-07`: `WP9` is complete; tracked plans now use `readPaths`/`writePaths`, copy-mode scope validation fails missing or directory read inputs, overlapping write tasks serialize by declared `writePaths`, and workspace audit now fails out-of-scope edits.
- `2026-04-07`: `SPIKE-AI-MULTI-AGENT.md` now records `WP9` complete and keeps `WP10` through `WP13` open.
- The current coordinator is proven for isolated implementer/review/promotion flows, but it still lacks dependency-state handoff for downstream implementers.
- `SPIKE-LIMITATIONS.md` now effectively advances to grouped/aggregate subquery widening as the next execution slice.
- Claude orchestration uses repo-local `.claude-orchestrator/`; review/export/promote/retry/cleanup and `validate-run` are live.
- Live workers are structured-intent-only; raw worker `validationCommands` survive only in old manifests or review-time `validate-run`.
- `WP6` proved the live end-to-end path, `WP7` added reviewer diff previews plus task-workspace `validate-run`, and `WP8` added deterministic invalid-plan and worktree failure coverage.
- Validation-intent widening (`WP5`) still stays deferred unless a later live run exposes a real unsupported intent shape.

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
