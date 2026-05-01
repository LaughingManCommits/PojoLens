# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: The quickstart example now includes a promoted `/api/employees/by-salary-range` endpoint, and the salary-range docs/tests fix-up was completed through a follow-up orchestrator run.
- `2026-05-01`: Live-run hardening is now in place: `validate` warns about risky reviewer prompt budgets, promotion dedupes exact duplicate reviewer materialization, and promoted coding runs stay `awaiting_validation` until repo-scope validation is recorded after promotion.
- `2026-05-01`: The orchestrator now places default copy/worktree workspaces in an external temp-backed root recorded in `workspacesDir`; manifests and task artifacts remain under repo-local `.claude-orchestrator`.
- `2026-05-01`: A real parallel implementer+reviewer coding run now works end to end: `example-parallel-implement-review-quickstart` completed, promoted reviewed README/test changes, and the quickstart Maven tests passed.
- `2026-05-01`: WP32-WP36 are complete: retained runs now expose evals, approval checkpoints, trace export, and a thin orchestrator entrypoint with the split control-plane modules behind it.
- `2026-05-01`: Roadmap queue is now deferred WP18, then Release Gate.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: The live run `20260501T152301Z-example-parallel-implement-review-quickstart-salary-range-b1addb99` added and promoted the salary-range quickstart endpoint with docs/tests; the follow-up run `20260501T152714Z-example-implement-review-quickstart-salary-range-fixup-a8f6343d` fixed the docs/tests contract mismatch, and the quickstart Maven tests now pass.
- `2026-05-01`: The orchestrator contract now keeps promoted coding runs in `awaiting_validation` until a repo-scope validation checkpoint is recorded after promotion, and exact duplicate reviewer/materialized promotion ops no longer block safe promotion.
- `2026-05-01`: The Spring Boot starter quickstart example now includes a grouped department salary-summary endpoint plus tracked coding/review orchestration samples; exact commands live in `ai/state/recent-validations.md`.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is deferred WP18 -> Release Gate.
- `2026-05-01`: Next decision is whether to keep WP18 deferred or burn down the remaining release risk first.
