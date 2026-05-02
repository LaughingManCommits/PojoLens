# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP39 is complete. The orchestrator now resolves lean `outputProfile`, adds docs-oriented low-cost worker/reviewer profiles, tightens worker JSON caps, and surfaces retained verbosity warnings.
- `2026-05-02`: `example-cheap-proof-docs.json` is the tracked low-cost proof plan, and the quickstart docs parallel sample now uses the lean docs profiles.
- `2026-05-02`: Orchestrator prompt-size policy is explicit: agent prompts warn above `6 KB` and fail above `8 KB`, skill files warn above `3 KB` and fail above `4 KB`, and validate warns when a task resolves more than `4` skills.
- `2026-05-02`: Next queue is WP40 end-to-end coding reliability, then deferred WP18 and Release Gate.

## Facts
- `2026-05-02`: `resolve_output_profile(...)` in `plan_support.py` merges task-level plus agent-default output profiles and records whether each task profile came from the task, agent, or fallback default.
- `2026-05-02`: `prompt_contracts.py`, `provider_worker.py`, and `worker_contracts.py` enforce lean output discipline through lower caps plus an explicit prompt section, and retained summaries warn when lean tasks still produce large summaries, notes, or follow-ups.
- `2026-05-02`: `docs-implementer` and `docs-reviewer` are tracked in `ai/orchestrator/agents.json` with `modelProfile = simple`, `effort = low`, `outputProfile = lean`, and `docs` plus `caveman` skills.
- `2026-05-02`: Task/agent skill routing still comes from the tracked registry, bounded inference, and task-specific selected-agent payloads.
- `2026-05-02`: `TODO.md` order is WP40, deferred WP18, Release Gate (WP39 complete).
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`
- `README.md`, `RELEASE.md`, `.github/workflows/*`
