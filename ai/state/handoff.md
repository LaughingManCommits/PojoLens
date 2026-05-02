# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow `TODO.md`: WP44 -> WP45 -> WP46 -> WP40 -> deferred WP18 -> Release Gate.
4. Treat `Release Gate` as last and cut from `RELEASE.md` only when requested.

## Focus
- `2026-05-02`: WP43 is complete. Dual-provider model: `POJO_LENS_PROVIDER=sdk` or auto-detect uses Anthropic Python SDK with bounded agentic tool loop; subprocess remains default. SDK exceptions map to WP42 retry patterns. 65 new tests; 484 total green.
- `2026-05-02`: WP42 is complete. Transient retry policy classifies rate-limit/timeout/5xx vs permanent errors; backoff 1s/2s/4s + jitter; `attempt`/`attempt_errors` in task records; `--max-task-retries` CLI override.
- `2026-05-02`: Next queue is WP44 (async task execution) through WP46, then WP40, then deferred WP18 and Release Gate.

## Facts
- `2026-05-02`: `sdk_provider.py` owns the SDK agentic loop: 4 tools (`read_file`, `write_file`, `str_replace_based_edit_tool`, `bash`), path-traversal guard via `_safe_workspace_path()`, streaming when stderr TTY, usage accum across turns.
- `2026-05-02`: Provider mode: `POJO_LENS_PROVIDER` env var overrides; auto-detect uses SDK when `anthropic` importable + `ANTHROPIC_API_KEY` set; subprocess otherwise.
- `2026-05-02`: `ensure_provider_available(bin, mode)` in `provider_worker.py` validates SDK deps; `task_execution.py` unifies both paths into `stdout_text`/`stderr_text`/`return_code`/`usage`.
- `2026-05-02`: `resolve_output_profile(...)` in `plan_support.py` merges task-level plus agent-default output profiles and records whether each task profile came from the task, agent, or fallback default.
- `2026-05-02`: `TODO.md` order is WP44, WP45, WP46, WP40, deferred WP18, Release Gate (WP43 complete).
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
