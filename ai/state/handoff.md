# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP39 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: WP38 complete - docs/text guardrails now block mojibake promotion, warn on new non-ASCII doc text in ASCII baselines, add docs-only validation warnings at plan time, and let `validate-run` synthesize the docs consistency check for docs-only retained changes.
- `2026-05-01`: WP37 complete - reviewer findings persist severity, block promotion when needed, and surface `review-blocked` lifecycle state.
- `2026-05-01`: Parallel docs and salary-range quickstart proofs are promoted; repo-scope post-promotion validation remains required for coding runs.
- `2026-05-01`: Next queue is WP39 low-cost worker tuning, then WP40 end-to-end coding reliability.

## Facts
- `2026-05-01`: `ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json` is the tracked low-cost parallel docs proof for the quickstart example.
- `2026-05-01`: `ai/orchestrator/tasks/example-implement-review-quickstart.json` is the smallest tracked implementer-to-reviewer coding sample.
- `2026-05-01`: Parallel salary-range and fixup proofs are tracked in `ai/orchestrator/tasks/`.
- `2026-05-01`: LangGraph is still only a spike in `pojo_lens_agents.langgraph_spike`; it is not a live runtime backend.
- `2026-05-01`: `export-trace` uses retained manifest data only; it does not add new runtime state or change manifest schema.
- `2026-05-01`: `TODO.md` order is WP39, WP40, deferred WP18, Release Gate (WP38 complete).
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `scripts/ai/pojo_lens_agents/langgraph_spike.py`
- `README.md`, `docs/**`, `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
