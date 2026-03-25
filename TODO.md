# TODO

## Current Roadmap: Product Surface Realignment

## Execution Board

Status model:
- `Done`: completed and reflected in repo state or roadmap decisions
- `In progress`: active current work
- `Ready`: next executable work with no unresolved dependency
- `Planned`: sequenced work, not started yet
- `Blocked`: intentionally waiting on earlier roadmap output

| Spike | Priority | Status | Dependency |
| --- | --- | --- | --- |
| 1. Feature Surface Audit and Positioning | `P0` | `Done` | none |
| 2. Entry Point Realignment | `P0` | `Done` | spike 1 complete |
| 3. Reusable Wrapper Consolidation | `P0` | `Done` | spike 1 complete |
| 4. Advanced Feature Containment | `P1` | `Done` | spikes 1-3 decisions |
| 5. Docs and Example Navigation Realignment | `P1` | `Ready` | spikes 1-4 decisions |
| 6. Consolidation and Deprecation Candidate Review | `P2` | `Blocked` | spikes 1-5 decisions |

### Feature Coherence Snapshot

Current assessment:
- The core engine is coherent: PojoLens is one in-memory POJO query runtime with two primary query styles, fluent and SQL-like.
- The main realignment problem is not duplicate execution engines. It is surface-area overlap in entry points, reusable wrappers, configuration paths, and documentation narrative.
- Most overlap is convenience-layer overlap, not behavior-layer overlap. That means the next work should focus on positioning, API guidance, and selective consolidation rather than adding more major features.

Primary overlap zones:
- entry points: `PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`
- reusable wrappers: `ReportDefinition`, `ChartQueryPreset`, `StatsViewPreset`
- configuration paths: static/global `PojoLens.*` controls vs instance-scoped `PojoLensRuntime`
- product narrative: core querying features are currently presented alongside charts, presets, telemetry, fixtures, metamodel generation, and benchmarking at similar prominence

Realignment principle:
- keep one clear core story
- keep convenience APIs, but make their place explicit
- keep advanced/tooling APIs, but stop presenting them like primary product pillars

### 1) Feature Surface Audit and Positioning

Priority:
- `P0`

Status:
- `Done`

Problem:
- The repository now exposes enough capabilities that the product story is broader than the core query engine, and new users can read it as multiple libraries bundled together.

Spike goal:
- Define a canonical feature map that separates core query capabilities from convenience workflows, integrations, and tooling.

Spike steps:
1. Inventory public features into four buckets:
   `core query engine`, `workflow helpers`, `integration`, `tooling`.
2. Decide which features are first-class recommended paths and which are advanced or compatibility paths.
3. Align README, modules, and stability docs on one terminology set.
4. Record explicit keep/de-emphasize/merge decisions for overlapping feature families.

Work packages:
- `WP1.1` `P0` `Done`: capture the initial overlap map for entry points, reusable wrappers, config paths, and docs narrative.
- `WP1.2` `P0` `Done`: build a canonical feature-family matrix covering all public-facing capabilities and docs sections.
- `WP1.3` `P0` `Done`: classify each feature as `core`, `workflow helper`, `integration`, `tooling`, `compatibility`, or `advanced`.
- `WP1.4` `P1` `Done`: apply terminology alignment to README, modules, and stability docs after the matrix is agreed.

Acceptance criteria:
- PojoLens can be described in 2-3 sentences without listing unrelated helpers.
- Every public feature belongs to one documented family only.
- README and docs no longer present all adjacent features as equal peers.

### 2) Entry Point Realignment

Priority:
- `P0`

Status:
- `Done`

Problem:
- The project exposes multiple top-level entry points that start similar flows, but the preferred use of each one is not strict enough.

Spike goal:
- Make the recommended starting points explicit and reduce ambiguity between the compatibility facade, explicit API entry points, and runtime-scoped usage.

Spike steps:
1. Decide default guidance for:
   `PojoLens` facade,
   `PojoLensCore`,
   `PojoLensSql`,
   `PojoLensChart`,
   and `PojoLensRuntime`.
2. Define when `PojoLensRuntime` is the preferred entry point instead of static/global APIs.
3. Audit examples and docs so they consistently use the chosen defaults.
4. Mark compatibility-oriented or advanced entry paths clearly in documentation.

Work packages:
- `WP2.1` `P0` `Done`: define the recommended default entry point for service-owned fluent queries.
- `WP2.2` `P0` `Done`: define the recommended default entry point for dynamic/config-driven SQL-like queries.
- `WP2.3` `P0` `Done`: define when `PojoLensRuntime` is the preferred model over static/global configuration.
- `WP2.4` `P1` `Done`: normalize examples so they consistently use the chosen default path per scenario.

Acceptance criteria:
- There is one recommended path for service-owned fluent queries.
- There is one recommended path for dynamic/config-driven SQL-like queries.
- There is one recommended path for DI or multi-tenant runtime configuration.
- Static/global policy APIs are documented as global/advanced, not as the default model.

### 3) Reusable Wrapper Consolidation

Priority:
- `P0`

Status:
- `Done`

Problem:
- `ReportDefinition`, `ChartQueryPreset`, and `StatsViewPreset` all package reusable query execution with slightly different defaults, which creates conceptual overlap.

Spike goal:
- Define a strict abstraction ladder for reusable query wrappers and remove ambiguous positioning.

Spike steps:
1. Compare the three wrappers by responsibility:
   reusable rows,
   reusable chart mapping,
   reusable table metadata,
   totals support,
   join/bundle reuse,
   schema access.
2. Confirm or revise the intended ownership:
   `ReportDefinition` as the general reusable contract,
   `ChartQueryPreset` as chart-first convenience,
   `StatsViewPreset` as table-first convenience.
3. Add or refine bridge methods and docs so the relationship is obvious.
4. Identify duplicate convenience that should be de-emphasized or scheduled for later deprecation.

Work packages:
- `WP3.1` `P0` `Done`: produce a capability matrix for `ReportDefinition`, `ChartQueryPreset`, and `StatsViewPreset`.
- `WP3.2` `P0` `Done`: define the canonical decision rule for when to use each wrapper.
- `WP3.3` `P1` `Done`: standardize bridge methods and wording so wrapper relationships are obvious in code and docs.
- `WP3.4` `P2` `Done`: record overlap that is only documentation noise versus overlap that may justify later deprecation.

Acceptance criteria:
- The project has one documented decision rule for choosing between the three wrappers.
- Wrapper naming and method shapes are consistent where they overlap.
- No wrapper is presented as a separate primary product identity.

### 4) Advanced Feature Containment

Priority:
- `P1`

Status:
- `Done`

Problem:
- Caching, telemetry, regression fixtures, snapshot comparison, metamodel generation, and benchmark tooling are useful, but they currently increase the apparent product scope too early.

Spike goal:
- Keep advanced features public and discoverable while containing them behind a clearly advanced/tooling narrative.

Spike steps:
1. Reclassify advanced features in docs and API guidance.
2. Reduce their prominence in README hero and quick-start flows.
3. Align the stability policy with the same core vs advanced distinction.
4. Add one advanced-features landing section or index for deeper discovery.

Work packages:
- `WP4.1` `P1` `Done`: enumerate advanced/tooling features that should be removed from first-read product messaging.
- `WP4.2` `P1` `Done`: define a single advanced-features index or landing section.
- `WP4.3` `P1` `Done`: align `README.md` and `docs/public-api-stability.md` on core versus advanced terminology.
- `WP4.4` `P2` `Done`: review whether any advanced public APIs need explicit de-emphasis text in docs.

Acceptance criteria:
- Quick-start reading stays focused on query, projection, chart, and table flows.
- Advanced features remain documented without dominating the first product impression.
- Stability and documentation language match on what is stable core vs advanced surface.

### 5) Docs and Example Navigation Realignment

Priority:
- `P1`

Status:
- `Ready`

Problem:
- The docs are individually solid, but navigation still reflects feature breadth more than product decision-making.

Spike goal:
- Reorganize docs and examples so they answer "which path should I use?" before "how do I use every capability?"

Spike steps:
1. Rework README and `docs/usecases.md` around feature-selection decisions.
2. Add a compact selection matrix for:
   query style,
   reusable wrapper choice,
   chart vs table vs report flows,
   runtime configuration choice.
3. Ensure examples stop mixing equivalent abstractions without explanation.
4. Re-run docs consistency and example-backed docs coverage after reorganization.

Work packages:
- `WP5.1` `P1` `Ready`: rewrite README top-level flow so it answers "what is PojoLens?" and "which entry path should I pick?" first.
- `WP5.2` `P1` `Ready`: restructure `docs/usecases.md` into a decision-first selection guide.
- `WP5.3` `P1` `Ready`: add a compact matrix for query style, wrapper choice, chart/table/report path, and runtime configuration.
- `WP5.4` `P1` `Planned`: revalidate docs consistency after navigation changes land.

Acceptance criteria:
- A new user can pick one path quickly from the docs index.
- Examples reinforce the chosen abstraction ladder instead of competing with it.
- README, use cases, and modules docs tell the same story.

### 6) Consolidation and Deprecation Candidate Review

Priority:
- `P2`

Status:
- `Blocked`

Problem:
- Some overlap may require API reduction, not just better documentation, but any cleanup must respect the `1.x` stability policy.

Spike goal:
- Produce a safe consolidation plan for low-value overlap without introducing accidental stable-surface breakage.

Spike steps:
1. List overlapping APIs/features as `keep`, `de-emphasize`, `candidate for deprecation`, or `merge later`.
2. Cross-check each candidate against the stable/advanced tier policy.
3. Add migration notes for any deprecation-worthy advanced APIs.
4. Validate the plan against public-surface and binary-compat guardrails before implementation.

Work packages:
- `WP6.1` `P2` `Blocked`: create a disposition register for overlapping public APIs after spikes 1-5 settle the preferred surface.
- `WP6.2` `P2` `Blocked`: map each candidate to `stable` or `advanced` tier impact.
- `WP6.3` `P2` `Blocked`: draft migration notes for any advanced-surface deprecation candidates.
- `WP6.4` `P2` `Blocked`: validate the consolidation plan with public-surface, binary-compat, and docs guardrails before any code removal.

Acceptance criteria:
- Every overlapping public feature has an explicit disposition.
- Any real API reduction is staged through documented migration, not ad-hoc removal.
- The realignment roadmap stays compatible with `1.x` guarantees.

## Secondary Follow-Up

- Retry Maven Central release workflow for `v1.0.0` after project-surface realignment decisions are stable.
- Continue incremental checkstyle reduction without using baseline refreshes as backlog replacement.
