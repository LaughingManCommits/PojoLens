# Snapshot Comparison

`SnapshotComparison` compares two keyed in-memory snapshots and returns queryable delta rows plus summary counts.

This is a follow-on analysis/testing feature rather than part of the default
query onboarding path.

Use it when you need:

- added rows
- removed rows
- changed rows
- unchanged rows

## Basic Comparison

```java
SnapshotComparison<Employee, Integer> comparison = SnapshotComparison
    .builder(currentSnapshot, previousSnapshot)
    .byKey(employee -> employee.id);
```

Available outputs:

- `comparison.rows()`
- `comparison.added()`
- `comparison.removed()`
- `comparison.changed()`
- `comparison.unchanged()`
- `comparison.summary()`

## Diff Row Shape

Each `SnapshotDeltaRow<T, K>` includes:

- `key`
- `keyText`
- `changeType`
- `added`, `removed`, `changed`, `unchanged`
- `currentPresent`, `previousPresent`
- `changedFieldCount`
- `changedFieldSummary`
- `changedFields`
- `current`
- `previous`

`keyText`, `changeType`, the boolean flags, and the change-count fields are queryable directly.
`key`, `current`, and `previous` remain available for application code.

## Querying Deltas

```java
List<ChangeProjection> rows = PojoLensSql
    .parse("select keyText, changedFieldCount, changedFieldSummary "
            + "where changeType = 'CHANGED' order by keyText asc")
    .filter(comparison.rows(), ChangeProjection.class);
```

## Reporting And Charts

Delta rows are just another in-memory dataset, so they can be used directly in reports and chart flows:

```java
ReportDefinition<ChangeCountRow> report = ReportDefinition.sql(
    PojoLensSql.parse("select changeType, count(*) as total group by changeType order by changeType asc"),
    ChangeCountRow.class,
    ChartSpec.of(ChartType.BAR, "changeType", "total"));

List<ChangeCountRow> rows = report.rows(comparison.rows());
ChartData chart = report.chart(comparison.rows());
```

## Summary Metrics

`comparison.summary()` includes:

- `currentCount()`
- `previousCount()`
- `addedCount()`
- `removedCount()`
- `changedCount()`
- `unchangedCount()`
- `netRowDelta()`

## Null And Duplicate Keys

Default behavior:

- null keys are rejected
- duplicate keys are rejected
- null rows are rejected

If null keys are intentional:

```java
SnapshotComparison<MyRow, String> comparison = SnapshotComparison
    .builder(currentSnapshot, previousSnapshot)
    .allowNullKeys()
    .byKey(row -> row.externalId);
```

## Comparison Semantics

- row matching is based only on the provided key selector
- change detection compares queryable POJO fields using deterministic field order
- nested POJO fields participate in change detection
- `@Exclude` fields do not participate
- if a row type exposes no queryable fields, comparison falls back to object equality


