# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP48 -> WP49 -> WP50 -> WP51 -> WP52 -> WP53 -> WP54 -> WP55 -> WP56 -> WP57 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP45 is complete; OTEL emission now maps retained run spans to OTLP HTTP with env/CLI activation, exports extra lineage as links, and reuses the retained trace graph for live runs plus `export-trace`.
- `2026-05-02`: WP47 is complete; HITL gates are available through `runPolicy` and `run`/`resume` flags, with sentinel/stdin approval, auto-approve, and abort blocking.
- `2026-05-02`: WP46 is complete; Pydantic v2 backs major orchestrator contracts and JSON-boundary validation, with `py.typed` and mypy coverage.
- `2026-05-02`: WP44/WP43/WP42 are complete; async execution, SDK provider fallback, and transient retry are active.
- `2026-05-02`: Next queue is WP48 through WP57, then WP40, deferred WP18, and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: `otel_spans.py` emits OTLP HTTP spans from the retained custom trace graph; OTEL task spans use the first retained parent as the OTEL parent and map any remaining parents to OTEL span links.
- `2026-05-02`: `hitl.py` owns HITL policy resolution and operator waiting; run events are `hitl-gate`, `hitl-approved`, and `hitl-aborted`.
- `2026-05-02`: Provider mode: `POJO_LENS_PROVIDER` overrides; auto-detect uses SDK when `anthropic` importable and `ANTHROPIC_API_KEY` set, otherwise subprocess.
- `2026-05-02`: `run_ops.run_loaded_plan` is `async def`; sync CLI entry points call it through `asyncio.run()`.
- `2026-05-02`: `TODO.md` order is WP48-WP57, WP40, deferred WP18, Release Gate (WP45/WP47 complete).
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
