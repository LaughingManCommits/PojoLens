# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: deferred WP18, then Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-01`: The quickstart salary-range feature is now promoted in the repo, and the docs/tests fix-up was completed through a second live orchestrator run after post-promotion Maven validation caught a contract mismatch.
- `2026-05-01`: Contract hardening is in place after those runs: reviewer budget risk now surfaces in `validate`, exact duplicate reviewer/materialized promotion ops are deduped, and promoted coding runs stay `awaiting_validation` until repo-scope validation is recorded after promotion.
- `2026-05-01`: Default copy/worktree worker sandboxes now live in an external temp-backed root recorded in `workspacesDir`; manifests and prompts stay under repo-local `.claude-orchestrator`.
- `2026-05-01`: Retained runs now expose evals, approval checkpoints, trace export, effort/source visibility, and the split orchestrator modules behind the thin entrypoint.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-05-01`: `TODO.md` order is deferred WP18, Release Gate.
- `2026-05-01`: `ai/orchestrator/tasks/example-implement-review-quickstart.json` is the smallest tracked implementer-to-reviewer coding sample and targets the Spring Boot quickstart example.
- `2026-05-01`: `ai/orchestrator/tasks/example-parallel-implement-review-quickstart-salary-range.json` is the tracked live proof for a parallel two-implementer plus reviewer quickstart feature slice, and `example-implement-review-quickstart-salary-range-fixup.json` is the follow-up docs/tests correction slice.
- `2026-05-01`: `pojo_lens_agents.orchestrator_app` is now 863 lines after the final WP36 split into focused helper modules.
- `2026-05-01`: LangGraph is still only a spike in `pojo_lens_agents.langgraph_spike`; it is not a live runtime backend.
- `2026-05-01`: The first failed parallel implementer proof was caused by repo-local copy sandboxes; the fix moved default worker sandboxes out of the repo while keeping legacy manifest fallback for older retained runs.
- `2026-05-01`: `export-trace` uses retained manifest data only; it does not add new runtime state or change manifest schema.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/ai/refresh-ai-memory.ps1`, then `scripts/ai/refresh-ai-memory.ps1 -Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `scripts/ai/pojo_lens_agents/langgraph_spike.py`
- `README.md`, `docs/**`, `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
