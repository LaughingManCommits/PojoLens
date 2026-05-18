# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-18`: The local multi-agent/orchestrator code has moved to the separate `neon` codebase.
- `2026-05-18`: `TODO.md` was reset from the stale WP roadmap to a cleanup backlog focused on removing in-repo orchestrator assets.
- `2026-05-18`: The remaining orchestrator footprint still includes `scripts/ai/pojo_lens_agents/**`, `scripts/ai/claude-orchestrator.*`, `pyproject.toml`, `ai/orchestrator/**`, orchestrator-only Python tests, and retained run directories under `.claude-orchestrator/**` and `runs/**`.

## Verified
- `2026-05-18`: No build or test validation was run in this planning and memory-alignment pass.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- Hot memory and `TODO.md` had stale orchestrator-first guidance that no longer matches the user's direction.
- Removing the old orchestrator stack will require coordinated cleanup across packaging, tests, docs, memory, and retained runtime artifacts.

## Next
- `2026-05-18`: Remove package/runtime surfaces first, then delete orchestrator-only tests and docs, then run the surviving validation set.
