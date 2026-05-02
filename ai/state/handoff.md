# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP45 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP46 is complete; Pydantic v2 backs major orchestrator contracts and JSON-boundary validation, with `py.typed` and mypy coverage.
- `2026-05-02`: WP44/WP43/WP42 are complete; async execution, SDK provider fallback, and transient retry are active.
- `2026-05-02`: Next queue is WP45 (OpenTelemetry), then WP40, then deferred WP18 and Release Gate.

## Facts
- `2026-05-02`: `orchestrator_models.py` owns Pydantic model mirrors; existing contract dataclasses are Pydantic-backed and still support `dataclasses.asdict`.
- `2026-05-02`: Provider mode: `POJO_LENS_PROVIDER` overrides; auto-detect uses SDK when `anthropic` importable and `ANTHROPIC_API_KEY` set, otherwise subprocess.
- `2026-05-02`: `run_ops.run_loaded_plan` is `async def`; sync CLI entry points call it through `asyncio.run()`.
- `2026-05-02`: `TODO.md` order is WP45, WP40, deferred WP18, Release Gate (WP46 complete).
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
