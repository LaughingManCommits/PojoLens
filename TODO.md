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

## Next Roadmap: Claude-Assisted Context Compression

Use Claude as an optional summarization/compression stage after retrieval and before generation.
Keep retrieval deterministic and code/tests as source of truth.

## Objective (WP10)

- keep existing retrieval pipeline (`refresh-ai-memory`, `query-ai-memory`) as the source selector
- add an optional Claude compression pass that produces a strict context brief
- enforce no-new-facts compression policy with source-linked bullets
- track Claude token/cost usage per turn and per tracker session until explicit session end
- keep compressed context inside hot-budget-aligned targets
- benchmark quality and latency impact before defaulting it on

## Execution Board (WP10)

| Work Package                                      | Priority | Status  | Dependency |
|---------------------------------------------------|----------|---------|------------|
| `WP10.1` Claude Compression Spike                 | `P0`     | `Ready` | none       |
| `WP10.2` Wrapper Script (`context-runner.ps1`)    | `P0`     | `Planned` | `WP10.1` |
| `WP10.3` Compression Policy and Prompt Contract   | `P0`     | `Planned` | `WP10.1` |
| `WP10.4` Validation and Benchmark Delta           | `P1`     | `Planned` | `WP10.2`, `WP10.3` |
| `WP10.5` Operational Rollout Decision             | `P1`     | `Planned` | `WP10.4` |
| `WP10.6` Claude Token Ledger Tracking             | `P0`     | `Done` | none |

## Spike Plan (WP10.1)

### Questions

- Can Claude produce stable compression with strict source-linked output and no invented facts?
- What token/line reduction do we get versus current direct-context loading?
- Does compression improve or degrade retrieval quality (`top1/top3`) and operator speed?
- What is the incremental latency cost per request?

### Options Matrix

| Option | Summary | Strengths | Risks | Fit |
|--------|---------|-----------|-------|-----|
| `A` No Claude | Keep current hybrid routing and summarization rules only. | Lowest complexity and zero integration risk. | Leaves manual summarization burden. | Baseline control |
| `B` Claude Optional (Manual Toggle) | Run Claude compression only when explicitly requested (`-CompressWithClaude`). | Safe rollout with easy A/B comparison. | Extra operator decision overhead. | Recommended first implementation |
| `C` Claude Default for Cold Loads | Auto-run compression whenever cold context is loaded. | Consistent compressed inputs. | Risk of over-compression or latency tax on all tasks. | Consider only after `WP10.4` evidence |
| `D` Claude for End-of-Session Only | Use Claude only for handoff/state compaction, not per-task prompts. | Targets highest-noise workflow with lower runtime risk. | Does not help per-task context windows. | Good parallel fallback path |

### Recommendation

- start with `Option B` plus `Option D`:
  optional per-task compression and end-of-session compaction
- require source links (`path`, `date` where applicable) for each compressed bullet
- block promoted output that introduces uncited facts

## Work Packages (WP10)

### `WP10.1` Claude Compression Spike

Goal:
- prove feasibility and tradeoffs before implementation default

Deliverables:
- short spike report with option recommendation and go/no-go criteria
- 3 representative task samples comparing raw vs compressed context packs

Acceptance criteria:
- compression output is source-linked and reproducible
- no observed factual drift in sampled tasks

### `WP10.2` Wrapper Script (`context-runner.ps1`)

Goal:
- provide one command path for retrieval plus optional Claude compression

Deliverables:
- new script to:
  run `query-ai-memory`
  assemble context snippets
  optionally call `claude.exe`
  emit a bounded context brief

Acceptance criteria:
- supports `-CompressWithClaude` toggle
- fails safe to non-compressed path on Claude errors

### `WP10.3` Compression Policy and Prompt Contract

Goal:
- make compression behavior strict, auditable, and bounded

Deliverables:
- policy doc covering:
  no-new-facts rule
  citation requirement
  max lines/bytes for compressed brief
  handling of uncertain or conflicting facts
- prompt template checked into repo for deterministic reuse

Acceptance criteria:
- policy and template are referenced from `AGENTS.md`/`ai/AGENTS.md`
- output format is machine-checkable enough for validation scripts

### `WP10.4` Validation and Benchmark Delta

Goal:
- compare baseline vs compressed pipeline quality and latency

Deliverables:
- benchmark extension with compressed-mode cases
- report of quality deltas (`top1`, `top3`) and latency deltas

Acceptance criteria:
- compressed mode does not reduce `top3` quality below baseline thresholds
- latency impact is documented and acceptable for intended usage mode

### `WP10.5` Operational Rollout Decision

Goal:
- decide default operating mode based on evidence

Deliverables:
- decision note: keep optional, enable by default, or restrict to end-of-session use
- update handoff/current-state instructions if default behavior changes

Acceptance criteria:
- decision is explicit and reflected in repo instructions

### `WP10.6` Claude Token Ledger Tracking

Goal:
- capture Claude usage totals for the active tracker session until it is explicitly ended

Deliverables:
- tracked Claude wrapper:
  `scripts/run-claude-tracked.ps1`
- per-turn ledger:
  `ai/log/claude-usage.jsonl`
- session aggregate state:
  `ai/state/claude-session-usage.json`

Acceptance criteria:
- each turn records tokens, cache tokens, cost, and duration
- active tracker session totals accumulate across turns
- explicit end-session emits final totals and closes the active tracker session

Result (2026-03-29):
- implemented and validated with live calls:
  `scripts/run-claude-tracked.ps1 "<prompt>"`
  `scripts/run-claude-tracked.ps1 -EndSession`
- tracker now records session totals without forcing `claude --session-id` reuse
