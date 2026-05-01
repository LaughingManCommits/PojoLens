# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP34 is complete. Retained runs now support `export-trace`, which writes `pojo-lens-orchestrator-trace/v1` JSON spans for run, batch, task, validation, and approval checkpoints without changing manifest schema.
- `2026-05-01`: WP36 is fully complete. `scripts/ai/claude-orchestrator.py` is a 50-line shim, `pojo_lens_agents.orchestrator_app` is down to 863 lines, and parser/contracts/utils/plan/review-provider support now live in focused `pojo_lens_agents` modules.
- `2026-05-01`: WP33 is complete. Retained runs now expose approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints in the manifest.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, `evaluate-corpus`, and the tracked eval fixture.
- `2026-05-01`: Effort hardening added `--effort` overrides plus retained effort/source visibility and `evaluate-run` effort-fit warnings.
- `2026-05-01`: Roadmap queue is now deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: WP34 trace export passed `py -3 -m py_compile scripts/ai/pojo_lens_agents/trace_export.py scripts/ai/pojo_lens_agents/cli_parser.py scripts/ai/pojo_lens_agents/orchestrator_app.py scripts/tests/test_claude_orchestrator.py`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (131 tests), and focused CLI checks including `scripts/ai/claude-orchestrator.ps1 export-trace ... --json`.
- `2026-05-01`: The final WP36 split passed `py -3 -m py_compile scripts/ai/pojo_lens_agents/orchestrator_app.py scripts/ai/pojo_lens_agents/orchestrator_contracts.py scripts/ai/pojo_lens_agents/cli_parser.py scripts/ai/pojo_lens_agents/orchestrator_utils.py scripts/ai/pojo_lens_agents/plan_support.py scripts/ai/pojo_lens_agents/workspace_run_review.py scripts/ai/pojo_lens_agents/provider_worker.py scripts/ai/pojo_lens_agents/command_dispatch.py`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests), and `scripts/docs/check-doc-consistency.ps1`. `pojo_lens_agents.orchestrator_app` is now 863 lines.
- `2026-05-01`: Earlier WP36 prompt/execution/manifest and planner/runtime-admin splits remain green; see `ai/state/recent-validations.md` for the intermediate cut points.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is deferred WP18 -> Release Gate.
- `2026-05-01`: Next decision is whether to keep WP18 deferred or burn down the remaining release risk first.
