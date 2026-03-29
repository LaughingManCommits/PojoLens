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
indexes/refresh-state.json -> derived per-file hash cache for incremental refresh
state/recent-validations.md -> warm validation ledger
log/events.jsonl -> recent discovery history
log/archive/*-summary.md -> derived monthly archive summaries
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
- keep `ai/state/current-state.md` and `ai/state/handoff.md` within their per-file budgets and required heading order
- keep combined hot context within hard cap `240` lines and `24 KB`; target operating range `160-200` lines total

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
Prefer archive summaries before raw `log/archive/*.jsonl` when archive history is needed.

Conditional cold-load triggers (additive hints, not hard gates):

| Task signal | Also load |
|---|---|
| release/publish/signing/versioning work or touching `RELEASE.md`, `.github/workflows/release.yml`, `pom.xml`, `pojo-lens*/pom.xml` | `ai/core/runbook.md`, `ai/state/recent-validations.md` |
| benchmark/JMH work or touching `pojo-lens-benchmarks/**`, `benchmarks/**`, `scripts/benchmark-*` | `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md` |
| public API/docs alignment work or touching `README.md`, `MIGRATION.md`, `docs/**` | `ai/core/readme-alignment.md`, `ai/core/documentation-index.md` |
| module topology/build boundary work | `ai/core/module-index.md`, `ai/core/system-boundaries.md`, `ai/core/architecture-map.md` |
| test strategy or validation history work | `ai/core/test-strategy.md`, `ai/state/recent-validations.md` |
| AI memory maintenance or touching `ai/**`, `scripts/refresh-ai-memory*`, `scripts/query-ai-memory*` | `ai/core/discovery-notes.md`, `ai/state/recent-validations.md` |

Routing fallback:
- if task intent is broad or ambiguous after applying the trigger table, run:
  `scripts/query-ai-memory.ps1 -Query "<task keywords>" -Limit 5`
- prefer top non-archive hits before raw archive logs

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

Use `scripts/refresh-ai-memory.ps1 -ForceFull` only when a full rebuild is required; the default refresh is incremental.

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
- use summary-first bullets and avoid repeating the same fact across `current-state.md` and `handoff.md`
- prefer date-stamped bullets (`YYYY-MM-DD`) for volatile facts

core/
- durable truths only
- remove duplicates and stale facts

indexes/
- derived data
- regenerate instead of editing
- `scripts/refresh-ai-memory.ps1` rebuilds `ai/indexes/*.json`
- `scripts/refresh-ai-memory.ps1` updates `ai/indexes/refresh-state.json` for incremental reuse
- optional SQLite cold search under `ai/indexes/cold-memory.db` is derived only
- `scripts/refresh-ai-memory.ps1 -CompactLog` compacts the recent event log into monthly archives
- `scripts/query-ai-memory.ps1` supports `-Tier`, `-Kind`, and `-Path` facets for cold retrieval
- `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` proves refresh/query latency and fixed-query hit quality

Summary guardrails:
- one bullet should carry one fact; split mixed bullets
- keep only startup-critical content in hot files; demote detail to cold docs
- when event history becomes noisy, run `scripts/refresh-ai-memory.ps1 -CompactLog`
- after AI memory edits, run:
  `scripts/refresh-ai-memory.ps1`
  `scripts/refresh-ai-memory.ps1 -Check`

---

# Promotion Rule

If knowledge remains relevant across multiple sessions,
promote it from `state/` to `core/`.
