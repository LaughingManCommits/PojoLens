# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Use `ai/state/benchmark-state.md` only for benchmark work.
4. Run `scripts/refresh-ai-memory.ps1 -Check` if memory freshness is uncertain.

## Focus

- The orchestration spike is complete through `WP17`; live proof now confirms accepted `tool: mvn` intents and in-scope CSV doc creation.
- The first CSV adapter slice is now in the repo as a bounded typed loader via `PojoLensCsv`.
- If engine follow-up resumes, the next limitation slice is grouped/aggregate subquery widening.

## Facts

- `2026-04-09`: `PojoLensCsv` plus `CsvOptions` landed as a CSV adapter with strict typed loading, row/column-aware errors, nested-path support, and public API/docs coverage.
- `2026-04-09`: header-mode CSV loading now rejects missing primitive-backed mapped columns; nullable object fields may still be omitted and resolve to `null`.
- `2026-04-09`: worker validation-intent guidance now tells workers to mirror approved hints exactly, avoid swapping `mvn` and `mvnw`, keep `repo-script` to `scripts/...` or `mvnw(.cmd)`, use `tool` for approved executables, and emit `[]` instead of invented scripts.
- `2026-04-09`: retained runs `20260409T150845Z-wp17-csv-typed-loader-slice-e16f357e` and `20260409T160647Z-wp17-csv-typed-loader-slice-f939dec7` proved accepted `tool: mvn ...` execution, fixed `docs/csv.md` write scope, and promoted the CSV guide/docs; the remaining live issue is `summary-only` reviewer handoff for new files.
- `2026-04-07`: task-workspace `validate-run` still fails when sparse copy hydration omits runtime-loaded files.
- `2026-04-07`: reviewer false positives remain a live risk.
- Live workers are structured-intent-only; `validationCommands` survive only in old manifests or review-time `validate-run`, and the remaining representative-plan gap is reviewer-visible materialization of new dependency files.

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
