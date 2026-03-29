# Query Snapshot Regression Fixtures

`PojoLens` regression fixtures provide a code-first way to lock query behavior against a named immutable dataset snapshot.

This is advanced testing tooling, not a required adoption step for basic query
usage.

Core types:
- `QuerySnapshotFixture`
- `QueryRegressionFixture<T>`

Use them when you want deterministic checks for:
- expected rows
- derived metrics
- optional `explain()` output
- optional SQL-like lint codes
- fluent vs SQL-like parity over the same snapshot

## Named Snapshot

```java
QuerySnapshotFixture snapshot = QuerySnapshotFixture.of(
    "employees-dev",
    employees);
```

With join sources:

```java
QuerySnapshotFixture snapshot = QuerySnapshotFixture.of(
    "companies-dev",
    companies,
    JoinBindings.of("employees", employees));
```

## SQL-like Regression Fixture

```java
QueryRegressionFixture<Employee> fixture = QueryRegressionFixture.sql(
    snapshot,
    PojoLensSql.parse("where active = true order by salary desc limit 2"),
    Employee.class);

fixture
    .assertRowCount(2)
    .assertOrderedRows(row -> row.name + ":" + row.salary,
        "Cara:130000",
        "Alice:120000")
    .assertExplainContains(Map.of("limit", 2));
```

## Report Regression Fixture

```java
QueryRegressionFixture<DepartmentCount> fixture = QueryRegressionFixture.report(
    snapshot,
    ReportDefinition.sql(
        PojoLensSql.parse("select department, count(*) as total group by department"),
        DepartmentCount.class));

fixture.assertUnorderedRows(row -> row.department + ":" + row.total,
    "Engineering:3",
    "Finance:1");
```

## Fixture-Backed Parity

```java
FluentSqlLikeParity.assertUnorderedEquals(
    snapshot,
    DepartmentCount.class,
    builder -> builder.addGroup("department").addCount("total"),
    PojoLensSql.parse("select department, count(*) as total group by department"),
    row -> row.department + ":" + row.total);
```

Notes:
- snapshots are immutable
- report/fluent fixtures do not expose `explain()` or lint assertions unless the underlying contract supports them
- SQL-like fixture parity carries SQL-like sort direction into fluent execution automatically


