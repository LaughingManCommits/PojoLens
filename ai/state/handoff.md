# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP50 -> WP55 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP54 is complete; the local orchestrator now has optional `textual` dashboard support with `--tui`, interactive auto-enable plus `--watch` fallback, stderr tailing, and TUI HITL approve/abort controls.
- `2026-05-02`: WP49 and WP48 remain the latest runtime-governance additions: injected `followUpTasks` mutate the retained selected plan between batches, and pre-flight `costEstimate` data now flows through validate/run/manifests.
- `2026-05-02`: Next queue is WP50, then WP55 through WP57, then WP40, deferred WP18, and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `tui_app.py` owns the optional dashboard; `orchestrator_app._resolve_tui_mode(...)` handles auto-enable/fallback policy, and `run_loaded_plan(...)` can host the TUI in the same asyncio loop as the coordinator.
- `2026-05-02`: `run_ops.run_loaded_plan` now emits `task-started` events with planned artifact paths and can await an async HITL decision hook so dashboard approval stays in-process without dropping sentinel-file support.
- `2026-05-02`: `run_ops.run_loaded_plan` now mutates the retained selected plan between batches when follow-up injection is enabled; accepted follow-up tasks are revalidated through the shared task-definition loader and persisted back into `selected-plan.json`.
- `2026-05-02`: `cost_estimation.py` owns pre-flight USD/token estimation from tracked `ai/orchestrator/model-pricing.json`.
- `2026-05-02`: `otel_spans.py` emits OTLP HTTP spans from the retained custom trace graph.
- `2026-05-02`: `hitl.py` owns HITL policy resolution; provider mode still auto-detects SDK when `anthropic` and `ANTHROPIC_API_KEY` are present, otherwise subprocess.
- `2026-05-02`: `TODO.md` order is WP50, WP55-WP57, WP40, deferred WP18, Release Gate (WP45/WP47/WP48/WP49/WP51/WP52/WP53/WP54 complete).
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `ai/orchestrator/README.md`, `ai/orchestrator/SYSTEM-SPEC.md`
- `README.md`, `RELEASE.md`, `.github/workflows/*`
