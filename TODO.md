# TODO

## Current Roadmap: Context Loading Rules

This roadmap is for tightening AI memory retrieval with conditional cold loads.
Use coarse, durable triggers so the rules stay maintainable.

## Objective

- keep hot context fixed and small
- keep hot context within budget: hard cap `240` lines and `24 KB`; target operating range `160-200` lines total
- add conditional cold-load rules tied to work domains
- reduce unnecessary file loading while preserving task accuracy
- keep rule maintenance cost low as the repo evolves
- add summarization and compaction rules so context stays inside budget over time

## Status Model

- `Done`: completed and reflected in repo state or decisions
- `In progress`: active current work
- `Ready`: next executable work with no unresolved dependency
- `Planned`: sequenced work, not started yet
- `Blocked`: waiting on an explicit decision

## Execution Board

| Work Package                                   | Priority | Status  | Dependency |
|------------------------------------------------|----------|---------|------------|
| `WP9.1` Define Trigger Matrix                  | `P0`     | `Done` | none |
| `WP9.2` Add Conditional Load Rules             | `P0`     | `Done` | `WP9.1`  |
| `WP9.3` Validate Rule Behavior on Real Tasks   | `P1`     | `Done` | `WP9.2`  |
| `WP9.4` Refresh Derived Memory Artifacts       | `P1`     | `Done` | `WP9.2`  |
| `WP9.5` Update Handoff and Current-State Notes | `P1`     | `Done` | `WP9.3`  |
| `WP9.6` Summarization and Budget Guardrails    | `P0`     | `Done` | `WP9.2`  |

## Spike Outcome (2026-03-29)

### Option Matrix

| Option                              | Summary                                                                                      | Strengths                                                                            | Risks                                                                                                    | Fit                           |
|-------------------------------------|----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|-------------------------------|
| `A` Static Rule Table Only          | Add domain-to-file conditional rules in `AGENTS.md` and `ai/AGENTS.md`.                      | Simple, deterministic, low runtime overhead.                                         | Manual maintenance drift; weak for ambiguous tasks.                                                      | Good baseline                 |
| `B` Query-Driven Only               | Route cold loads using `scripts/query-ai-memory.ps1` per task.                               | Adaptive and low manual rule churn.                                                  | Query wording sensitivity (`release-central signing retry` returned no hit); depends on index freshness. | Useful but insufficient alone |
| `C` Hybrid (Rules + Query Fallback) | Use static domain rules first, then query-based lookup when task intent is broad or unclear. | Predictable defaults plus adaptive recovery path; best accuracy/maintenance balance. | Slightly more process complexity than `A`.                                                               | Best overall                  |
| `D` Generated Routing Index         | Extend refresh pipeline to emit an explicit task-routing map from metadata.                  | Potentially strongest consistency at scale.                                          | Highest implementation cost and maintenance burden right now.                                            | Overkill for current scope    |

### Quick Spike Evidence

- `scripts/query-ai-memory.ps1 -Query "release retry"` returned high-signal hot context hits (`ai/state/handoff.md`, `ai/state/current-state.md`).
- `scripts/query-ai-memory.ps1 -Query "benchmark JMH"` returned useful warm/cold benchmark context.
- `scripts/query-ai-memory.ps1 -Query "release-central signing retry"` returned no match, confirming query-only routing is brittle for phrasing variants.

### Recommendation

- proceed with `Option C`:
  static rules for known domains, query fallback when uncertain
- enforce hot-context budget targets:
  hard cap `240` lines and `24 KB`; target range `160-200` lines total
- keep rules coarse (domain/path triggers) and avoid file-level microrules
- validate with at least three task types before marking `WP9.1` done

## Work Packages

### `WP9.1` Define Trigger Matrix

Goal:
- map major task domains to required cold-context files

Deliverables:
- one compact trigger table grouped by domain (release, benchmarks, docs, module structure, AI memory updates)
- explicit rule scope (`if task touches X, also load Y`)

Acceptance criteria:
- no file-level microrules unless they are stable and high-value
- every rule links to an existing cold-context source
- overlap and conflicting rules are resolved

### `WP9.2` Add Conditional Load Rules

Goal:
- encode the trigger matrix in agent instructions

Deliverables:
- updated conditional-load section in `AGENTS.md` and/or `ai/AGENTS.md`
- examples for benchmark, release, docs/api, and memory-maintenance tasks

Acceptance criteria:
- rules are additive hints, not hard gates
- hot context behavior remains unchanged
- instructions stay concise and scannable

### `WP9.3` Validate Rule Behavior on Real Tasks

Goal:
- verify rules load only what is needed while preserving accuracy

Deliverables:
- validation notes for at least 3 representative tasks
- adjustments for any under-loading or over-loading patterns

Acceptance criteria:
- no missed required context in validation scenarios
- measurable reduction in unnecessary cold-context loading

Result (2026-03-29):
- validated representative routing queries:
  `release retry` -> hot release state (`ai/state/handoff.md`, `ai/state/current-state.md`)
  `DashboardPlaywrightE2eTest` -> warm validation history (`ai/state/recent-validations.md`)
  `module index` with `-Kind ai-core` -> module routing core doc (`ai/core/module-index.md`)
  `single-join fast-path` with archive path filter -> archive summary (`ai/log/archive/2026-03-summary.md`)
- tightened fallback guidance and benchmark case to use facet-constrained module routing (`-Kind ai-core`) for stable intent-targeted retrieval
- benchmark quality recovered to `top1Rate=1.0` and `top3Rate=1.0`

### `WP9.4` Refresh Derived Memory Artifacts

Goal:
- keep generated memory indexes aligned after instruction changes

Deliverables:
- run `scripts/refresh-ai-memory.ps1`
- run `scripts/refresh-ai-memory.ps1 -Check`
- if retrieval path changes materially, run:
  `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json`

Acceptance criteria:
- refresh/check complete without errors
- derived artifacts match updated instructions

Result (2026-03-29):
- `scripts/refresh-ai-memory.ps1` passed
- `scripts/refresh-ai-memory.ps1 -Check` passed
- `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` passed

### `WP9.5` Update Handoff and Current-State Notes

Goal:
- make next-session startup guidance reflect the new rules

Deliverables:
- refreshed `ai/state/current-state.md`
- refreshed `ai/state/handoff.md`
- optional significant event in `ai/log/events.jsonl` if the change affects normal operating flow

Acceptance criteria:
- startup instructions and TODO roadmap are aligned

Result (2026-03-29):
- refreshed `ai/state/current-state.md` to reflect `WP9` completion and routing baseline
- refreshed `ai/state/handoff.md` to reflect new startup expectations, routing facets, and validation commands
- handoff/startup notes now align with `TODO.md` and the updated AGENTS policy

### `WP9.6` Summarization and Budget Guardrails

Goal:
- define and enforce summarization techniques that keep hot context inside budget

Deliverables:
- add a short summarization playbook to `AGENTS.md` and/or `ai/AGENTS.md` covering:
  summary-first bullets, de-duplication, date-stamped facts, and promotion of durable items from `state` to `core`
- add explicit compaction guidance:
  use `scripts/refresh-ai-memory.ps1 -CompactLog` when active log noise grows
- add verification steps after memory edits:
  `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check`

Acceptance criteria:
- hot context remains at or below `240` lines and `24 KB`
- preferred operating range (`160-200` lines total) is documented
- no repeated facts across `ai/state/current-state.md` and `ai/state/handoff.md` without clear reason
