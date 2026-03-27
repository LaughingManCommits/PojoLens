# AI Memory Maintenance Mode

Use this file only when performing a memory cleanup pass for the `/ai` directory.

The goal is to keep repository memory small, accurate, and useful.

## Maintenance Objective

Normalize and compact the AI memory system so that:

- hot context stays small
- state files contain only current work
- core files contain only durable truths
- indexes reflect the current repository structure
- optional cold-search artifacts stay derived and disposable
- logs contain only significant discoveries

## Memory Model

- `ai/core/` = durable markdown truths
- `ai/state/` = current markdown snapshot
- `ai/indexes/` = derived navigation data
- `ai/indexes/cold-memory.db` = optional derived SQLite/FTS cold search
- `ai/log/events.jsonl` = recent significant discovery history
- `ai/log/archive/*.jsonl` = archived discovery history

Compaction flow:

log -> state -> core -> regenerated indexes

## Maintenance Rules

### 1. Clean `ai/state/*`
Keep only:

- active work
- current risks
- next actions
- short verified notes needed for the next session

Remove:

- completed work
- stale blockers
- historical narrative
- duplicated repository facts already stored in `core/`

If knowledge is still useful across multiple sessions, promote it to `ai/core/`.

### 2. Clean `ai/core/*`
Keep only durable repository truths.

Remove:

- temporary implementation notes
- session-specific details
- duplicated facts
- stale or invalid information

Merge overlapping files when useful.

### 3. Rebuild `ai/indexes/*`
Treat all indexes as derived data.

Do not preserve history in indexes.
Regenerate them from the current repository structure.
Overwrite stale entries.
Rebuild the optional cold-search database if enabled.

### 4. Compact event history
Keep only recent significant events in `ai/log/events.jsonl`.
Move older significant events into `ai/log/archive/*.jsonl`.

Remove or summarize:

- repeated entries
- resolved exploratory threads
- low-value operational noise

Prefer one summary event over many repetitive events.
Keep exact validation commands in `ai/state/recent-validations.md` instead of bloating hot state.

### 5. Refresh hot context
After maintenance, make sure these remain short and accurate:

- `ai/core/agent-invariants.md`
- `ai/core/repo-purpose.md`
- `ai/state/current-state.md`
- `ai/state/handoff.md`

Hot context should contain only startup-critical information.

## Integrity Rules

When facts conflict, prefer:

1. code
2. tests
3. build config
4. `/ai` memory

Do not invent facts.
Preserve uncertainty where needed.

## Expected Output

A maintenance pass should result in:

- smaller hot context
- smaller state files
- cleaner core files
- regenerated indexes
- regenerated optional cold-search database when used
- reduced log noise
- updated `ai/state/current-state.md`
- updated `ai/state/handoff.md`

## Maintenance Prompt

When this file is loaded, perform a memory maintenance pass on `/ai`:

1. compact `ai/state/*`
2. compact `ai/core/*`
3. regenerate `ai/indexes/*`
4. rebuild optional `ai/indexes/cold-memory.db` when used
5. summarize redundant history in `ai/log/events.jsonl` and archive older entries
6. refresh hot context files

Make the smallest correct edits necessary.
Preserve useful knowledge.
Remove redundancy and stale information.
Use `scripts/refresh-ai-memory.ps1` to rebuild derived JSON indexes and optional cold-search artifacts after the Markdown truth is updated. Use `scripts/refresh-ai-memory.ps1 -CompactLog` when the active log needs to be reduced.
