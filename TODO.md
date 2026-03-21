# TODO

## High-Value Features (Current Focus)

- [x] Add `OFFSET` pagination primitives for fluent + SQL-like query flows.
- [x] Add SQL-like named parameter support for pagination clauses (`LIMIT :limit OFFSET :offset`).
- [x] Add first-class keyset/cursor pagination API primitives (beyond documented query patterns).
- [x] Add streaming execution output (iterator/stream) to avoid full materialization on large datasets.
- [x] Add optional in-memory indexes for hot filter/join paths to improve repeated-query latency.

## Platform Hardening (Phase 2)

- [ ] Define and document a small **stable public API surface** (vs advanced/internal APIs), with explicit compatibility guarantees.
- [ ] Add **binary compatibility checks** to CI (for example `revapi` or `japicmp`) to block accidental breaking API changes before release.

## Spike Plan

### 1) Pagination Primitives (`OFFSET` + Keyset/Cursor)

Problem:
- API consumers need deterministic page navigation; `LIMIT` alone is insufficient.

Spike goal:
- Add first-class pagination support with predictable ordering semantics.

Spike steps:
1. [x] Define fluent and SQL-like syntax/contracts for `OFFSET`.
2. [x] Add SQL-like named parameter support for `LIMIT/OFFSET` (`:limit`, `:offset`) with integer and non-negative validation.
3. [x] Design keyset/cursor API shape (token format, sort-field requirements, null-handling).
4. [x] Validate behavior with large sorted datasets and tie-heavy keys.
5. [x] Document guidance on when to prefer offset vs keyset.

Acceptance criteria:
- Offset pagination is supported in fluent and SQL-like flows.
- SQL-like pagination supports both numeric literals and named parameters for `LIMIT/OFFSET`.
- Keyset/cursor pagination has a documented API contract and tests.
- Docs include stability guidance for deterministic paging.

### 2) Streaming Execution Output

Problem:
- Full list materialization is expensive for large result sets and pipeline consumers.

Spike goal:
- Provide a streaming/iterator execution mode for memory-efficient consumption.

Spike steps:
1. [x] Evaluate API shape (`Iterator<T>`, `Stream<T>`, or both) and resource lifecycle.
2. [x] Implement fluent and SQL-like streaming prototypes.
3. [x] Validate behavior with filtering and projection paths, with list-backed fallback on complex shapes (join/group/having/ordered windows).
4. [x] Benchmark memory/latency tradeoffs vs existing list materialization.

Acceptance criteria:
- At least one stable streaming API is available and documented.
- Behavior is covered by tests across core query features.
- Benchmark notes show memory benefits on large datasets.

### 3) Optional In-Memory Indexes for Hot Paths

Problem:
- Repeated filter/join queries on large snapshots still pay full scan costs.

Spike goal:
- Introduce opt-in index structures for frequently queried fields.

Spike steps:
1. [x] Identify high-impact query shapes (equality filters, join keys, compound predicates).
2. [x] Design index lifecycle and invalidation strategy for snapshot data.
3. [x] Prototype index-assisted execution path with fallback to scan.
4. [x] Measure gain/loss across warm and cold benchmarks.

Acceptance criteria:
- Optional index API is defined and documented.
- Indexed execution falls back safely when index is unavailable/inapplicable.
- Benchmarks show clear wins on targeted hot-path scenarios.

### 4) Stable Public API Surface

Problem:
- The public surface is broad, making long-term compatibility harder to reason about.

Spike goal:
- Define a minimal, versioned "stable" API contract and label remaining APIs as advanced/internal.

Spike steps:
1. Inventory public classes and methods currently reachable from `PojoLens`, `PojoLensRuntime`, `PojoLensCore`, and `PojoLensSql`.
2. Propose a stable subset for 1.x and document inclusion/exclusion rules.
3. Add a "Public API Stability" section to README and docs, including upgrade guarantees.
4. Add tests that assert availability/behavior of stable entry points.

Acceptance criteria:
- Stable API list is published in docs.
- CI has at least one test suite asserting stable contract behavior.
- A deprecation policy is documented for moving APIs between tiers.

### 5) Binary Compatibility Checks in CI

Problem:
- Breaking API changes can slip into releases without compile/runtime failures in internal tests.

Spike goal:
- Add automated binary compatibility verification to release/CI workflows.

Spike steps:
1. Evaluate `revapi` vs `japicmp` for Maven integration and report quality.
2. Configure baseline artifact selection (latest release tag or local reference build).
3. Add CI step that fails on non-allowed binary breaks.
4. Document suppression/override process for intentional major-version changes.

Acceptance criteria:
- Compatibility check runs in CI on PRs/releases.
- CI fails on an unapproved binary break.
- Project docs include "how to approve an intentional break" instructions.
