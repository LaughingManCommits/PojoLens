# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP39 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: Orchestrator workers now use a tracked skill registry in `ai/orchestrator/skills/registry.json`; task plans may add task-local `skills`, and the router merges task skills, agent defaults, and bounded inferred skills for docs/release/benchmark/orchestrator work.
- `2026-05-02`: Orchestrator role prompts are now file-backed via `ai/orchestrator/agents/<role>/prompt.md`; keep `agents.json` for structured settings and use inline `prompt` only as a compatibility path.
- `2026-05-01`: Parallel docs and salary-range quickstart proofs are promoted; repo-scope post-promotion validation remains required for coding runs.
- `2026-05-01`: Next queue is WP39 low-cost worker tuning, then WP40 end-to-end coding reliability.

## Facts
- `2026-05-02`: `scripts/ai/pojo_lens_agents/skill_router.py` owns tracked skill-registry loading, explicit-skill validation when a nearby registry exists, bounded path-based inference, and per-task resolved skill merging.
- `2026-05-02`: Validate/run/manifest surfaces now expose resolved task skills, and worker invocations build task-specific selected-agent payloads so task-local skill additions do not require duplicating agent definitions.
- `2026-05-02`: `scripts/ai/pojo_lens_agents/task_plan_ops.py` resolves relative `promptFile` entries from `agents.json`, rejects ambiguous inline-plus-file prompt definitions, and still accepts inline `prompt` for compatibility.
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
