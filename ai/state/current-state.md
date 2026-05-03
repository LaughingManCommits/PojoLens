# Current State

## Repo
- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmarks.
- Release is `2026.04.29.1809`.

## Focus
- `2026-05-03`: WP71 complete; `prune_generated_plans` in `runtime_admin.py`; wired into `prune_runs`; 30-day/20-count defaults; slug collision warning + `generatedPlanCollision` payload in `wizard_command`; 16 regression tests; 1033 tests pass.
- `2026-05-03`: WP66 complete; `write_shared_context` 5th base tool; `shared-context.jsonl` per-run scratchpad; `sharedContextTags` filter; prompt section injection; `sharedContextPath` in manifest; 24 regression tests; 1017 tests pass.
- `2026-05-03`: WP64 complete; `conditionField`/`conditionValue` predicate on followUpTask; case-insensitive substring match against emitter record; `task-injection-skipped` event; 17 regression tests; 993 tests pass.
- `2026-05-03`: WP63 complete; `ExtraToolDef` + `extraTools` JSON contract; shell/script execution via `execute_extra_tool`; collision + traversal guards; 27 regression tests; 976 tests pass.
- `2026-05-03`: WP70 complete; `always` mode now fires every batch; stale sentinel gateId validation added to `_sentinel_action`; 18 new regression tests; 949 tests pass.
- `2026-05-03`: WP62 complete + after-care; `orchestrator_app` wrapper derives `budgetExceeded` from manifest; event payload + wrapper tests added; 931 tests pass.
- `2026-05-03`: WP56 complete + after-care; dry-run/estimate suppression fix, `notify_on=[]` fix; 895 tests pass.
- `2026-05-03`: WP69 complete; planner-first wizard flow landed with clarification loop, staged plan summary, approve/revise/stop checkpoint, and revision loop (up to 3 rounds); 841 tests pass.
- `2026-05-03`: WP68 + follow-ups complete; `Header` added to OrchestratorApp, DataTable column widths/right-align, wizard card containers; 837 tests pass.
- `2026-05-03`: WP60 is complete; shared interactive streaming now reaches watch and TUI surfaces; 836 Python tests passed.
- `2026-05-03`: WP50 is complete; proactive TPM/RPM throttling, wizard wiring, and follow-up-budget recompute landed; 799 tests passed.
- `2026-05-03`: WP59 and WP67 are complete; Textual ownership is consolidated and covered by 65 focused `tui_console` tests.
- `2026-05-03`: WP58/WP57/WP55 remain the active operator base: persistent console, retained diff preview, and guided wizard flow.

## Verified
- `2026-05-03`: Full Python suite at `1033` tests after WP71.
- `2026-05-03`: Full Python suite at `1017` tests after WP66.
- `2026-05-03`: Full Python suite at `993` tests after WP64.
- `2026-05-03`: Full Python suite at `976` tests after WP63.
- `2026-05-03`: Full Python suite at `949` tests after WP70.
- `2026-05-03`: Full Python suite at `931` tests after WP62 + after-care.
- `2026-05-03`: Full Python suite at `895` tests after WP56 after-care.
- `2026-05-03`: Focused validations passed for WP60, WP50, WP59, WP67, WP58, and WP57.
- `2026-05-03`: `scripts/docs/check-doc-consistency.ps1` passed.

## Release
- Latest cut: `2026.04.29.1809`.
- Use `RELEASE.md` when the user wants a new cut.

## Risks
- No active orchestrator risks beyond the remaining roadmap queue.

## Next
- `2026-05-03`: Roadmap order is WP65 -> WP72 -> WP40 -> Release Gate.
