# Report Definitions

`ReportDefinition<T>` captures a reusable query + projection contract for
repeated execution against different in-memory dataset snapshots.

It also exposes deterministic table metadata through `schema()`.
It is the general reusable wrapper in PojoLens and the default reusable-query
contract for docs and new code.
SQL-like, natural, and typed queries are the public paths into it.
`ChartQueryPreset<T>` and `StatsViewPreset<T>` remain available as advanced
chart-first and table-first convenience wrappers that can bridge back to it.

Output-helper route:
- [output-helpers.md](output-helpers.md)

Wrapper selection guide:
- [docs/reusable-wrappers.md](reusable-wrappers.md)

Use it when the same report/query shape is executed across:

- multiple requests
- scheduled jobs over refreshed snapshots
- endpoints that need both rows and chart payloads
- flows that may start simple now but later need more than one consumer

## SQL-like Report Definition

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
TabularSchema schema = report.schema();
```

## Natural Report Definition

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
    PojoLensNatural.parse(
        "show department, count of employees as total "
            + "where active is true group by department sort by department ascending"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
```

If runtime vocabulary or computed fields should apply, parse the query through
`runtime.natural()` first and then wrap that `NaturalQuery`:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setNaturalVocabulary(NaturalVocabulary.builder()
    .field("department", "team")
    .build());

ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
    runtime.natural().parse(
        "show team as department, count of employees as total "
            + "where active is true group by team sort by department ascending"),
    DepartmentCount.class);
```

Natural report definitions support `JoinBindings` / `DatasetBundle` the same
way SQL-like report definitions do.

## Typed Report Definition

```java
TypedField<Employee, String> department = TypedField.of("department", String.class);
TypedField<Employee, Boolean> active = TypedField.of("active", Boolean.class);
TypedField<DepartmentCount, Long> total = TypedField.of("total", Long.class);

ReportDefinition<DepartmentCount> report = ReportDefinition.typed(
    TypedQuery.from(Employee.class)
        .where(active.eq(true))
        .groupBy(department)
        .count(total)
        .orderBy(department),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
TabularSchema schema = report.schema();
```

Typed report definitions derive a synthetic `source()` label such as
`typed:Employee`, while preserving the same reusable rows/chart/schema contract
as the SQL-like and natural factories.

For SQL-like definitions, `JoinBindings` is the default one-off multi-source
execution input:

```java
List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));
```

If the same snapshot is reused across multiple report calls, wrap the primary
rows plus `JoinBindings` once in `DatasetBundle`:

```java
DatasetBundle bundle = DatasetBundle.of(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = report.rows(bundle);
ChartData chart = report.chart(bundle);
```

If the underlying query depends on reusable derived fields, attach the registry at query/build time:

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build();

ReportDefinition<DepartmentAdjustedPayroll> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, sum(adjustedSalary) as totalAdjustedPayroll group by department")
        .computedFields(registry),
    DepartmentAdjustedPayroll.class);
```

## Optional Chart Mapping

Chart mapping is optional.

If the report definition was created without a `ChartSpec`, `chart(...)` will throw.
This stays true for natural report definitions even when the source natural
query text contains `as <type> chart`; reusable report contracts keep chart
mapping explicit.

You can attach one later:

```java
ReportDefinition<DepartmentCount> rowsOnly = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as total group by department"),
    DepartmentCount.class);

ReportDefinition<DepartmentCount> chartReady = rowsOnly.withChartSpec(
    ChartSpec.of(ChartType.BAR, "department", "total"));
```

---

## SavedReport — Versioned Saved-Report Contract

`SavedReport` carries query text, default parameters, optional chart spec, and
optional schema as plain serialization-friendly data — no lambdas or live
executors. It is designed to be stored, reviewed without executing, and replayed
on demand against live data snapshots.

Use it when a report must be:
- persisted to a database, file, or config store and replayed later
- reviewed by an admin before execution
- shared across services or processes as a versioned contract
- migrated safely when query text evolves

When the report is only executed in-process and reuse across requests is enough,
a plain `ReportDefinition<T>` is simpler.

### Create

```java
SavedReport report = SavedReport
    .sqlLike("active-by-dept", "Active employees by department",
             "select department, count(*) as total "
             + "where active = :active group by department order by department asc")
    .withDefaultParam("active", true)
    .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));
```

Natural query:

```java
SavedReport report = SavedReport
    .natural("active-by-dept-natural", "Active employees by department",
             "show department, count of employees as total "
             + "where active is true group by department sort by department ascending");
```

The query is parsed at creation time. Invalid query text throws immediately.

### Configure

All builder methods return a new `SavedReport` instance — the original is unchanged:

```java
SavedReport withParams = report.withDefaultParam("active", true);
SavedReport withChart  = report.withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));
SavedReport withSchema = report.withSchema(mySchema);
SavedReport withBulk   = report.withDefaultParams(Map.of("active", true, "dept", "Engineering"));
```

### Review Without Data

Both methods are safe to call without any row data:

```java
// structural query shape — fields, grouping, paging, joins, required params
SqlLikePlanPreview preview = report.planPreview();
List<String> requiredParams = preview.requiredParams();   // ["active"]
boolean hasGrouping         = preview.hasGrouping();      // true

// field references, lint warnings, output fields, required params
QueryDiagnostics diag = report.diagnostics();
boolean valid            = diag.valid();
List<String> required    = diag.requiredParams();
```

### Replay

```java
// Full replay — builds a live ReportDefinition with default params, chart spec, and schema applied
ReportDefinition<DeptRow> def = report.toDefinition(DeptRow.class);
List<DeptRow> rows = def.rows(employeeSnapshot);
ChartData chart   = def.chart(employeeSnapshot);

// Raw query replay — for callers that need the SqlLikeQuery directly
SqlLikeQuery query = report.toQuery();          // SQL_LIKE reports only
NaturalQuery nq    = report.toNaturalQuery();   // NATURAL reports only
```

### Versioned Contract Metadata

```java
String version = report.version();        // "1" — format version for deserialization checks
String id      = report.id();
String name    = report.name();
SavedReportKind kind = report.kind();     // SQL_LIKE or NATURAL
String source  = report.source();         // derived from query at creation
Map<String, Object> defaults = report.defaultParams();
ChartSpec spec = report.chartSpec();      // null if not set
TabularSchema schema = report.schema();   // null if not set
```

### Full Workflow Example

```java
// 1. Define once and store
SavedReport report = SavedReport
    .sqlLike("rpt-001", "High earners by department",
             "select department, count(*) as total "
             + "where active = :active and salary >= :minSalary "
             + "group by department order by total desc")
    .withDefaultParam("active", true)
    .withDefaultParam("minSalary", 100000)
    .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));

// 2. Admin review before running
SqlLikePlanPreview preview = report.planPreview();
// preview.requiredParams() → ["active", "minSalary"]

// 3. Replay against a live snapshot
ReportDefinition<DeptCountRow> def = report.toDefinition(DeptCountRow.class);
List<DeptCountRow> rows = def.rows(currentEmployees);
ChartData chart         = def.chart(currentEmployees);
```

## Advanced Chart Preset Convenience

`ChartQueryPreset<T>` remains available as lightweight advanced sugar for
chart-first SQL-like flows.
It is not the default reusable-contract story for new docs or new code.

If you want the more general report abstraction, convert it:

```java
ReportDefinition<DepartmentCount> report = preset.reportDefinition();
TabularSchema schema = preset.schema();
```

## Advanced Stats Preset Convenience

`StatsViewPreset<T>` remains available as table-first advanced sugar for common
summary/grouped/leaderboard query shapes.
Choose it when totals and `StatsTable<T>` are part of the contract.

It adds optional totals and schema metadata through `StatsTable<T>`:

```java
StatsTable<DepartmentCount> table = StatsViewPresets
    .by("department", DepartmentCount.class)
    .table(source);

List<DepartmentCount> rows = table.rows();
Map<String, Object> totals = table.totals();
TabularSchema schema = table.schema();
```

Converting a stats preset to `ReportDefinition<T>` keeps the row query, but not the totals payload:

```java
ReportDefinition<DepartmentCount> report = StatsViewPresets
    .by("department", DepartmentCount.class)
    .reportDefinition();
```

## Period Comparison

`ReportComparisons` computes current/previous metric deltas and wraps them in
`PeriodComparison` for formatted output.

Numeric pair (when aggregation is done outside):

```java
PeriodComparison c = ReportComparisons.of(currentCount, previousCount);
c.percentageDelta();   // "+10%", "-5%", "flat", "new"
c.absoluteDelta();     // numeric change
```

Row-based aggregation:

```java
// Compare SUM of a field across two filtered row lists
PeriodComparison c = ReportComparisons.compare(
    currentRows, previousRows, "amount", Metric.SUM);

// Compare row counts
PeriodComparison c = ReportComparisons.compareCount(currentRows, previousRows);
```

Rate/percentage field deltas (e.g. approval rates expressed as 0–1 fractions):

```java
PeriodComparison c = ReportComparisons.of(0.92, 0.87);
c.ratePointDelta();    // "+5.0 pt"
```

`percentageDelta()` special values:
- `"flat"` — both current and previous are zero
- `"new"` — previous is zero, current is non-zero


