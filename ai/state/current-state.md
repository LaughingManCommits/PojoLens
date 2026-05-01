# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Current release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP31 started: manifests now emit run-event lineage for run start, ready batches, task completion/blocking, parent task ids, and run finish.
- `2026-05-01`: WP30 complete: retained-run UX now includes `status`, richer inventory flags/counts, grouped review summaries, dry-run promotion allow/refuse summaries, and documented operator flow.
- `2026-05-01`: WP29 complete: `pojo_lens_agents.langgraph_spike` records the LangGraph spike decision while keeping the custom scheduler as the production path.
- `2026-05-01`: Roadmap queue is now WP31, then deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
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
- `2026-05-01`: Roadmap order is WP31 -> deferred WP18 -> Release Gate.
- `2026-05-01`: Continue WP31 by surfacing compact trace summaries and adding evaluator coverage for orchestration quality.
