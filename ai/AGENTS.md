# AI Memory Guide

This directory stores persistent repository memory used by AI agents.

The root `AGENTS.md` defines agent workflow.
This file defines how the memory system is organized and maintained.

---

# Memory Layout

core/ -> durable markdown truths
state/ -> current markdown snapshot
indexes/ -> derived JSON navigation data
indexes/cold-memory.db -> optional derived SQLite/FTS cold-search artifact
state/recent-validations.md -> warm validation ledger
log/events.jsonl -> recent discovery history
log/archive/*.jsonl -> archived discovery history

Conceptually:

log -> state -> core -> regenerated indexes

---

# Hot Context

Always loaded at session start:

- ai/core/agent-invariants.md
- ai/core/repo-purpose.md
- ai/state/current-state.md
- ai/state/handoff.md

Rules:

- keep hot context small
- store only startup-critical information
- move durable knowledge into `core`

---

# Cold Context

Load only when deeper repository knowledge is required.

Examples:

- ai/core/module-index.md
- ai/core/architecture-map.md
- ai/core/system-boundaries.md
- ai/core/test-strategy.md
- ai/core/readme-alignment.md
- ai/core/benchmark-context.md
- ai/core/discovery-notes.md
- ai/state/recent-validations.md
- ai/state/benchmark-state.md
- ai/indexes/*
- ai/log/events.jsonl
- ai/log/archive/*.jsonl

Do not auto-load cold files.
Do not treat generated JSON indexes or the optional SQLite database as source of truth.

---

# Integrity

When facts conflict prefer:

1. code
2. tests
3. build config
4. `/ai`

Do not invent facts.
Preserve uncertainty.

---

# Freshness

When durable repository facts change:

1. update affected memory files
2. regenerate affected indexes
3. refresh `ai/state/current-state.md`
4. refresh `ai/state/handoff.md`
5. regenerate derived memory artifacts with `scripts/refresh-ai-memory.ps1`
6. log a significant event if useful

---

# Compaction

Keep memory lightweight.

log/
- significant events only
- merge repeated discoveries
- summarize old exploration
- keep `events.jsonl` as the small recent window
- move older significant events into `log/archive/*.jsonl`

state/
- active work only
- remove completed tasks
- promote durable knowledge to `core`

core/
- durable truths only
- remove duplicates and stale facts

indexes/
- derived data
- regenerate instead of editing
- `scripts/refresh-ai-memory.ps1` rebuilds `ai/indexes/*.json`
- optional SQLite cold search under `ai/indexes/cold-memory.db` is derived only
- `scripts/refresh-ai-memory.ps1 -CompactLog` compacts the recent event log into monthly archives

---

# Promotion Rule

If knowledge remains relevant across multiple sessions,
promote it from `state/` to `core/`.
