# Agent Workflow

This repository uses persistent AI memory stored in `/ai`.

Start of session:
- load hot context
- summarize repository purpose, current state, and next tasks
- do not load cold context unless the task needs it

Hot context:
- `ai/core/agent-invariants.md`
- `ai/core/repo-purpose.md`
- `ai/state/current-state.md`
- `ai/state/handoff.md`

Cold context:
- `ai/core/*`
- `ai/indexes/*`
- `ai/state/recent-validations.md`
- `ai/log/events.jsonl`
- `ai/log/archive/*-summary.md`
- `ai/log/archive/*.jsonl`
- `ai/state/benchmark-state.md`

Conditional cold-load matrix (additive hints, not hard gates):

| Task signal | Also load |
|---|---|
| release/publish/signing/versioning work or touching `RELEASE.md`, `.github/workflows/release.yml`, `pom.xml`, `pojo-lens*/pom.xml` | `ai/core/runbook.md`, `ai/state/recent-validations.md` |
| benchmark/JMH work or touching `pojo-lens-benchmarks/**`, `benchmarks/**`, `scripts/benchmark-*` | `ai/state/benchmark-state.md`, `ai/core/benchmark-context.md` |
| public API/docs alignment work or touching `README.md`, `MIGRATION.md`, `docs/**` | `ai/core/readme-alignment.md`, `ai/core/documentation-index.md` |
| module topology/build boundary work or touching module structure and build wiring | `ai/core/module-index.md`, `ai/core/system-boundaries.md`, `ai/core/architecture-map.md` |
| test strategy or validation history work | `ai/core/test-strategy.md`, `ai/state/recent-validations.md` |
| AI memory maintenance or touching `ai/**`, `scripts/refresh-ai-memory*`, `scripts/query-ai-memory*` | `ai/AGENTS.md`, `ai/core/discovery-notes.md`, `ai/state/recent-validations.md` |

Routing fallback:
- if task intent is broad or ambiguous after applying the matrix, run:
  `scripts/query-ai-memory.ps1 -Query "<task keywords>" -Limit 5`
- for domain-specific precision, add facets:
  `-Kind ai-core` for architecture/module facts
  `-Tier hot,warm` for recency-focused state
  `-Path "ai/core/*"` or `-Path "ai/state/*"` to constrain scope
- prefer top non-archive hits before opening archive logs

Session rules:
- load hot context once per new session
- do not reload hot context after every work package
- reload only if state files changed outside the current edit flow, context was lost, or deeper repo knowledge is needed
- if the current task edits `ai/state/*`, do not immediately reload hot context unless the updated wording is needed for the next decision

Memory rules:
- follow `/ai/AGENTS.md` when updating memory
- treat cross-references between `AGENTS.md` and `ai/AGENTS.md` as guidance links, not recursive load triggers
- code, tests, and build config override `/ai` if facts conflict
- `ai/indexes/*.json` and optional `ai/indexes/cold-memory.db` are derived artifacts; refresh them with `scripts/refresh-ai-memory.ps1` after structural or documentation changes
- run `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` after changing the AI memory retrieval path

Context budget and summarization:
- hot context hard cap: `240` lines and `24 KB` total across the 4 hot files
- hot context target range: `160-200` total lines
- summarize state using short, date-stamped bullets; keep one fact per bullet
- remove duplicated facts across `ai/state/current-state.md` and `ai/state/handoff.md` unless a repetition is operationally required
- move durable multi-session facts from `ai/state/*` to `ai/core/*`
- compact log history with `scripts/refresh-ai-memory.ps1 -CompactLog` when event noise grows
- after AI memory edits, run:
  `scripts/refresh-ai-memory.ps1`
  `scripts/refresh-ai-memory.ps1 -Check`

End of session:
- update `ai/state/current-state.md`
- update `ai/state/handoff.md`
- append significant discoveries to `ai/log/events.jsonl` if useful
- compact older log history with `scripts/refresh-ai-memory.ps1 -CompactLog` when the active log grows noisy
