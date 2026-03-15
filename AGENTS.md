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
- `ai/log/events.jsonl`
- `ai/state/benchmark-state.md`

Session rules:
- load hot context once per new session
- do not reload hot context after every work package
- reload only if state files changed, context was lost, or deeper repo knowledge is needed

Memory rules:
- follow `/ai/AGENTS.md` when updating memory
- code, tests, and build config override `/ai` if facts conflict

End of session:
- update `ai/state/current-state.md`
- update `ai/state/handoff.md`
- append significant discoveries to `ai/log/events.jsonl` if useful