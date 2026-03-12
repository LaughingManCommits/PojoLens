# AI Memory Maintenance Rules

This repository uses persistent AI memory stored under `/ai`.

Future agents must maintain this memory to keep the system accurate.

## Memory Layers

### Core Memory
Location: `ai/core/*`

Contains durable repository truths.

Examples:
- repo purpose
- architecture map
- module index
- boundaries
- test strategy

Update rules:
- Only update when structural facts change
- Do not rewrite casually
- Prefer incremental edits

---

### State Memory
Location: `ai/state/*`

Contains session-level knowledge.

Files:
- current-state.md
- handoff.md

Update rules:
- update `current-state.md` after meaningful work
- update `handoff.md` before ending a session
- keep these files short

---

### Index Memory
Location: `ai/indexes/*`

Machine-readable metadata.

Examples:
- files-index.json
- symbols-index.json
- test-index.json
- docs-index.json

Update rules:
- regenerate if repo structure changes
- regenerate if new docs are added
- regenerate after large refactors

---

### Log Memory
Location: `ai/log/events.jsonl`

Append-only history.

Rules:
- log significant events
- do not log trivial file reads
- never delete entries

---

## Documentation Tracking

Agents must monitor Markdown documentation.

If new `.md` files appear:
- update `docs-index.json`
- update `documentation-index.md`

If TODO or roadmap files change:
- reflect that in `current-state.md`

---

## Drift Detection

AI memory may become stale.

Agents must verify memory against:

1. runtime code
2. build config
3. tests
4. documentation

If memory contradicts code:
- update `/ai` memory
- record change in log

---

## Compaction Rules

To avoid memory bloat:

- keep `current-state.md` under ~200 lines
- summarize long logs periodically
- archive obsolete task files to `/ai/archive`

---

## Safety Rules

Agents must never:

- delete `/ai/core/*`
- invent repository purpose
- mark assumptions as verified
- trust outdated documentation over code