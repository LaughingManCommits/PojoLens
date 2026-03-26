# TODO

## Current Roadmap: Pre-Adoption Simplification

Use this roadmap only if the library still has no meaningful external adoption
and a smaller public surface is worth small breaking changes before wider use.

## Objective

- reduce the remaining overlapping public surface before wider adoption
- keep explicit entry points as the primary public API
- keep reusable wrappers intact
- move cache and policy guidance toward `PojoLensRuntime`

## Decision Gate

Before implementation starts, confirm one product decision:
- pre-adoption breaking changes are acceptable for the next release

## Status Model

- `Done`: completed and reflected in repo state or decisions
- `In progress`: active current work
- `Ready`: next executable work with no unresolved dependency
- `Planned`: sequenced work, not started yet
- `Blocked`: waiting on an explicit product or release decision

## Execution Board

| Work Package | Priority | Status | Dependency |
| --- | --- | --- | --- |
| `WP7.1` Facade Method Audit | `P0` | `Done` | decision gate |
| `WP7.2` Facade Fate Decision | `P0` | `Done` | `WP7.1` findings |
| `WP7.3` Runtime-First Cache Policy Audit | `P0` | `Done` | decision gate |
| `WP7.4` Simplification Implementation | `P0` | `Done` | `WP7.2` and `WP7.3` decisions |
| `WP7.5` Docs, Examples, and Tests Refresh | `P1` | `Done` | `WP7.4` |

## Work Packages

### `WP7.1` Facade Method Audit

Priority:
- `P0`

Status:
- `Done`

Goal:
- audit every public `PojoLens` facade method and classify it as:
  `keep helper-only`, `deprecate in favor of explicit entry point`, or
  `remove before wider adoption`

Deliverables:
- a keep/deprecate/remove table for the full `PojoLens` facade
- explicit notes for query entry, SQL-like entry, chart entry, config access,
  and compatibility-only helpers

Acceptance criteria:
- every public `PojoLens` method has a disposition
- overlap with `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, and
  `PojoLensRuntime` is explicitly documented

Result:
- delivered in `docs/consolidation-review.md` under
  `Pre-Adoption Facade Audit`

### `WP7.2` Facade Fate Decision

Priority:
- `P0`

Status:
- `Done`

Goal:
- make the concrete keep/deprecate/remove decision for facade query and chart
  entry methods:
  `newQueryBuilder`, `parse`, `template`, and `toChartData`

Deliverables:
- final disposition for each overlapping facade entry method
- migration wording for docs and release notes
- explicit decision on whether `PojoLens` remains helper-only or keeps selected
  convenience entry methods

Acceptance criteria:
- new users see one clear query-entry story
- the facade no longer competes with the explicit entry points in onboarding

Result:
- decision recorded in `docs/consolidation-review.md`:
  `PojoLens` becomes a helper-only facade in the pre-adoption path and
  `newQueryBuilder`, `parse`, `template`, and `toChartData` are slated for
  removal before wider adoption
- migration wording recorded in `MIGRATION.md`
- entry-point guidance updated in `docs/entry-points.md`

### `WP7.3` Runtime-First Cache Policy Audit

Priority:
- `P0`

Status:
- `Done`

Goal:
- audit static and global cache-policy APIs on `PojoLens`, `PojoLensCore`, and
  `PojoLensSql` and map each one to a `PojoLensRuntime`-first replacement story

Deliverables:
- API inventory for static/global cache-policy surface
- replacement mapping to instance-scoped runtime configuration
- explicit identification of any static/global API that must remain for
  compatibility reasons

Acceptance criteria:
- runtime policy guidance clearly prefers `PojoLensRuntime`
- each global policy API either has a replacement path or an explicit
  justification for staying

Result:
- decision recorded in `docs/consolidation-review.md`:
  no public global cache-policy owner should remain after pre-adoption cleanup;
  the only public tuning surface should be `PojoLensRuntime`
- runtime-first replacement map recorded in `docs/caching.md`
- migration wording recorded in `MIGRATION.md`

### `WP7.4` Simplification Implementation

Priority:
- `P0`

Status:
- `Done`

Goal:
- implement the chosen facade and cache-policy simplification path

Deliverables:
- deprecations or removals, depending on the pre-adoption release decision
- focused public-surface review
- focused binary-compat review if any deprecated methods remain in place

Acceptance criteria:
- overlapping facade and global-policy paths are materially reduced
- the implementation matches the documented keep/deprecate/remove decisions

Result:
- removed `PojoLens` facade query/chart entry methods and facade cache delegates
- removed public static/global cache policy methods from `PojoLensSql` and
  `PojoLensCore`
- kept explicit static entry points working on internal default singleton caches
- wired `PojoLensRuntime.parse(...)` and `runtime.newQueryBuilder(...)` onto the
  runtime-owned cache objects
- updated benchmark/runtime/cache/public-api tests to the new surface
- validations passed:
  `mvn -q -pl pojo-lens -am test-compile`
  `mvn -q -pl pojo-lens-benchmarks -am test`
  `mvn -q test`

### `WP7.5` Docs, Examples, and Tests Refresh

Priority:
- `P1`

Status:
- `Done`

Goal:
- make the smaller public surface internally consistent across docs, examples,
  and regression coverage

Deliverables:
- updated README and selection docs
- refreshed examples using the chosen canonical entry points
- updated public API and regression tests where surface expectations changed

Acceptance criteria:
- docs, examples, and tests all reflect the simplified public surface
- reusable wrappers remain positioned as intentional specialized abstractions,
  not simplification targets

Result:
- refreshed `MIGRATION.md`, `docs/caching.md`, and `docs/sql-like.md` to match
  the helper-only facade and runtime-only public cache tuning model
- updated public API, runtime cache, SQL-like cache, and benchmark parity tests
  to the explicit entry-point/runtime-first surface
