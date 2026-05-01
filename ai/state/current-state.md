# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP36 is active. First slice is complete: `run_loaded_plan`, `run_plan`, `resume_run`, and `retry_run` now delegate to `pojo_lens_agents.run_ops`.
- `2026-05-01`: WP35 is complete. `claude-orchestrator.py` now delegates retained-run summary/lifecycle, review/promote, validation checkpoints, and evals to focused `pojo_lens_agents` modules.
- `2026-05-01`: WP33 is complete. Retained runs now expose approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints in the manifest.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, `evaluate-corpus`, and the tracked `example-eval-readonly-review` fixture.
- `2026-05-01`: AI memory refresh now stages derived artifacts and `refresh-ai-memory -Check` tolerates in-flight refresh.
- `2026-05-01`: Effort hardening added `--effort` overrides plus retained effort/source visibility and `evaluate-run` effort-fit warnings.
- `2026-05-01`: Roadmap queue is now active WP36, planned WP34, deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: WP36 slice 1 passed `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/run_ops.py scripts/tests/test_claude_orchestrator.py` and `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests).
- `2026-05-01`: WP35 completion passed `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/ai/pojo_lens_agents/run_summary.py scripts/ai/pojo_lens_agents/review_ops.py scripts/ai/pojo_lens_agents/validation_ops.py scripts/ai/pojo_lens_agents/evals.py scripts/tests/test_claude_orchestrator.py`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests), and `scripts/docs/check-doc-consistency.ps1`.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is active WP36 -> planned WP34 -> deferred WP18 -> Release Gate.
- `2026-05-01`: The next concrete WP36 slice is planner decomposition, then runtime-admin cleanup/prune helpers.
