# TODO

## Current Roadmap: Entropy Reduction

Use this roadmap to reduce code, public-surface overlap, and conceptual sprawl.
Prefer deletions and internalization over reshuffling.

## Objective

- reduce runtime code and public-surface complexity
- keep one default path per job:
  `PojoLensCore`, `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, and
  `ReportDefinition`
- internalize implementation details that do not need public `1.x` contracts
- only take execution-path cleanup that is plausibly performance-neutral or
  performance-positive

## Decision Gate

Resolved for this roadmap:
- stable-surface removals wait for `2.x`
- advanced/helper implementation leaks may be narrowed in `1.x` when the
  stable compatibility gate is unaffected and migration notes are explicit

## Status Model

- `Done`: completed and reflected in repo state or decisions
- `In progress`: active current work
- `Ready`: next executable work with no unresolved dependency
- `Planned`: sequenced work, not started yet
- `Blocked`: waiting on an explicit product or release decision

## Execution Board

| Work Package                                  | Priority | Status    | Dependency                    |
|-----------------------------------------------|----------|-----------|-------------------------------|
| `WP8.1` Public Surface and Entropy Audit      | `P0`     | `Done`    | decision gate                 |
| `WP8.2` Public Leak/Internalization Decision  | `P0`     | `Done`    | `WP8.1` findings              |
| `WP8.3` Wrapper and Binding Simplification    | `P0`     | `Done`    | `WP8.1` findings              |
| `WP8.4` Execution Path Unification Audit      | `P1`     | `Done`    | `WP8.1` findings              |
| `WP8.5` Entropy Reduction Implementation      | `P0`     | `Done` | `WP8.2`, `WP8.3`, `WP8.4`     |
| `WP8.6` Docs, Benchmarks, and Release Refresh | `P1`     | `Done` | `WP8.5`                       |

## Work Packages

### `WP8.1` Public Surface and Entropy Audit

Priority:
- `P0`

Status:
- `Done`

Goal:
- inventory the current public runtime surface and classify each type or API
  family as:
  `default path`, `specialized helper`, `compatibility-only`, `advanced`, or
  `internalize`

Deliverables:
- a public package and type inventory for the runtime artifact
- a shortlist of code-deletion and internalization candidates
- a baseline count for public packages, public types, and duplicate concept
  families

Acceptance criteria:
- every public runtime type is classified
- public implementation leaks are identified explicitly
- duplicate entry, wrapper, binding, or helper stories are called out with
  concrete reduction options

Result:
- delivered in `docs/entropy-audit.md`
- baseline recorded:
  `122` public top-level types across `36` packages
- identified `52` clear internalization candidates and `2` packaging anomalies
- advanced `WP8.2` and `WP8.3` to `Ready`

### `WP8.2` Public Leak/Internalization Decision

Priority:
- `P0`

Status:
- `Done`

Goal:
- decide which public implementation or intermediate types should stop being
  part of the intended library surface

Deliverables:
- a keep or internalize table for public implementation-heavy types such as
  builder/filter implementations, row/intermediate models, and SQL-like AST
  helpers
- explicit compatibility notes for each candidate that cannot move in `1.x`
- a package policy for what must remain public versus move under
  `*.internal.*` or package-private scope

Acceptance criteria:
- no public implementation class remains public without explicit justification
- the intended public package boundary is materially narrower than today
- compatibility implications are documented before implementation work starts

Result:
- delivered in `docs/entropy-internalization-decision.md`
- approved `1.x` internalization for builder/filter internals, execution-plan
  types, SQL-like parser/AST helpers, chart mapping helpers, intermediate row
  models, and support/util packages
- kept `SqlLikeQueryCache`, `FilterExecutionPlanCacheStore`, and
  `SqlLikeErrorCodes` public in `1.x`, with package cleanup deferred unless it
  also removes code

### `WP8.3` Wrapper and Binding Simplification

Priority:
- `P0`

Status:
- `Done`

Goal:
- reduce overlap across reusable wrappers and multi-source binding styles so
  the library exposes fewer peer-level concepts

Deliverables:
- a disposition for `ReportDefinition`, `ChartQueryPreset`,
  `StatsViewPreset`, raw join maps, `JoinBindings`, and `DatasetBundle`
- one canonical reusable-query contract for docs and new code
- one canonical multi-source binding story for docs and new code

Acceptance criteria:
- there is one default reusable wrapper story
- there is one default multi-source binding story
- every overlapping wrapper or binding path is marked `keep`, `de-emphasize`,
  `deprecate`, or `remove later`

Result:
- delivered in `docs/entropy-wrapper-binding-decision.md`
- established `ReportDefinition<T>` as the canonical reusable-query contract
  for docs and new code
- established the default multi-source binding progression as
  `JoinBindings` first, then `DatasetBundle` when the same snapshot is reused,
  with raw map overloads retained only as compatibility-first paths
- realigned README and core docs to lead with `ReportDefinition<T>`,
  `JoinBindings`, and `DatasetBundle`

### `WP8.4` Execution Path Unification Audit

Priority:
- `P1`

Status:
- `Done`

Goal:
- find duplicate execution, planning, binding, or materialization paths whose
  removal can lower code volume and may also improve runtime behavior

Deliverables:
- a map of duplicate internal paths across parse, bind, join, projection, and
  materialization stages
- a shortlist of unification targets tied to benchmark or test coverage
- a risk note for any target that is likely complexity-only with no performance
  upside

Acceptance criteria:
- each target is tied to actual code deletion or path removal
- each target has a validation plan (`tests` and, where relevant, benchmarks)
- execution-path cleanup is sequenced behind surface decisions, not mixed with
  them blindly

Result:
- delivered in `docs/entropy-execution-path-audit.md`
- identified three primary `WP8.5` targets:
  shared fluent stage running, shared SQL-like stage accounting, and unified
  SQL-like output materialization
- identified one low-risk cleanup target:
  optional-join helper consolidation beginning with the unused
  `executeIteratorWithOptionalJoin(...)`
- explicitly deferred prepared-rebind mode removal and broad chart/tabular
  helper convergence as lower-value or higher-risk than the stage-runner work

### `WP8.5` Entropy Reduction Implementation

Priority:
- `P0`

Status:
- `Done`

Goal:
- implement the chosen surface reductions, internalizations, and execution-path
  unifications

Deliverables:
- removed or internalized public surface where approved
- reduced duplicate wrapper or binding paths where approved
- strengthened public-surface guardrails so new public API growth is explicit

Acceptance criteria:
- code and concept count are materially reduced
- default user guidance is simpler than before the change
- validations pass:
  `mvn -q test`
  `scripts/check-doc-consistency.ps1`
- benchmark guardrails are unchanged or improved for touched hot paths

Progress:
- unified SQL-like output materialization now resolves one shared internal
  mode across `filter`, `stream`, and `chart`
- removed the unused optional-join iterator helper
- flat fluent `filter` and `chart` now share one internal materialization
  resolver for window/qualify, fast-array, fast-stats, and raw-row fallback
- internalized `ChartValidation` to a package-private chart helper
- SQL-like explain stage counts now run through an unpaged bound execution
  context and the real fluent execution path, replacing the manual replay and
  ad-hoc `QUALIFY` reconstruction
- grouped fluent `filterGroups(...)` and row-based flat execution now share
  one internal distinct/filter stage runner

Result:
- completed all `WP8.4` execution-path unification targets selected for
  `WP8.5`
- removed the remaining duplicated SQL-like explain stage-replay path
- removed the remaining grouped-vs-flat fluent duplication at the base
  distinct/filter stage

### `WP8.6` Docs, Benchmarks, and Release Refresh

Priority:
- `P1`

Status:
- `Done`

Goal:
- make the simplified library shape visible and durable across docs, benchmarks,
  and release notes

Deliverables:
- updated README and selection docs with the reduced concept set
- migration and release-note wording for any changed advanced/helper surface
- benchmark evidence where execution-path cleanup changed hot internals

Acceptance criteria:
- docs describe one default path per job
- migration text is sufficient for any narrowed advanced/helper surface
- benchmark evidence exists for any cleanup that touched runtime hot paths

Result:
- delivered in `docs/entropy-release-refresh.md`
- refreshed `README.md`, `docs/usecases.md`, `docs/sql-like.md`,
  `docs/benchmarking.md`, `MIGRATION.md`, and `RELEASE.md`
- added benchmark coverage for the exact `WP8.5` paths in
  `StatsQueryJmhBenchmark.fluentGroupedRows` and
  `SqlLikePipelineJmhBenchmark.parseAndExplainExecution`
- captured forked JMH spot-check evidence for grouped fluent execution,
  execution-backed SQL-like explain, and streaming materialization behavior

## Operational Follow-Up

- Maven Central release retry or verification for `v1.0.0` remains the main
  repo-level operational task.
- Treat that release work as operational follow-up, not as a roadmap package in
  this file.
