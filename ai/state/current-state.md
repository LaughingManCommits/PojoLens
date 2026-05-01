# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP31 complete: manifests now emit run-event lineage, retained-run summaries expose compact `traceSummary`/`branchSummary` rollups, `evaluate-run` scores retained orchestration quality, and `example-trace-multibatch` proves the multi-batch lineage contract.
- `2026-05-01`: WP30 complete: retained-run UX now includes `status`, richer inventory flags/counts, grouped review summaries, dry-run promotion allow/refuse summaries, and documented operator flow.
- `2026-05-01`: WP29 complete: `pojo_lens_agents.langgraph_spike` records the LangGraph spike decision while keeping the custom scheduler as the production path.
- `2026-05-01`: Roadmap queue is now deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: WP31 completion passed focused `py_compile`, `unittest scripts.tests.test_claude_orchestrator` (127 tests), `validate example-trace-multibatch --json`, `run example-trace-multibatch --dry-run --max-parallel 2 --json`, and doc consistency.
- `2026-05-01`: WP31 trace-summary slice passed focused `py_compile`, `unittest scripts.tests.test_claude_orchestrator` (125 tests), `inventory --json`, `run example-parallel --dry-run --max-parallel 2 --json`, and doc consistency.
- `2026-05-01`: WP31 first slice passed focused `py_compile`, `unittest scripts.tests.test_claude_orchestrator` (125 tests), and added manifest-backed run-event lineage coverage.
- `2026-05-01`: WP30 passed `py_compile`, `unittest discover` (148 tests), `inventory --json`, `prune --older-than-days 14 --dry-run --json`, `status <run> --json`, and doc consistency.
- `2026-05-01`: WP29 passed `py_compile`, `unittest discover` (146 tests), `run example-parallel --dry-run --max-parallel 2 --json`, and doc consistency.
- `2026-04-30`: WP26-WP28 previously passed reactor/script validation; see `ai/state/recent-validations.md` for exact commands.

## Release
- Latest cut is `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is deferred WP18 -> Release Gate.
- `2026-05-01`: If orchestration resumes, the next meaningful slice is broader evaluator depth or release hardening rather than more retained-run visibility plumbing.
