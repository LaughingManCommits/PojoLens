# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP35 is now active. The next orchestrator slice is command decomposition so retained-run logic can move out of the large compatibility entrypoint before more features land.
- `2026-05-01`: WP33 is complete. Retained runs now expose approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints in the manifest.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, `evaluate-corpus`, and the tracked `example-eval-readonly-review` fixture.
- `2026-05-01`: AI memory refresh now stages derived artifacts and `refresh-ai-memory -Check` tolerates in-flight refresh.
- `2026-05-01`: Effort hardening added `--effort` overrides on `plan`/`run`/`resume`/`retry`, retained effort/source visibility, and `evaluate-run` effort-fit warnings.
- `2026-05-01`: WP29-WP31 are complete: LangGraph stays deferred, retained-run UX includes `status`/inventory/review summaries, and manifests expose trace plus branch lineage with `evaluate-run`.
- `2026-05-01`: Roadmap queue is now active WP35, planned WP34, deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: WP33 completion passed focused `py_compile`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (129 tests), and `scripts/docs/check-doc-consistency.ps1`.
- `2026-05-01`: WP32 completion passed focused orchestrator tests (128 tests), `validate`, dry-run `run`, `evaluate-run`, `evaluate-corpus`, and doc consistency.
- `2026-05-01`: AI memory staged publish hardening passed focused tests, normal and overlapping `refresh-ai-memory`/`-Check` runs, and doc consistency.
- `2026-05-01`: Effort override hardening passed focused tests, `validate`, dry-run `run --effort low`, and doc consistency.
- `2026-05-01`: AI memory refresh/check is passing again after fixing `scripts/ai/refresh-ai-memory.py` so cached derived indexes no longer survive when they reference missing paths like the removed `.perf/baseline-*` tree.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is active WP35 -> planned WP34 -> deferred WP18 -> Release Gate.
- `2026-05-01`: The next concrete task is splitting retained-run summary, review/promotion, validation, and eval helpers out of `scripts/ai/claude-orchestrator.py`.
