# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP50 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP55 is complete; `pojolens-agents` now defaults to a guided wizard with plan inventory, optional natural-language routing, and inline review/promote/validate plus `--resume` / `--retry`.
- `2026-05-02`: WP54 is complete; the local orchestrator also has optional `textual` dashboard support with `--tui`, interactive auto-enable, `--watch` fallback, stderr tailing, and TUI HITL controls.
- `2026-05-02`: WP49 and WP48 remain the latest runtime-governance additions: injected `followUpTasks` mutate `selected-plan.json` between batches, and `costEstimate` now flows through validate/run/manifests.
- `2026-05-02`: Next queue is WP50, then WP56-WP57, then WP40, deferred WP18, and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `tui_app.py` owns the optional dashboard; `orchestrator_app._resolve_tui_mode(...)` handles auto-enable/fallback, and `run_loaded_plan(...)` can host the TUI in the same asyncio loop as the coordinator.
- `2026-05-02`: `wizard.py` owns the no-args operator flow; it preprocesses argv before argparse, wraps validate/run/review/promote/validate-run handlers directly, and keeps generated goal plans under `.claude-orchestrator/generated-plans/`.
- `2026-05-02`: `run_ops.run_loaded_plan` now emits `task-started` events with planned artifact paths and can await an async HITL decision hook so dashboard approval stays in-process without dropping sentinel-file support.
- `2026-05-02`: `run_ops.run_loaded_plan` mutates the retained plan between batches when follow-up injection is enabled; accepted tasks are revalidated and persisted back into `selected-plan.json`.
- `2026-05-02`: `cost_estimation.py` owns pre-flight USD/token estimation from tracked `ai/orchestrator/model-pricing.json`.
- `2026-05-02`: `otel_spans.py` emits OTLP HTTP spans from the retained custom trace graph.
- `2026-05-02`: `hitl.py` owns HITL policy resolution; provider mode still auto-detects SDK when `anthropic` and `ANTHROPIC_API_KEY` are present, otherwise subprocess.
- `2026-05-02`: `TODO.md` order is WP50, WP56-WP57, WP40, deferred WP18, Release Gate.
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
