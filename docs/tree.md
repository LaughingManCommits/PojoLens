# Tree Traversal

`PojoLensTree` reshapes a flat in-memory list with parent IDs into rows that
can be queried by the existing PojoLens engine.

Use it when your data already exists as Java objects and the only missing step
is selecting a subtree before normal SQL-like or natural execution.

## Basic Subtree

```java
List<Employee> subtree = PojoLensTree.subtreeOf(
    employees,
    Employee::getId,
    Employee::getManagerId,
    ceoId
);

List<Employee> rows = PojoLensSql
    .parse("order by salary desc limit 10")
    .filter(subtree, Employee.class);
```

`subtreeOf(...)` is equivalent to:

```java
List<Employee> subtree = PojoLensTree
    .fromFlat(employees, Employee::getId, Employee::getManagerId)
    .subtree(ceoId)
    .toList();
```

## Builder Options

- `.subtree(rootId)` scopes output to one root and its descendants. If omitted,
  output starts from every root in the forest.
- `.maxDepth(n)` stops traversal at depth `n`; roots have depth `0`.
- `.prune(predicate)` includes a node but skips its descendants when the
  predicate returns `false`.
- `.leavesOnly()` returns only nodes with no children in the original index.
- `.toList()` returns `List<T>`, the default output for normal query execution.
- `.toEntries()` returns `List<TreeEntry<T>>` when depth or parent metadata is
  needed.

Example:

```java
List<Employee> leaves = PojoLensTree
    .fromFlat(employees, Employee::getId, Employee::getManagerId)
    .subtree(ceoId)
    .maxDepth(3)
    .leavesOnly()
    .toList();
```

## Traversal Metadata

`TreeEntry<T>` exposes:

- `node()`
- `depth()`
- `parent()`

```java
List<Employee> tier2 = PojoLensTree
    .fromFlat(employees, Employee::getId, Employee::getManagerId)
    .subtree(ceoId)
    .toEntries()
    .stream()
    .filter(entry -> entry.depth() == 2)
    .map(TreeEntry::node)
    .toList();
```

The original POJOs are not mutated. This works with mutable classes, immutable
types, and records.

## SQL-Like Queries

Tree traversal runs before SQL-like parsing and binding:

```java
List<Employee> subtree = PojoLensTree.subtreeOf(
    employees,
    Employee::getId,
    Employee::getManagerId,
    ceoId
);

List<Employee> rows = PojoLensSql
    .parse("where department = :dept order by salary desc limit 10")
    .params(Map.of("dept", "Engineering"))
    .filter(subtree, Employee.class);
```

`PojoLensTree` does not add SQL-like or natural tree syntax. It returns rows;
the existing engine handles filtering, ordering, grouping, projection, charts,
and reports.

## Determinism And Validation

- Node IDs must be non-null.
- Duplicate IDs fail fast with `IllegalArgumentException`.
- Parent-ID cycles fail fast with `IllegalArgumentException`.
- Nodes with `null` parent IDs are roots.
- Nodes whose parent ID is missing from the ID set are roots.
- Forest roots keep first-seen source order.
- Siblings keep source order.
- Output uses breadth-first traversal.

## Boundaries

`PojoLensTree` is a row-shaping helper, not an ORM, database feature, parser
extension, or graph runtime.

Use it for flat parent-ID lists that need subtree selection before normal
in-memory querying. For arbitrary graph algorithms or non-tree traversal, use a
dedicated graph library before passing the resulting rows to PojoLens.
