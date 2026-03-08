# TODO

## Planned Work Packages

### WP-17: Explain/Diagnostics for Executed SQL-like Queries
Status: `completed`
Priority: `P2`

Problem:
- Hard to inspect what happened at runtime when a query behaves unexpectedly.

Goal:
- Provide a stable diagnostics view for SQL-like execution.

Scope:
- Expand explain payload with:
  - normalized query
  - resolved sort direction
  - projection mode (direct vs alias/computed)
  - join source names and binding status
  - parameter snapshot (safe/redacted format)
- Keep output deterministic for tests.

Tests:
- Contract tests for explain payload shape and key fields.
- Backward-compatibility checks for existing explain keys.

Definition of done:
- Explain output is useful for debugging without changing execution semantics.
- `mvn test` passes.

### WP-18: Remove Legacy Bind-First APIs
Status: `completed`
Priority: `P1`

Problem:
- SQL-like bind-first has two styles (`bind(...).initFilter().filter(...)` and `bindTyped(...).filter()`).
- Keeping both increases API surface and onboarding confusion.

Goal:
- Remove legacy bind-first SQL-like APIs and make typed bind-first the single path.

Scope:
- Remove legacy bind-first SQL-like methods immediately (no deprecation window).
- Keep non-SQL-like fluent `Filter` API unchanged.
- Remove legacy references from docs/tests and keep only typed bind-first examples.
- Treat as intentional pre-adoption breaking cleanup before public usage.

Tests:
- Add removal coverage ensuring typed bind-first parity for all current legacy scenarios.

Definition of done:
- Legacy bind-first SQL-like APIs are removed from public surface.
- All docs/examples/tests use typed bind-first path only.
- `mvn test` passes.

### WP-19: Typed Parameter Builder (`SqlParams`)
Status: `completed`
Priority: `P1`

Problem:
- `params(Map<String, ?>)` is flexible but key strings are typo-prone.

Goal:
- Add a typed parameter builder with better IDE discoverability.

Scope:
- Add `SqlParams.builder().put(...).put(...).build()`.
- Add `SqlLikeQuery.params(SqlParams)` overload.
- Keep existing `params(Map<String, ?>)` API unchanged.

Definition of done:
- Builder API available and documented.
- Validation behavior matches map-based path.
- `mvn test` passes.

### WP-20: Query Templates with Parameter Schema
Status: `completed`
Priority: `P1`

Problem:
- Repeatedly validating required parameter names across reused queries is manual.

Goal:
- Support reusable query templates with declared parameter schema.

Scope:
- Add template object for query + expected params.
- Validate missing/unknown params against schema before execution.
- Support repeated execution with different parameter sets.

Definition of done:
- Template API supports safe reuse and deterministic validation.
- Backward compatibility preserved.
- `mvn test` passes.

### WP-21: Stable Error Codes + Troubleshooting Links
Status: `completed`
Priority: `P2`

Problem:
- Error text is readable but hard to index/search in large teams.

Goal:
- Add stable error codes and docs references.

Scope:
- Introduce deterministic code format (e.g. `EQ-SQL-VAL-001`).
- Attach codes to key parser/validation/runtime errors.
- Add docs table mapping codes to meaning and fixes.

Definition of done:
- Common developer-facing errors include codes.
- Docs include searchable code reference.
- `mvn test` passes.

### WP-22: Explain Stage Row Counts
Status: `completed`
Priority: `P1`

Problem:
- Hard to pinpoint where rows are filtered out during SQL-like execution.

Goal:
- Add stage-level row counts in explain diagnostics.

Scope:
- Extend explain with counts before/after WHERE, GROUP, HAVING, ORDER, LIMIT.
- Keep output deterministic and low-overhead.

Definition of done:
- Explain payload includes stage counts for executed query paths.
- Contract tests cover payload shape and values.
- `mvn test` passes.

### WP-23: Strict Parameter Type Mode
Status: `completed`
Priority: `P2`

Problem:
- Some type mismatches fail late during comparison.

Goal:
- Add optional strict mode for early parameter type validation.

Scope:
- Runtime/config flag for strict parameter typing.
- Deterministic validation errors for mismatched parameter types.
- Default behavior remains backward compatible.

Definition of done:
- Strict mode can be enabled per runtime/query.
- Type mismatch diagnostics are clear and consistent.
- `mvn test` passes.

### WP-24: Field Metamodel/Constants Generator
Status: `completed`
Priority: `P2`

Problem:
- String-based field references are fragile in fluent/spec flows.

Goal:
- Provide generated field constants/metamodel helpers.

Scope:
- Add optional generator for field-name constants per domain model.
- Document usage for fluent builders/specs/charts.

Definition of done:
- Generated constants integrate with existing APIs.
- Documentation and sample generation flow provided.
- `mvn test` passes.

### WP-25: Fluent vs SQL-like Parity Test Helper
Status: `completed`
Priority: `P2`

Problem:
- Teams migrating queries need easier parity assertions.

Goal:
- Provide test utility that compares fluent and SQL-like outputs.

Scope:
- Add reusable test helper for parity assertions.
- Include order-aware and order-agnostic comparison modes.

Definition of done:
- Helper is used by internal tests and documented for users.
- Reduces repetitive parity test boilerplate.
- `mvn test` passes.

### WP-27: SQL-like Query Lint Mode
Status: `completed`
Priority: `P2`

Problem:
- Some patterns are legal but risky (hard to maintain or reason about).

Goal:
- Add optional lint mode for non-blocking best-practice warnings.

Scope:
- Warn on risky patterns (dynamic string interpolation, broad `select *`, limit without order).
- Make lint warnings deterministic and suppressible.

Definition of done:
- Lint mode can be enabled independently of strict validation.
- Warnings are documented and test-covered.
- `mvn test` passes.

### WP-28: Runtime Policy Presets
Status: `completed`
Priority: `P2`

Problem:
- Runtime policy setup (cache/validation flags) is repetitive.

Goal:
- Add preconfigured runtime policy profiles for common environments.

Scope:
- Add preset builders (e.g. `dev`, `prod`, `test`).
- Keep full manual policy control available.

Definition of done:
- Presets provide sensible defaults and clear docs.
- Existing runtime APIs remain compatible.
- `mvn test` passes.

### WP-29: SQL-like Subquery Support
Status: `completed`
Priority: `P2`

Problem:
- The SQL-like grammar cannot express nested filtering or derived result constraints.

Goal:
- Add scoped subquery support for the highest-value SQL-like cases without turning the parser into a full SQL engine.

Scope:
- Support a limited subquery form for `IN`, `EXISTS`, or derived aggregate filtering.
- Keep execution deterministic and clearly document unsupported nesting patterns.
- Preserve current validation quality and error reporting for nested queries.

Definition of done:
- At least one documented subquery shape is supported end-to-end.
- Parser/validation/runtime behavior is deterministic and test-covered.
- `mvn test` passes.

### WP-30: Multi-Join SQL-like Plans
Status: `completed`
Priority: `P2`

Problem:
- SQL-like execution supports only a single join, which blocks realistic relational-style query flows.

Goal:
- Support multiple SQL-like joins in one query with predictable binding and validation behavior.

Scope:
- Extend grammar and binder for chained joins.
- Validate join source ordering, alias/source references, and ambiguous field usage.
- Keep explain output and diagnostics readable for multi-join execution.

Definition of done:
- SQL-like queries can execute with more than one join source.
- Validation and explain output remain deterministic.
- `mvn test` passes.

### WP-31: Expanded SQL-like Aggregation Semantics
Status: `completed`
Priority: `P2`

Problem:
- Current aggregation semantics are intentionally narrow and reject several useful grouped-query patterns.

Goal:
- Broaden SQL-like aggregation support while keeping validation strict and predictable.

Scope:
- Support additional valid aggregate/query combinations beyond the current minimal contract.
- Revisit grouped alias handling and aggregate expression flexibility where semantics are unambiguous.
- Document supported aggregation rules and failure modes clearly.

Definition of done:
- Common grouped-query patterns no longer require awkward workarounds.
- Validation remains deterministic and well documented.
- `mvn test` passes.

### WP-32: Chart/Report Query Presets
Status: `completed`
Priority: `P2`

Problem:
- Common dashboard query + chart wiring is still repetitive even when the underlying data shape is simple.

Goal:
- Provide reusable presets for common chart/report query flows without forcing users into a reporting framework.

Scope:
- Add helpers for common patterns such as category totals, time-series totals, and grouped breakdowns.
- Pair query output shape with chart mapping defaults where the intent is unambiguous.
- Keep the preset layer optional and lightweight.

Definition of done:
- Common reporting/chart flows require materially less boilerplate.
- Presets remain composable with existing query/chart APIs.
- `mvn test` passes.

### WP-33: Nested Object Path Querying
Status: `completed`
Priority: `P2`

Problem:
- Rich object graphs still require flattening assumptions or awkward workarounds when teams want to query nested properties directly.

Goal:
- Support deterministic nested object-path querying in a way that fits POJO-first usage better than relational-style joins.

Scope:
- Add dotted-path field references for fluent and SQL-like querying where semantics are clear.
- Validate nested paths strictly and respect `@Exclude`.
- Keep error messages and explain output readable for nested references.

Definition of done:
- Common nested POJO properties can be queried without manual flattening workarounds.
- Validation and runtime behavior remain deterministic.
- `mvn test` passes.

### WP-34: Reusable Report Definition API
Status: `completed`
Priority: `P2`

Problem:
- Teams building dashboards often repeat the same projection, grouping, and chart-mapping contracts across endpoints and jobs.

Goal:
- Provide a reusable report-definition abstraction for app-layer analytics over in-memory datasets.

Scope:
- Add a report definition object that captures query, projection, and optional chart mapping.
- Support repeated execution against different dataset snapshots.
- Keep it compatible with both fluent and SQL-like query styles where practical.

Definition of done:
- Report definitions can be reused across multiple app-layer analytics flows.
- Boilerplate for repeated grouped/report queries is reduced.
- `mvn test` passes.

### WP-35: Query Telemetry Hooks
Status: `completed`
Priority: `P2`

Problem:
- `explain()` is useful for local debugging but does not integrate cleanly with application observability or profiling.

Goal:
- Expose low-overhead telemetry hooks for query execution stages.

Scope:
- Add optional listener/hook support for parse, bind, filter, aggregate, order, and chart stages.
- Emit deterministic timing/count metadata without introducing a logging dependency.
- Keep the default path low-overhead when hooks are disabled.

Definition of done:
- Applications can capture query telemetry without patching core execution paths.
- Telemetry integrates cleanly with the existing explain/diagnostics model.
- `mvn test` passes.

### WP-36: Dataset Bundle Execution Context
Status: `completed`
Priority: `P2`

Problem:
- App-layer analytics often operate on a named snapshot of related datasets, but repeated `joinSources` maps are noisy and error-prone.

Goal:
- Provide a reusable dataset bundle/context for multi-source in-memory query execution.

Scope:
- Add an immutable bundle that captures a primary dataset plus named secondary datasets.
- Integrate bundle execution with SQL-like queries, joins, subqueries, and chart flows.
- Preserve explicit behavior and deterministic validation when named sources are missing.

Definition of done:
- Repeated multi-source execution no longer requires ad hoc `Map<String, List<?>>` plumbing.
- Bundle-based execution remains compatible with existing runtime APIs.
- `mvn test` passes.

### WP-37: Computed Field Registry
Status: `completed`
Priority: `P2`

Problem:
- Repeated derived dimensions and measures currently have to be rebuilt inline across queries, reports, and charts.

Goal:
- Provide a reusable registry for named computed fields that fits POJO-first analytics better than ad hoc expression duplication.

Scope:
- Add a way to register named derived fields/measures with explicit output types.
- Make computed fields reusable from fluent queries, SQL-like queries, and report/chart flows where semantics are clear.
- Surface computed-field usage in validation, explain output, and error messages.

Definition of done:
- Common derived business fields no longer need to be redefined across multiple query paths.
- Computed fields remain deterministic and type-safe at validation time.
- `mvn test` passes.

### WP-38: Snapshot Comparison Queries
Status: `completed`
Priority: `P2`

Problem:
- App-layer analytics often need "current vs previous snapshot" comparisons, but delta and churn logic is still manual and repetitive.

Goal:
- Add first-class snapshot comparison helpers for in-memory datasets without turning the library into a temporal database engine.

Scope:
- Support keyed comparisons for added, removed, unchanged, and changed rows across two snapshots.
- Add helpers for common delta metrics and comparison-friendly projections.
- Keep comparison semantics explicit and deterministic for missing keys and null handling.

Definition of done:
- Teams can express common snapshot delta/report workflows without custom diff plumbing.
- Comparison behavior is documented and predictable.
- `mvn test` passes.

### WP-39: Time Bucket and Calendar Presets
Status: `completed`
Priority: `P2`

Problem:
- Time-series analytics still require repeated manual bucketing, label normalization, and calendar-boundary handling.

Goal:
- Provide lightweight time-bucket and calendar presets for app-layer analytics and chart-ready outputs.

Scope:
- Add standard day, week, month, quarter, and year bucketing presets.
- Support explicit timezone and week-start/calendar behavior where required for deterministic output.
- Integrate presets with grouped queries, chart helpers, and explain output.

Definition of done:
- Common time-series reporting flows no longer need repeated custom bucketing logic.
- Calendar behavior is explicit rather than implicit.
- `mvn test` passes.

### WP-40: Tabular Result Schema Metadata
Status: `completed`
Priority: `P2`

Problem:
- Query results are executable today, but downstream table/report rendering still lacks a first-class schema/column metadata contract.

Goal:
- Expose deterministic tabular result metadata so application-layer tables, exports, and reports can be built without reflection-heavy glue.

Scope:
- Add column metadata for name, label, type, order, and optional formatting hints.
- Keep metadata aligned with projections, aliases, computed fields, and grouped outputs.
- Make schema metadata available from report definitions and chart/report presets where relevant.

Definition of done:
- Query consumers can render stable tables and exports without reverse-engineering row shapes.
- Metadata stays consistent with query validation and projection semantics.
- `mvn test` passes.

### WP-41: Query Snapshot Regression Fixtures
Status: `completed`
Priority: `P2`

Problem:
- Teams adopting SQL-like queries need a simple way to lock query behavior against known dataset snapshots during refactors and migrations.

Goal:
- Provide a lightweight regression-fixture model for deterministic query and report outputs over fixed in-memory snapshots.

Scope:
- Add helpers to capture expected rows, metrics, and optionally explain/lint outputs for a named dataset snapshot.
- Support fluent and SQL-like parity assertions over fixture-backed datasets.
- Keep fixtures code-first and test-friendly rather than introducing a custom external format requirement.

Definition of done:
- Query behavior can be regression-tested with less custom harness code.
- Migration and refactor safety improves for app-layer analytics flows.
- `mvn test` passes.

### WP-42: Performance Baselines and Budgets
Status: `completed`
Priority: `P2`

Problem:
- Performance matters for an in-memory query engine, but current benchmarking is framed mostly as internal tooling and snapshots rather than a clear product-level performance contract.

Goal:
- Define a stable performance envelope for the core execution paths and document comparable baselines without over-indexing on competitor marketing claims.

Scope:
- Define benchmark categories for filter, group, join, chart, parse, cache-hit, and cache-miss paths.
- Publish explicit performance budgets for representative dataset sizes and key execution flows.
- Add a conservative external baseline using equivalent plain Java Streams workloads where semantics match.
- Require semantic parity checks for benchmarked comparisons so performance numbers do not hide behavior differences.
- Document comparison caveats clearly and avoid misleading cross-project claims where semantics are not truly comparable.

Definition of done:
- Benchmark documentation explains expected workloads, budgets, and how to interpret results.
- CI/perf checks cover the agreed hot paths with deterministic thresholds.
- Comparable baseline measurements are reproducible and semantics-aligned.
- `mvn test` passes.
