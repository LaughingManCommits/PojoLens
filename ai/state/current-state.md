# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-01`: WP38 complete - docs/text guardrails now block mojibake promotion, warn on new non-ASCII doc text in ASCII docs, warn on docs-only plans without docs validation, and add a synthesized docs consistency helper in `validate-run`.
- `2026-05-01`: WP37 complete - reviewer findings now carry severity, block promotion when needed, and surface `review-blocked` lifecycle state.
- `2026-05-01`: Quickstart proofs now cover the promoted `/api/employees/by-salary-range` endpoint plus a parallel docs README improvement run.
- `2026-05-01`: Live-run hardening includes reviewer budget warnings, exact-duplicate promotion dedupe, repo-scope post-promotion validation, and external temp-backed copy/worktree sandboxes recorded in `workspacesDir`.
- `2026-05-01`: WP32-WP38 are complete; next queue is WP39 low-cost worker tuning, then WP40 end-to-end coding reliability.
- `2026-04-30`: Parallel execution remains required; preserve `--max-parallel`, isolated workspaces, and conservative write-scope serialization.

## Verified
- `2026-05-01`: `py -3 -m unittest scripts.tests.test_claude_orchestrator` passed with 156 tests after WP38, including docs consistency helper coverage for docs-only retained changes.
- `2026-05-01`: `scripts/docs/check-doc-consistency.ps1` passed after the live parallel quickstart docs run; cost was `$0.17907`.
- `2026-05-01`: The salary-range quickstart feature run plus follow-up fixup run are promoted, and the quickstart Maven tests pass.
- `2026-05-01`: Promoted coding runs remain `awaiting_validation` until a repo-scope validation checkpoint is recorded after promotion.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-01`: Roadmap order is WP39 -> WP40 -> deferred WP18 -> Release Gate.
- `2026-05-01`: WP39 is next: low-cost worker profiles, tighter output discipline, and cheaper repeated live-proof runs.
