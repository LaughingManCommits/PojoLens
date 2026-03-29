# AI Memory Maintenance Mode

Use this file only when performing a memory cleanup pass for the `/ai` directory.

The goal is to keep repository memory small, accurate, and useful.

## Maintenance Objective

Normalize and compact the AI memory system so that:

- hot context stays small
- hot state files keep their exact required sections and per-file budgets
- state files contain only current work
- core files contain only durable truths
- indexes reflect the current repository structure
- optional cold-search artifacts stay derived and disposable
- logs contain only significant discoveries

## Memory Model

- `ai/core/` = durable markdown truths
- `ai/state/` = current markdown snapshot
- `ai/indexes/` = derived navigation data
- `ai/indexes/refresh-state.json` = derived per-file hash cache for incremental refresh
- `ai/indexes/cold-memory.db` = optional derived SQLite/FTS cold search
- `ai/log/events.jsonl` = recent significant discovery history
- `ai/log/archive/*.jsonl` = archived discovery history
- `ai/log/archive/*-summary.md` = derived monthly archive summaries

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
Prefer incremental refresh with `scripts/refresh-ai-memory.ps1`; use `-ForceFull` only when the refresh schema or derived artifact shape changes.

### 4. Compact event history
Keep only recent significant events in `ai/log/events.jsonl`.
Move older significant events into `ai/log/archive/*.jsonl`.
Regenerate `ai/log/archive/*-summary.md` so search can hit summaries before raw archive rows.

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
`ai/state/current-state.md` and `ai/state/handoff.md` must keep their required heading order and stay within their per-file budgets.

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
- preserved incremental refresh reuse when inputs did not change
- regenerated optional cold-search database when used
- refreshed archive summaries
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
7. run `scripts/benchmark-ai-memory.ps1` when the memory retrieval path changes and keep the benchmark report current

Make the smallest correct edits necessary.
Preserve useful knowledge.
Remove redundancy and stale information.
Use `scripts/refresh-ai-memory.ps1` to rebuild derived JSON indexes and optional cold-search artifacts after the Markdown truth is updated. Use `scripts/refresh-ai-memory.ps1 -CompactLog` when the active log needs to be reduced, `scripts/refresh-ai-memory.ps1 -ForceFull` for a full rebuild, `scripts/query-ai-memory.ps1 -Tier/-Kind/-Path` for targeted retrieval, and `scripts/benchmark-ai-memory.ps1` to prove refresh/query performance and hit quality.
