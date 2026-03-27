# Agent Workflow

This repository uses persistent AI memory stored in `/ai`.

Start of session:
- load hot context
- summarize repository purpose, current state, and next tasks
- do not load cold context unless the task needs it

Hot context:
- `ai/core/agent-invariants.md`
- `ai/core/repo-purpose.md`
- `ai/state/current-state.md`
- `ai/state/handoff.md`

Cold context:
- `ai/core/*`
- `ai/indexes/*`
- `ai/state/recent-validations.md`
- `ai/log/events.jsonl`
- `ai/log/archive/*-summary.md`
- `ai/log/archive/*.jsonl`
- `ai/state/benchmark-state.md`

Session rules:
- load hot context once per new session
- do not reload hot context after every work package
- reload only if state files changed, context was lost, or deeper repo knowledge is needed

Memory rules:
- follow `/ai/AGENTS.md` when updating memory
- code, tests, and build config override `/ai` if facts conflict
- `ai/indexes/*.json` and optional `ai/indexes/cold-memory.db` are derived artifacts; refresh them with `scripts/refresh-ai-memory.ps1` after structural or documentation changes
- run `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` after changing the AI memory retrieval path

End of session:
- update `ai/state/current-state.md`
- update `ai/state/handoff.md`
- append significant discoveries to `ai/log/events.jsonl` if useful
- compact older log history with `scripts/refresh-ai-memory.ps1 -CompactLog` when the active log grows noisy
