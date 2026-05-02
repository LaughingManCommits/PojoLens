# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-02`: Orchestrator skills are now tracked under `ai/orchestrator/skills/registry.json`, task plans may declare task-local `skills`, and worker runs now resolve task-local plus agent-default plus bounded inferred skills into task-specific selected-agent payloads.
- `2026-05-02`: Orchestrator agent definitions now support file-backed `promptFile` entries, and the tracked role prompts moved into `ai/orchestrator/agents/<role>/prompt.md` for per-role customization without inline JSON prompt blobs.
- `2026-05-01`: WP32-WP38 are complete; next queue is WP39 low-cost worker tuning, then WP40 end-to-end coding reliability.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-02`: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"` passed with 315 tests after adding the tracked skill registry/router plus task-level orchestrator skills support.
- `2026-05-02`: `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json --json` now exposes `agentSkills` plus per-task `resolvedSkills`, and `scripts/docs/check-doc-consistency.ps1` passed for the skill-router docs.
- `2026-05-01`: Promoted coding runs remain `awaiting_validation` until a repo-scope validation checkpoint is recorded after promotion.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is WP39 -> WP40 -> deferred WP18 -> Release Gate.
- `2026-05-01`: WP39 is next: low-cost worker profiles, tighter output discipline, and cheaper repeated live-proof runs.
