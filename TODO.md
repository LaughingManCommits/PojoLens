# TODO

## Tree Traversal — `PojoLensTree`

Advanced workflow helper for flat POJO lists that have a parent-ID relationship.
Builds a queryable subtree from a flat `List<T>` - the shape you get from a
database, CSV, or REST API - and returns `List<T>` so the existing engine takes
over from there.

Architectural boundary: this is row shaping before query execution, not a
second query engine, parser feature, ORM, persistence layer, or general graph
runtime. `PojoLensTree` must not add SQL-like or natural tree syntax in this
slice; callers feed the returned rows into `PojoLensCore`, `PojoLensSql`, or
runtime-owned equivalents.

Story: **"You have a flat list with parent IDs — PojoLensTree turns it into a queryable subtree."**

Primary flow: `fromFlat` / `subtreeOf` → `toList()` → `PojoLensCore` / `PojoLensSql`.

Public surface goal:
- `laughing.man.commits.PojoLensTree` is the root-package public facade, matching
  `PojoLensCore`, `PojoLensSql`, `PojoLensCsv`, and `PojoLensChart`.
- `laughing.man.commits.tree.TreeEntry` and `TreeTraversalBuilder` hold the
  supporting tree contracts.
- `TreeEntry<T>` (Java 17 record: `node()`, `depth()`, `parent()`) is opt-in
  when traversal metadata is needed. No POJO mutation. Works with immutable
  types and records.

Target API:

```java
// Standard — builder for full control
List<Employee> subtree = PojoLensTree
    .fromFlat(allEmployees, Employee::getId, Employee::getManagerId)
    .subtree(ceoId)
    .maxDepth(3)
    .leavesOnly()
    .toList();

// One-liner — most common case
List<Employee> subtree =
    PojoLensTree.subtreeOf(allEmployees, Employee::getId, Employee::getManagerId, ceoId);

// Feed into existing engine — unchanged from normal PojoLens usage
List<Employee> result = PojoLensCore.newQueryBuilder(subtree)
    .addOrder("salary", 1)
    .limit(10)
    .initFilter()
    .filter(Sort.DESC, Employee.class);

// or SQL-like
List<Employee> result = PojoLensSql
    .parse("where department = :dept order by salary desc limit 10")
    .params(Map.of("dept", "Engineering"))
    .filter(subtree, Employee.class);

// opt-in: toEntries() when traversal metadata is needed
List<Employee> tier2 = PojoLensTree
    .fromFlat(allEmployees, Employee::getId, Employee::getManagerId)
    .subtree(ceoId)
    .toEntries()
    .stream()
    .filter(e -> e.depth() == 2)
    .map(TreeEntry::node)
    .toList();
```

---

### TREE-WP1 — Core implementation

**Goal:** Flat list → queryable subtree.

Files to create:
- `pojo-lens/src/main/java/laughing/man/commits/tree/TreeEntry.java` — public record: `node()`, `depth()`, `parent()`
- `pojo-lens/src/main/java/laughing/man/commits/PojoLensTree.java` — public facade
- `pojo-lens/src/main/java/laughing/man/commits/tree/TreeTraversalBuilder.java` — fluent builder

Behavior:
- `PojoLensTree.fromFlat(List<T> items, Function<T,K> idFn, Function<T,K> parentIdFn)` — entry point; returns builder
- `PojoLensTree.subtreeOf(List<T>, idFn, parentIdFn, K rootId)` → `List<T>` one-liner convenience;
  equivalent to `fromFlat(...).subtree(rootId).toList()`
- Build a source-order-preserving children index from the flat list
- `.subtree(K rootId)` — scope to one root; if not called, includes all roots (forest)
- `.maxDepth(int n)` — stop descending beyond depth n; default unbounded
- `.prune(Predicate<T>)` — if predicate returns false, do not descend into that node's
  children; node itself still included in output
- `.leavesOnly()` — exclude internal nodes; a leaf has no children in the index
- IDs define tree semantics:
  - non-null IDs are required for included nodes
  - duplicate IDs fail fast with `IllegalArgumentException`
  - nodes with `null` parentId or parentId not found in the ID set are roots
- Traversal is deterministic:
  - roots keep first-seen source order
  - siblings keep source order
  - BFS traversal is the default output order
- Cycle handling is ID-based:
  - detect parent-ID cycles before traversal completes
  - fail fast with `IllegalArgumentException`
  - never silently loop or recover by dropping arbitrary nodes
- `.toList()` → `List<T>` primary output; feeds engine directly
- `.toEntries()` → `List<TreeEntry<T>>` opt-in; root has `depth=0`, `parent=null`
- Null-safe input handling: null or empty input list returns empty result
- Null extractor handling: null `idFn` or `parentIdFn` fails fast with `NullPointerException`

Validate: `mvn -B -ntp test`

---

### TREE-WP2 — Tests

**Goal:** Full coverage for all options, edge cases, and engine integration.

Files to create:
- `pojo-lens/src/test/java/laughing/man/commits/tree/PojoLensTreeTest.java`

Test cases:
- `fromFlat` builds correct source-order-preserving parent-child index
- `.subtree(id)` returns only descendants of that root
- `subtreeOf(...)` matches `fromFlat(...).subtree(...).toList()`
- Forest (no `.subtree()` call) → all nodes across all roots
- Orphan node (parentId missing from ID set) treated as root
- Forest root order preserves first-seen source order
- Sibling order preserves source order
- `maxDepth` stops at correct level
- `prune` — pruned node in output; its children excluded
- `leavesOnly` — only nodes with no children in index returned
- `toEntries()` depth values correct — root=0, children=1, grandchildren=2
- `toEntries()` parent refs correct — root parent null; others point to parent node
- `toList()` equals `toEntries().stream().map(TreeEntry::node).toList()`
- Duplicate IDs fail fast
- Null node ID fails fast
- Cycle detection — parent-ID cycle fails fast and does not loop
- Null input list → empty result
- Null extractor → `NullPointerException`
- Single-node list (no parentId) → one entry, depth=0, parent=null
- Integration: `.toList()` piped into `PojoLensCore` filter + order
- Integration: `.toList()` piped into `PojoLensSql`
- Public API contract: root facade and supporting tree contracts remain present

Validate: `mvn -B -ntp test`

---

### TREE-WP3 — Docs and surface update

**Goal:** Document the feature; add to public surface.

Files to create:
- `docs/tree.md` — full guide: the flat-list story, `fromFlat` vs `subtreeOf`, builder
  options (`maxDepth`, `prune`, `leavesOnly`), `toList` vs `toEntries`, chaining with
  `PojoLensCore` and `PojoLensSql`, limitations (requires ID/parentId fields; for
  in-memory root-node traversal or non-tree graphs use JGraphT directly)

Files to update:
- `README.md` — add `PojoLensTree` to Pick A Path table, Capability Snapshot, API Entry
  Points, and Documentation Map Core Guides
- `docs/entry-points.md` — add `PojoLensTree` with usage guidance
- `docs/product-surface.md` — classify `PojoLensTree` in correct surface tier
- `docs/public-api-stability.md` — add the intended stability tier and public contracts
- `docs/modules.md` — mention tree helper in runtime-layering notes if needed
- `CHANGELOG.md` — add under `[Unreleased]`
- public API contract tests — add `PojoLensTree` and tree support contracts

Validate: `scripts/check-doc-consistency.ps1`

---
