# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP17`; the representative follow-up now has one retained live CSV run.
- The first CSV adapter slice is now in the repo as a bounded typed loader via `PojoLensCsv`.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-09`: `PojoLensCsv` plus `CsvOptions` landed as a CSV adapter with strict header-based typed loading, row/column-aware errors, nested-path projection support, and public API/docs coverage.
- `2026-04-09`: retained live run `20260409T125416Z-wp17-csv-typed-loader-slice-b41544f3` completed both tasks with `governanceStatus = ok`, `totalCostUsd = 0.93547`, and `10987 B` of artifacts; the implementer made no edits because the slice was already landed.
- `2026-04-09`: worker validation-intent guidance now tells workers to mirror approved validation hints, use `repo-script` only for `scripts/...` or `mvnw(.cmd)`, use `tool` only for approved executables, and emit `[]` instead of inventing `grep` or pseudo scripts.
- `2026-04-08`: retained live run `20260408T184403Z-wp16-live-run-policy-proof-4f22bffe` proved between-batch `runPolicy` stop on real usage; the first task cost `$0.079901` and blocked the second task.
- `2026-04-07`: task-workspace `validate-run` still fails when sparse copy hydration omits runtime-loaded files.
- `2026-04-07`: reviewer false positives remain a live risk.
- Live workers are structured-intent-only; `validationCommands` survive only in old manifests or review-time `validate-run`, and the new prompt guidance still needs fresh live run proof if we want empirical confirmation that worker suggestions improved.

## Validate

- After code changes: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"`
- After docs or process changes: `scripts/check-doc-consistency.ps1`
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `scripts/refresh-ai-memory.ps1 -Check`

## Cold Pointers

- routing/process: `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`
- limitation spike: `SPIKE-LIMITATIONS.md`
- AI orchestration spike: `SPIKE-AI-MULTI-AGENT.md`, `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`, `scripts/claude-orchestrator.py`
- CSV spike and starter surface: `SPIKE-CSV.md`, `pojo-lens/src/main/java/laughing/man/commits/PojoLensCsv.java`, `pojo-lens/src/test/java/laughing/man/commits/PojoLensCsvTest.java`
- natural-query code/tests: `pojo-lens/src/main/java/laughing/man/commits/natural/`, `pojo-lens/src/main/java/laughing/man/commits/natural/parser/`, `pojo-lens/src/test/java/laughing/man/commits/natural/`
- runtime/public-API coverage: `pojo-lens/src/test/java/laughing/man/commits/publicapi/`
- natural docs/examples guide: `docs/natural.md`, `pojo-lens/src/test/java/laughing/man/commits/natural/NaturalDocsExamplesTest.java`
