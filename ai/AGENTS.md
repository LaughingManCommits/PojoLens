# AI Memory Maintenance Guide

This directory contains the persistent AI memory for this repository.

These files help future agents understand the project without rediscovering repository context.

This file defines **how the memory system itself should be maintained**.

---

# Memory Structure

The `/ai` directory contains four layers of memory.

## Core Memory
Location: `ai/core/*`

Durable repository knowledge.

Examples:
- agent-invariants.md
- repo-purpose.md
- module-index.md
- readme-alignment.md
- runbook.md
- documentation-index.md
- system-boundaries.md
- test-strategy.md

Rules:

- Only update when durable repository facts change.
- Prefer incremental edits instead of rewriting entire files.
- Never delete files inside `ai/core/*`.
- Avoid storing chronological logs inside core files.

Core memory should remain stable across many sessions.

---

## State Memory
Location: `ai/state/*`

Session-level memory describing the latest repository understanding.

Files:

- current-state.md
- handoff.md

Rules:

- Update `current-state.md` after meaningful discoveries, verification, or completed tasks.
- Update `handoff.md` before ending a session.
- Keep state files concise and focused on next actions.
- Rewrite state files rather than appending indefinitely.

State memory represents operational context, not durable facts.

---

## Index Memory
Location: `ai/indexes/*`

Machine-readable metadata about the repository.

Examples:

- files-index.json
- symbols-index.json
- docs-index.json
- test-index.json
- config-index.json

Rules:

- Regenerate indexes if repository structure changes.
- Remove entries for deleted or moved files.
- `docs-index.json` must include all Markdown documentation discovered in the repository.

Indexes represent the **current repository structure**, not historical data.

---

## Log Memory
Location: `ai/log/*`

Append-only historical records.

Example:

- events.jsonl

Rules:

Log events when:

- repository scans occur
- indexes are regenerated
- documentation mismatches are detected
- core repository knowledge changes

Avoid logging trivial actions such as simple file reads.

---

# Memory Integrity Rules

Agents maintaining this directory must:

- Prefer **code and tests** over `/ai` memory when contradictions appear.
- Preserve uncertainty rather than inventing facts.
- Never replace verified knowledge with speculation.
- Never delete `ai/core/*` automatically.

If `/ai` memory conflicts with repository reality, update `/ai`.

---

# Memory Compaction Rules

The `/ai` directory must remain compact.

### Core files
Keep concise and stable.

### State files
Rewrite regularly and remove stale context.

### Index files
Regenerate instead of accumulating history.

### Logs
If logs become large:

- summarize older entries
- move summaries into `ai/archive/`
- keep recent events in `events.jsonl`.

---

# Memory Update Workflow

When finishing a task:

1. update `ai/state/current-state.md`
2. update `ai/state/handoff.md`
3. update affected core files if durable facts changed
4. regenerate indexes if repository structure changed
5. append a relevant event to `ai/log/events.jsonl`

---

# Goal of This Directory

The `/ai` directory acts as a persistent repository knowledge cache.

Future agents should be able to:

- understand repository purpose
- understand architecture and modules
- locate important documentation
- verify documentation accuracy
- continue work from previous sessions