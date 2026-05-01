# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP38 -> WP39 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: WP37 complete — structured reviewer findings (`severity`: `info`/`warn`/`block`) now carried in worker output, persisted in task records and manifests, and block promotion when severity is `block`; retained runs gain `review-blocked` lifecycle state and severity counts in review summaries.
- `2026-05-01`: Parallel docs live run proved cheap; only README task promoted and docs consistency passed. Salary-range feature and fixup runs are promoted; post-promotion Maven validation and `awaiting_validation` contract hardening in place.
- `2026-05-01`: Next queue: WP38 docs/text guardrails, WP39 low-cost worker tuning, WP40 end-to-end coding reliability.
- `2026-05-01`: Default copy/worktree worker sandboxes now live in an external temp-backed root recorded in `workspacesDir`; manifests and prompts stay under repo-local `.claude-orchestrator`.

## Facts
- `2026-05-01`: `ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json` is the tracked low-cost parallel docs proof for the quickstart example.
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is WP38, WP39, WP40, deferred WP18, Release Gate (WP37 complete).
- `2026-05-01`: `ai/orchestrator/tasks/example-implement-review-quickstart.json` is the smallest tracked implementer-to-reviewer coding sample and targets the Spring Boot quickstart example.
- `2026-05-01`: Parallel salary-range and fixup proofs tracked in `ai/orchestrator/tasks/`.
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
