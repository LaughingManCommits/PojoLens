# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: remove the in-repo multi-agent/orchestrator stack now that it lives in `neon`.
4. Keep the repo-memory helper scripts unless the memory workflow is being replaced too.

## Focus
- `2026-05-18`: `TODO.md` no longer tracks the old orchestrator WPs; it now tracks the `neon` extraction cleanup.
- `2026-05-18`: Highest-value removal targets are `scripts/ai/pojo_lens_agents/**`, `scripts/ai/claude-orchestrator.*`, `pyproject.toml`, `ai/orchestrator/**`, orchestrator-only tests under `scripts/tests/`, and retained run data under `.claude-orchestrator/**` and `runs/**`.
- `2026-05-18`: `ai/core/repo-purpose.md` still describes PojoLens as a Java library; the stale part was the old state and backlog, not the core product definition.

## Facts
- `2026-05-18`: `pyproject.toml` still packages `pojolens-agents` from `scripts/ai/pojo_lens_agents` and exposes the `pojolens-agents` CLI.
- `2026-05-18`: `ai/orchestrator/` still contains tracked agents, skills, tasks, pricing, and operator docs for the extracted system.
- `2026-05-18`: `scripts/ai/refresh-ai-memory.*` and `scripts/ai/query-ai-memory.*` are the remaining repo-memory helpers and should be evaluated separately from the extracted orchestrator runtime.

## Validate
- After removal work: `mvn -B -ntp test`.
- Re-run only the Python validations that still apply to surviving repo-memory tooling.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `pyproject.toml`, `scripts/ai/*`
