# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: deferred WP18, then Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: WP31 is complete. Manifests now emit run-event lineage, retained-run `inventory`/`status` output includes compact `traceSummary` and `branchSummary` rollups, `evaluate-run` scores retained orchestration quality, and `example-trace-multibatch` is the tracked multi-batch lineage fixture.
- `2026-05-01`: WP30 is complete. Retained-run UX now includes `status`, richer inventory flags/counts, grouped review summaries, dry-run promotion allow/refuse summaries, and documented operator flow.
- `2026-05-01`: WP29 is complete. `pojo_lens_agents.langgraph_spike` covers lifecycle mapping, checkpointed ready-batch simulation, manifest-first `resume`/`retry` comparison, interrupt evaluation, and the decision to keep the custom scheduler in production for now.
- `2026-04-30`: Parallel agent execution remains required; keep `--max-parallel`, ready batches, isolated workspaces, and conservative write-scope serialization.
- `2026-04-30`: WP28 completed the runtime split across scheduling, provider calls, path safety, run-store helpers, workspace review, and run-governance checks.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is deferred WP18, then Release Gate.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).
- `2026-05-01`: There was no prior LangGraph implementation footprint in the repo; the current spike stays dependency-free and treats LangGraph as an optional wrapper around checkpointing, replay, and interrupts rather than a replacement for repo-owned safety logic.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `scripts/ai/pojo_lens_agents/langgraph_spike.py`
- `README.md`, `docs/**`, `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
