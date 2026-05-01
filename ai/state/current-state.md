# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: AI memory refresh now stages derived artifacts, publishes them atomically, and uses `ai/indexes/publish-state.json` so `refresh-ai-memory -Check` can tolerate an in-flight refresh.
- `2026-05-01`: Orchestrator hardening added `--effort` overrides on `plan`/`run`/`resume`/`retry`, retained effort/source visibility, and `evaluate-run` effort-fit warnings.
- `2026-05-01`: WP29-WP31 are complete: LangGraph was deferred in favor of the custom scheduler, retained-run UX now includes `status`/inventory/review summaries, and manifests expose trace plus branch lineage with `evaluate-run`.
- `2026-05-01`: Roadmap queue is now deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: AI memory staged publish hardening passed focused `py_compile`, `unittest scripts.tests.test_refresh_ai_memory`, a normal `refresh-ai-memory` + `-Check` sequence, a live overlapping `refresh-ai-memory` and `-Check` run, and doc consistency.
- `2026-05-01`: Orchestrator effort override hardening passed `py -3 -m py_compile scripts/ai/claude-orchestrator.py scripts/tests/test_claude_orchestrator.py scripts/ai/refresh-ai-memory.py`, `py -3 -m unittest scripts.tests.test_claude_orchestrator` (127 tests), `scripts/ai/claude-orchestrator.ps1 validate ai/orchestrator/tasks/example-trace-multibatch.json --json`, `scripts/ai/claude-orchestrator.ps1 run ai/orchestrator/tasks/example-trace-multibatch.json --dry-run --max-parallel 2 --effort low --json`, and `scripts/docs/check-doc-consistency.ps1`.
- `2026-05-01`: AI memory refresh/check is passing again after fixing `scripts/ai/refresh-ai-memory.py` so cached derived indexes no longer survive when they reference missing paths like the removed `.perf/baseline-*` tree.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is deferred WP18 -> Release Gate.
- `2026-05-01`: If orchestration resumes, the next meaningful slice is broader evaluator depth or release hardening rather than more retained-run visibility plumbing.
