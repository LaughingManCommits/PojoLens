# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: only optional historical cleanup remains after the `neon` extraction.
4. Keep the repo-memory helper scripts unless the memory workflow is being replaced too.

## Focus
- `2026-05-18`: `TODO.md` no longer tracks the old orchestrator WPs; it now tracks the `neon` extraction cleanup.
- `2026-05-18`: The package/CLI slice is already removed: `pyproject.toml`, `scripts/ai/claude-orchestrator.*`, `scripts/ai/pojo_lens_agents/**`, `scripts/ai/pojolens_agents.egg-info/**`, and orchestrator-only tests under `scripts/tests/` are gone.
- `2026-05-18`: `ai/orchestrator/**`, repo-local `.claude-orchestrator/**`, `runs/**`, and `ai/state/run-ledger.jsonl` are also gone.
- `2026-05-18`: Remaining cleanup is limited to optional historical references in docs, changelog history, and validation history.
- `2026-05-18`: `ai/core/repo-purpose.md` still describes PojoLens as a Java library; the stale part was the old state and backlog, not the core product definition.

## Facts
- `2026-05-18`: The surviving `scripts/ai/` tools are the repo-memory helpers: `refresh-ai-memory.*`, `query-ai-memory.*`, and `benchmark-ai-memory.*`.
- `2026-05-18`: The repo-memory refresh/query helpers no longer index or classify removed orchestrator paths.
- `2026-05-18`: `scripts/tests/test_refresh_ai_memory.py` is the remaining Python test coverage after the orchestrator-only suite was removed.

## Validate
- After removal work that changes Java/runtime behavior: `mvn -B -ntp test`.
- Re-run only the Python validations that still apply to surviving repo-memory tooling.
- After AI memory edits: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `TODO.md`, `CHANGELOG.md`
- `README.md`, `scripts/ai/*`, `ai/state/recent-validations.md`
