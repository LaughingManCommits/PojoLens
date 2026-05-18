# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-18`: The local multi-agent/orchestrator code has moved to the separate `neon` codebase.
- `2026-05-18`: `TODO.md` was reset from the stale WP roadmap to a cleanup backlog focused on removing in-repo orchestrator assets.
- `2026-05-18`: Removed `pyproject.toml`, `scripts/ai/claude-orchestrator.*`, `scripts/ai/pojo_lens_agents/**`, `scripts/ai/pojolens_agents.egg-info/**`, and the orchestrator-only Python tests.
- `2026-05-18`: Removed legacy `ai/orchestrator/**`, repo-local `.claude-orchestrator/**`, `runs/**`, and `ai/state/run-ledger.jsonl`, then stripped stale runtime references out of the surviving repo-memory scripts.

## Verified
- `2026-05-18`: `py -3 -m unittest scripts.tests.test_refresh_ai_memory`, `scripts/docs/check-doc-consistency.ps1`, `scripts/ai/refresh-ai-memory.ps1`, and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after removing the remaining extracted control-plane and retained run artifacts and cleaning the surviving repo-memory scripts/docs.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `ai/state/recent-validations.md`, `CHANGELOG.md`, and archived history still contain many references to the removed local runtime because they preserve past work history.

## Next
- `2026-05-18`: Decide whether any historical orchestrator references should be trimmed further or left as intentional project history.
