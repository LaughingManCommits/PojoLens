# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: deferred WP18, then Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: WP34 is complete. `export-trace` now writes `pojo-lens-orchestrator-trace/v1` JSON spans from retained events plus persisted review/validation/promotion checkpoints.
- `2026-05-01`: WP36 is fully complete. `scripts/ai/claude-orchestrator.py` is a 50-line shim, `pojo_lens_agents.orchestrator_app` is down to 863 lines, and parser/contracts/utils/plan/review-provider support now live in focused `pojo_lens_agents` modules.
- `2026-05-01`: WP33 is complete. Retained runs now derive approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints plus run-local summaries.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, and `evaluate-corpus`.
- `2026-05-01`: Orchestrator hardening added `--effort` overrides, retained effort/source visibility, and effort-fit warnings.
- `2026-05-01`: WP31 is complete: manifests now expose run-event lineage, retained `traceSummary`/`branchSummary`, and the `example-trace-multibatch` regression fixture.
- `2026-04-30`: WP28 completed the runtime split across scheduling, provider calls, path safety, run-store helpers, workspace review, and run-governance checks.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is deferred WP18, Release Gate.
- `2026-05-01`: `pojo_lens_agents.orchestrator_app` is now 863 lines after the final WP36 split into `orchestrator_contracts`, `cli_parser`, `orchestrator_utils`, `plan_support`, `workspace_run_review`, `provider_worker`, and `command_dispatch`.
- `2026-05-01`: LangGraph is still only a spike in `pojo_lens_agents.langgraph_spike`; it is not a live runtime backend.
- `2026-05-01`: `export-trace` uses retained manifest data only; it does not add new runtime state or change manifest schema.
- `2026-05-01`: `scripts/ai/refresh-ai-memory.ps1 -Check` is green again after the cache-reuse fix in `refresh-ai-memory.py`.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).
- `2026-05-01`: There was no prior LangGraph footprint in the repo; the spike stays dependency-free and treats LangGraph as optional wrapper logic, not a scheduler replacement.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `scripts/ai/pojo_lens_agents/langgraph_spike.py`
- `README.md`, `docs/**`, `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
