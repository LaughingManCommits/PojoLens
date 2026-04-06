# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- Pending repo-wide follow-up is AI orchestration `WP8`; release work is complete and out of scope.

## Facts

- `PojoLensRuntime` owns natural vocabulary; `docs/natural.md` is the canonical natural guide.
- `SPIKE-LIMITATIONS.md` still starts with time-bucket input broadening, then grouped/aggregate subquery widening.
- Claude orchestration uses repo-local `.claude-orchestrator/`; review/export/promote/retry/cleanup and `validate-run` are live.
- Live workers are structured-intent-only; raw worker `validationCommands` survive only in old manifests or review-time `validate-run`.
- `2026-04-06`: `wp6-live-parser-proof.json` completed and promoted a two-file parser/regression patch into the repo.
- `WP6` did not justify widening the intent vocabulary: the implementer's `tool` intent was accepted, while the reviewer's bad `repo-script` intents were rejected by policy.
- `2026-04-06`: `WP7` added reviewer dependency diff previews plus `validate-run --execution-scope task-workspace`, and explicitly deferred overlap overrides, same-run resume, prune helpers, and broader promotion ergonomics.
- Next orchestrator work is `WP8`: expand regression coverage around the larger coordinator surface and the new `WP7` paths.

## Validate

- After code changes: `mvn -q test`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- limitation spike: `SPIKE-LIMITATIONS.md`
- AI orchestration spike: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`, `scripts/claude-orchestrator.py`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- natural docs/examples guide: `docs/natural.md`, `pojo-lens/src/test/java/laughing/man/commits/natural/NaturalDocsExamplesTest.java`
