# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP36 is complete. `scripts/ai/claude-orchestrator.py` is a 50-line shim; planner flow, runtime admin, prompt/worker contracts, task execution, manifest IO, and task-plan governance now delegate to focused `pojo_lens_agents` modules.
- `2026-05-01`: WP35 is complete. Retained-run summary, review/promote, validation checkpoints, and evals now live in focused `pojo_lens_agents` modules.
- `2026-05-01`: WP33 is complete. Retained runs now expose approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints in the manifest.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, `evaluate-corpus`, and the tracked eval fixture.
- `2026-05-01`: AI memory refresh now stages derived artifacts and `refresh-ai-memory -Check` tolerates in-flight refresh.
- `2026-05-01`: Effort hardening added `--effort` overrides plus retained effort/source visibility and `evaluate-run` effort-fit warnings.
- `2026-05-01`: Roadmap queue is now planned WP34, deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: WP36 prompt/execution/manifest split passed `py -3 -m py_compile`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests), and `scripts/docs/check-doc-consistency.ps1`. `pojo_lens_agents.orchestrator_app` dropped from 4618 to 3792 lines.
- `2026-05-01`: WP36 planner/runtime-admin split passed `py -3 -m py_compile`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests), and `scripts/docs/check-doc-consistency.ps1`. `pojo_lens_agents.orchestrator_app` dropped from 5016 to 4618 lines.
- `2026-05-01`: The compatibility entrypoint is still 50 lines and the focused orchestrator suite remains green.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is planned WP34 -> deferred WP18 -> Release Gate.
- `2026-05-01`: Next roadmap item is WP34 trace export.
