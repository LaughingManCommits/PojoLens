# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-02`: WP54 is complete; optional `textual` dashboard support now adds `--tui` on `run` / `resume` / `retry`, interactive auto-enable with `--watch` fallback, task grid plus rolling summary panels, stderr tailing, and TUI HITL approve/abort controls.
- `2026-05-02`: WP49 is complete; structured worker `followUpTasks`, `runPolicy.followUpBehavior`, `--follow-up-mode`, between-batch task injection, persisted `injectedFrom` lineage, and selected-plan mutation now let same-run resume continue injected follow-up work.
- `2026-05-02`: WP48 is complete; pre-flight cost estimation now uses tracked Anthropic pricing plus effort/model heuristics, adds `run --estimate`, emits `costEstimate` in validate/run/manifests, and warns when `runBudgetUsd` is already below the minimum estimate.
- `2026-05-02`: Preserve the current orchestrator base: async `--max-parallel`, SDK/subprocess provider fallback, transient retry, HITL gates, low-cost profiles, and typed manifest/task-plan validation.

## Verified
- `2026-05-02`: WP54 validations passed: focused TUI/config/CLI/run tests, full Python suite (`620` tests), example parallel dry-run with `--tui`, and docs consistency checks.
- `2026-05-02`: WP49 validations passed: focused follow-up injection tests, full Python suite, example parallel validate, example parallel dry-run with `--follow-up-mode inject`, docs check, and AI memory refresh/check.
- `2026-05-02`: WP48 and WP45 validations already passed earlier the same day; no regressions appeared during the WP49 full-suite rerun.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- `2026-04-27`: Real MySQL verification for `examples/spring-boot-starter-risk-console` is still pending.

## Next
- `2026-05-02`: Roadmap order is WP50 -> WP55 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
- `2026-05-02`: WP50 is next unless the user chooses WP40 first: Rate-Limit-Aware Proactive Scheduling.
