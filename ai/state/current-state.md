# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-18`: The local multi-agent/orchestrator code has moved to the separate `neon` codebase.
- `2026-05-18`: `TODO.md` was reset from the stale WP roadmap to a cleanup backlog focused on removing in-repo orchestrator assets.
- `2026-05-18`: Removed `pyproject.toml`, `scripts/ai/claude-orchestrator.*`, `scripts/ai/pojo_lens_agents/**`, `scripts/ai/pojolens_agents.egg-info/**`, and the orchestrator-only Python tests.
- `2026-05-18`: The remaining extracted footprint is legacy `ai/orchestrator/**`, retained run data under `.claude-orchestrator/**` and `runs/**`, and historical docs/validation entries that still mention the old in-repo runtime.

## Verified
- `2026-05-18`: `py -3 -m unittest scripts.tests.test_refresh_ai_memory` passed after removing the in-repo orchestrator package and test suite.
- `2026-05-18`: `scripts/docs/check-doc-consistency.ps1` passed after updating repo docs and memory guidance for the extracted runtime.
- `2026-05-18`: `scripts/ai/refresh-ai-memory.ps1` and `scripts/ai/refresh-ai-memory.ps1 -Check` passed after the memory updates.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- Legacy `ai/orchestrator/**` content and retained run directories still exist and can mislead future cleanup work if left in place too long.
- `ai/state/recent-validations.md`, `CHANGELOG.md`, and archived history still contain many references to the removed local runtime.

## Next
- `2026-05-18`: Remove legacy `ai/orchestrator/**` control-plane material, retained run directories, and stale historical references next.
