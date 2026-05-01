# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: active WP36, planned WP34, deferred WP18, then Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: WP36 is active. `scripts/ai/claude-orchestrator.py` is a 50-line shim; planner flow, runtime admin, prompt/worker contracts, task execution, and manifest IO now delegate to focused `pojo_lens_agents` modules.
- `2026-05-01`: WP35 is complete. `claude-orchestrator.py` now delegates retained-run summary, review/promote, validation checkpoints, and evals to focused `pojo_lens_agents` modules.
- `2026-05-01`: WP33 is complete. Retained runs now derive approval lifecycle states, and `review`/`validate-run`/`promote` persist coordinator checkpoints plus run-local summaries.
- `2026-05-01`: WP32 is complete. The orchestrator now exposes `scoreSummary`, benchmark dimensions, and `evaluate-corpus`.
- `2026-05-01`: AI memory refresh now stages derived files and `refresh-ai-memory -Check` can wait/retry across overlap.
- `2026-05-01`: Orchestrator hardening added `--effort` overrides, retained effort/source visibility, and effort-fit warnings.
- `2026-05-01`: WP31 is complete: manifests now expose run-event lineage, retained `traceSummary`/`branchSummary`, and the `example-trace-multibatch` regression fixture.
- `2026-04-30`: WP28 completed the runtime split across scheduling, provider calls, path safety, run-store helpers, workspace review, and run-governance checks.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is active WP36, planned WP34, deferred WP18, Release Gate.
- `2026-05-01`: `pojo_lens_agents.orchestrator_app` dropped from 5016 to 3792 lines after moving planner flow, runtime admin, prompt/worker contracts, task execution, and manifest IO into focused modules.
- `2026-05-01`: The next WP36 slice is pushing plan loading/scope validation and the remaining argparse/dispatch glue out of `pojo_lens_agents.orchestrator_app`.
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
