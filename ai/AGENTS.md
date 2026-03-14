# AI Memory Maintenance Guide

This directory stores persistent repository memory in two tiers.

## Hot Context

Always loaded:

1. `ai/core/agent-invariants.md`
2. `ai/core/repo-purpose.md`
3. `ai/state/current-state.md`
4. `ai/state/handoff.md`

Rules:

- keep these files short
- store only the minimum context needed to start work
- move durable detail into cold files

## Cold Context

Load only when the task needs it:

- `ai/core/module-index.md`
- `ai/core/readme-alignment.md`
- `ai/core/runbook.md`
- `ai/core/benchmark-context.md`
- `ai/core/documentation-index.md`
- `ai/core/system-boundaries.md`
- `ai/core/architecture-map.md`
- `ai/core/test-strategy.md`
- `ai/state/benchmark-state.md`
- `ai/core/discovery-notes.md`
- `ai/indexes/*`
- `ai/log/events.jsonl`

Rules:

- keep durable truths in `ai/core/*`
- keep `ai/state/*` session-specific and concise
- regenerate indexes instead of letting stale entries accumulate
- log only significant events
- load benchmark context only when the task is about benchmark runs, thresholds, parity, profiling, or performance regressions

## Integrity Rules

- Prefer code, build config, and tests over `/ai` when facts conflict.
- Preserve uncertainty instead of inventing facts.
- Remove redundant memory, not useful knowledge.
- Do not auto-load cold files by default.

## Freshness Rules

`ai/memory-state.json` tracks the last full rebuild.

When durable repo facts or structure change:

1. update affected hot or cold files
2. regenerate affected indexes
3. refresh `ai/state/current-state.md`
4. update `ai/state/handoff.md`
5. append a significant event to `ai/log/events.jsonl`

## Compaction Rules

- Core files: durable facts only
- State files: current work only
- Index files: current structure only
- Log: significant events only

The goal is fast startup from hot context and selective deep reads from cold context.
