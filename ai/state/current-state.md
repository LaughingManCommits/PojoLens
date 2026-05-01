# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: A low-cost parallel docs run promoted a better quickstart README onboarding flow; the separate developer-notes file was not promoted after reviewer feedback.
- `2026-05-01`: The quickstart example now includes the promoted `/api/employees/by-salary-range` endpoint with the docs/tests fix-up completed through a follow-up orchestrator run.
- `2026-05-01`: Live-run hardening is in place: reviewer budget warnings, exact-duplicate promotion dedupe, and post-promotion `awaiting_validation` until repo-scope validation is recorded.
- `2026-05-01`: The next roadmap queue is broader follow-on orchestrator quality work: WP37 reviewer/promotion governance, WP38 docs/text guardrails, WP39 low-cost worker tuning, then WP40 end-to-end coding run reliability.
- `2026-05-01`: The orchestrator now places default copy/worktree workspaces in an external temp-backed root recorded in `workspacesDir`; manifests and task artifacts remain under repo-local `.claude-orchestrator`.
- `2026-05-01`: WP32-WP36 are complete: retained runs now expose evals, approval checkpoints, trace export, and a thin orchestrator entrypoint with the split control-plane modules behind it.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: `scripts/docs/check-doc-consistency.ps1` passed after promoting only the reviewed README task from the live parallel quickstart docs run; cost was `$0.17907`.
- `2026-05-01`: The live run `20260501T152301Z-example-parallel-implement-review-quickstart-salary-range-b1addb99` added and promoted the salary-range quickstart endpoint with docs/tests; the follow-up run `20260501T152714Z-example-implement-review-quickstart-salary-range-fixup-a8f6343d` fixed the docs/tests contract mismatch, and the quickstart Maven tests now pass.
- `2026-05-01`: The orchestrator now keeps promoted coding runs in `awaiting_validation` until a repo-scope validation checkpoint is recorded after promotion.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is WP37 -> WP38 -> WP39 -> WP40 -> deferred WP18 -> Release Gate.
- `2026-05-01`: Next decision is whether to start with structured reviewer findings and promotion blocking in WP37 or batch multiple review-governance changes together.
