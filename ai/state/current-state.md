# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-02`: WP39 is complete. The orchestrator now supports lean `outputProfile`, docs-oriented low-cost worker/reviewer profiles, tighter worker JSON caps, and retained verbosity reporting.
- `2026-05-02`: `ai/orchestrator/tasks/example-cheap-proof-docs.json` is the tracked cheap-proof plan, and the quickstart docs parallel sample now uses the lean docs profiles.
- `2026-05-02`: Orchestrator role prompts warn above `6 KB` and fail above `8 KB`, skill files warn above `3 KB` and fail above `4 KB`, and validate topology warns when a task resolves more than `4` skills.
- `2026-05-02`: Orchestrator skills are tracked under `ai/orchestrator/skills/registry.json`, task plans may declare task-local `skills`, and worker runs resolve task-local plus agent-default plus bounded inferred skills into task-specific selected-agent payloads.
- `2026-05-02`: Orchestrator agent definitions support file-backed `promptFile` entries, and the tracked role prompts live under `ai/orchestrator/agents/<role>/prompt.md`.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-02`: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"` passed with 334 tests after WP39 added output-profile routing, lean docs agents, tighter output contracts, and retained verbosity visibility.
- `2026-05-02`: `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-cheap-proof-docs.json --json`, `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel-implement-review-quickstart-docs.json --json`, and `scripts/docs/check-doc-consistency.ps1` passed for the WP39 docs-profile update.
- `2026-05-02`: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"` passed with 325 tests after adding realistic role/skill prompt-size guardrails and resolved-skill-count warnings.
- `2026-05-02`: `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-parallel.json --json` and `scripts/docs/check-doc-consistency.ps1` passed after documenting and enforcing the new role/skill size budgets.
- `2026-05-02`: `py -3 -m unittest discover -s scripts/tests -p "test_*.py"` passed with 315 tests after adding the tracked skill registry/router plus task-level orchestrator skills support.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-02`: Roadmap order is WP40 -> deferred WP18 -> Release Gate.
- `2026-05-02`: WP40 is next: end-to-end coding run reliability across planning, review, promotion, post-promotion validation, and tracked proofs.
