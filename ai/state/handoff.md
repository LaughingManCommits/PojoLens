# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike's tracked packages are complete through `WP16`; if it reopens, focus only on practical `runPolicy` tuning.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-08`: retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe` proved between-batch `runPolicy` stop on real usage; the first task cost `$0.079901` and blocked the second task.
- `2026-04-08`: `WP15` is complete; `validate`, run payloads, manifests, and retained-run summaries now expose compact `topology` data with conservative lean-plan warnings.
- `2026-04-08`: tracked `example-review.json` is now a one-task reviewer sample, and `example-parallel.json` now shows parallel analysts with no automatic review hop.
- `2026-04-08`: `WP14` is complete; plans may now declare top-level `runPolicy`, and the coordinator can warn or stop between batches on spend and oversized stdout/stderr/result artifacts while surfacing `runGovernance`.
- `2026-04-08`: planner guidance now prefers the smallest viable actor set, and per-task prompts no longer repeat role-stable output discipline.
- `2026-04-07`: `WP11` is complete; `resume`, `inventory`, and `prune` are live for retained `.claude-orchestrator` runs.
- `2026-04-07`: retained live run `20260407T120023Z-wp13-live-materialized-prompt-proof-87dda84c` proved same-file `apply-reviewed` chaining and downstream-only promotion.
- `2026-04-07`: task-workspace `validate-run` still fails when sparse copy hydration omits runtime-loaded files.
- `2026-04-07`: reviewer false positives remain a live risk.
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
