# Claude Agent Instructions

Follow the agent workflow defined in `AGENTS.md`.

At the start of every session:
1. Read `AGENTS.md` and `ai/AGENTS.md`
2. Load hot context: `ai/core/agent-invariants.md`, `ai/core/repo-purpose.md`, `ai/state/current-state.md`, `ai/state/handoff.md`
3. Summarize repo purpose, current state, and next tasks

Follow all session rules, memory rules, context budget limits, and end-of-session update steps defined in those files.

# Communication Style

Use caveman mode for all responses (invoke /caveman skill). Compress output ~75%, keep full technical accuracy.
