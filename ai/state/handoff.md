# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP50 -> WP51 -> WP52 -> WP53 -> WP54 -> WP55 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP49 is complete; dynamic follow-up mutation now supports structured worker `followUpTasks`, `runPolicy.followUpBehavior`, `--follow-up-mode`, between-batch task injection, persisted `injectedFrom` lineage, and selected-plan mutation for same-run resume.
- `2026-05-02`: WP48 is complete; pre-flight cost estimation now uses tracked model pricing, validate-time budget warnings, `run --estimate`, and retained `costEstimate` payloads/manifests.
- `2026-05-02`: WP45 is complete; OTEL emission now maps retained run spans to OTLP HTTP with env/CLI activation, exports extra lineage as links, and reuses the retained trace graph for live runs plus `export-trace`.
- `2026-05-02`: Next queue is WP50 through WP57, then WP40, deferred WP18, and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `run_ops.run_loaded_plan` now mutates the retained selected plan between batches when follow-up injection is enabled; accepted follow-up tasks are revalidated through the shared task-definition loader and persisted back into `selected-plan.json`.
- `2026-05-02`: `cost_estimation.py` owns pre-flight USD/token estimation; it loads tracked rates from `ai/orchestrator/model-pricing.json`, canonicalizes model aliases, and emits heuristic or observed prompt-based ranges.
- `2026-05-02`: `otel_spans.py` emits OTLP HTTP spans from the retained custom trace graph; OTEL task spans use the first retained parent as the OTEL parent and map any remaining parents to OTEL span links.
- `2026-05-02`: `hitl.py` owns HITL policy resolution; provider mode still auto-detects SDK when `anthropic` and `ANTHROPIC_API_KEY` are present, otherwise subprocess.
- `2026-05-02`: `TODO.md` order is WP50-WP57, WP40, deferred WP18, Release Gate (WP45/WP47/WP48/WP49 complete).
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
