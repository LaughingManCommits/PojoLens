# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP37 -> WP38 -> WP39 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: Live run `20260501T154703Z-example-parallel-implement-review-quickstart-docs-3fd45a14` proved a cheap real parallel docs flow; only the README task was promoted and docs consistency passed.
- `2026-05-01`: The quickstart salary-range feature is promoted in the repo, and its docs/tests fix-up completed through a second live orchestrator run after post-promotion Maven validation caught a contract mismatch.
- `2026-05-01`: Contract hardening is in place after those runs: reviewer budget warnings, exact-duplicate promotion dedupe, and post-promotion `awaiting_validation` until repo-scope validation is recorded.
- `2026-05-01`: The next queue is broader orchestrator quality work, not a narrow patch: WP37 reviewer/promotion governance, WP38 docs/text guardrails, WP39 low-cost worker tuning, then WP40 end-to-end coding reliability.
- `2026-05-01`: Default copy/worktree worker sandboxes now live in an external temp-backed root recorded in `workspacesDir`; manifests and prompts stay under repo-local `.claude-orchestrator`.

## Facts
- `2026-05-01`: `ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json` is the tracked low-cost parallel docs proof for the quickstart example.
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is WP37, WP38, WP39, WP40, deferred WP18, Release Gate.
- `2026-05-01`: `ai/orchestrator/tasks/example-implement-review-quickstart.json` is the smallest tracked implementer-to-reviewer coding sample and targets the Spring Boot quickstart example.
- `2026-05-01`: `example-parallel-implement-review-quickstart-salary-range.json` is the tracked live proof for a parallel two-implementer plus reviewer quickstart feature slice, and `example-implement-review-quickstart-salary-range-fixup.json` is the follow-up docs/tests correction slice.
- `2026-05-01`: LangGraph is still only a spike in `pojo_lens_agents.langgraph_spike`; it is not a live runtime backend.
- `2026-05-01`: `export-trace` uses retained manifest data only; it does not add new runtime state or change manifest schema.
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
