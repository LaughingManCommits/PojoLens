# Chart Data Guide

Chart mapping is one of PojoLens's output-helper layers.
Start from a query or reusable report contract first, then add chart output
when the consumer actually needs it.

Output-helper route:
- [output-helpers.md](output-helpers.md)

## Current Contract

`PojoLens` chart support is data-contract mapping only (no internal renderer).

Supported chart types:
- `BAR`
- `LINE`
- `PIE`
- `AREA`
- `SCATTER`

Chart models:
- `ChartType`
- `ChartSpec`
- `ChartData`
- `ChartDataset`
- `ChartQueryPreset`
- `ChartQueryPresets`
- `ChartJsAdapter`

`ChartSpec` contract:
- required: `type`, `xField`, `yField`
- optional: `seriesField`, `title`, `xLabel`, `yLabel`, `dateFormat`, `stacked`, `percentStacked`, `nullPointPolicy`
- dataset metadata hints:
  - `withDatasetColorHint(datasetLabel, colorHint)`
  - `withDatasetStackGroupId(datasetLabel, stackGroupId)`
  - `withDatasetAxisId(datasetLabel, axisId)`

Typed chart spec helpers:
- `ChartSpec.of(type, Row::getX, Row::getY)`
- `ChartSpec.of(type, Row::getX, Row::getY, Row::getSeries)`

Value type contract:
- x-axis (categorical types — BAR, LINE, PIE, AREA): `String`, `Number`, `Date` (formatted to string)
- x-axis (SCATTER): numeric only — stored in `ChartDataset.xValues`; string labels are not used
- y-axis: numeric only

Null/empty behavior:
- empty input rows -> empty labels/datasets
- null row entries are skipped
- null x values are included as `null` labels
- null y values are rejected

Validation error contract:
- `Chart type is required`
- `xField is required`
- `yField is required`
- `Unknown chart field '<name>'`
- `Chart yField '<name>' must be numeric`
- `Chart yField '<name>' must not be null`
- `Chart seriesField must not be blank when provided`
- `Chart type PIE does not support seriesField`
- `percentStacked requires stacked=true`
- `stacked charts require seriesField`
- `stacked/percentStacked is supported only for BAR and AREA charts`

Interop policy:
- `PojoLens` does not ship chart rendering.
- integration with chart libraries is validated via tests/examples.

Reusable-contract choice:
- for docs and new code, treat `ReportDefinition<T>` as the default reusable
  contract
- `ChartQueryPreset<T>` is advanced chart-first convenience when a preset
  factory already fits the workflow
- `ReportDefinition<T>` is the general reusable wrapper when the same query may
  feed chart and non-chart consumers
- wrapper selection guide: [docs/reusable-wrappers.md](reusable-wrappers.md)

## Output-Helper Route

Recommended defaults:
- start from `SqlLikeQuery.chart(...)` or `NaturalQuery.chart(...)` when the
  query itself owns the chart-producing workflow
- start from `ReportDefinition.chart(...)` / `report.chartJs(...)` when the
  same reusable contract should serve rows, chart output, and schema
- start from `PojoLensSql.parse(...)` for SQL-like chart flows
- start from `PojoLensNatural.parse(...)` for guided non-SQL chart flows when the query text already carries `as <type> chart`
- use `PojoLensChart.toChartData(...)` when rows already exist and only chart mapping remains
- use `ChartQueryPresets...` only as advanced convenience when the preset
  family is itself the contract
- for multi-source SQL-like chart execution, start with `JoinBindings` and
  promote to `DatasetBundle` when the same snapshot is reused

- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `NaturalQuery.chart(List<?>, Class<T>)`
- `NaturalQuery.chart(List<?>, Class<T>, ChartSpec)`
- `ChartJsAdapter.toPayload(ChartData)`
- `ReportDefinition.chart(List<?>)`
- `ReportDefinition.chart(List<?>, JoinBindings)`
- `ReportDefinition.chart(DatasetBundle)`
- `ReportDefinition.chartJs(List<?>)`
- `ReportDefinition.chartJs(List<?>, JoinBindings)`
- `ReportDefinition.chartJs(DatasetBundle)`
- `SqlLikeQuery.chart(List<?>, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, JoinBindings, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(DatasetBundle, Class<T>, ChartSpec)`
- advanced chart preset helpers:
  `ChartQueryPresets.categoryCounts(...)`,
  `ChartQueryPresets.categoryTotals(...)`,
  `ChartQueryPresets.timeSeriesCounts(...)`,
  `ChartQueryPresets.timeSeriesTotals(...)`
- `TimeBucketPreset` for explicit timezone/week-start chart presets
- `ChartQueryPresets.groupedBreakdown(...)`
- `ChartQueryPreset.schema()`
- `ChartQueryPreset.mapChartSpec(...)`
- `ChartQueryPreset.chartJs(...)`
- `ChartQueryPreset.reportDefinition()`
- `ReportDefinition.mapChartSpec(...)`
- `ReportDefinition.chartJs(...)`

## Examples

SQL-like chart:

```java
ChartData chart = PojoLensSql
    .parse("select department, sum(salary) as payroll group by department order by payroll desc")
    .chart(source, DepartmentPayrollRow.class, ChartSpec.of(ChartType.BAR, "department", "payroll"));
```

Natural chart phrase with inferred mapping:

```java
ChartData chart = PojoLensNatural
    .parse("show department, count of employees as total "
        + "where active is true group by department sort by total descending as bar chart")
    .chart(source, DepartmentCount.class);
```

Reusable report-driven chart:

```java
ReportDefinition<DepartmentHeadcount> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as headcount group by department order by department asc"),
    DepartmentHeadcount.class,
    ChartSpec.of(ChartType.BAR, "department", "headcount"));

ChartJsPayload payload = report.chartJs(source);
```

Advanced preset-driven chart:

```java
ChartQueryPreset<DepartmentHeadcount> preset = ChartQueryPresets
    .categoryCounts("department", "headcount", DepartmentHeadcount.class);

List<DepartmentHeadcount> rows = preset.rows(source);
ChartData chart = preset.chart(source);
```

Advanced projection-free preset with built-in Chart.js payload:

```java
ChartJsPayload payload = ChartQueryPresets
    .categoryTotals("department", Metric.SUM, "salary", "payroll")
    .chartJs(source);
```

Advanced preset with a customized title/axis contract:

```java
ChartJsPayload payload = ChartQueryPresets
    .categoryTotals("department", Metric.SUM, "salary", "payroll")
    .mapChartSpec(spec -> spec
        .withTitle("Payroll by Department")
        .withAxisLabels("Department", "Payroll"))
    .chartJs(source);
```

Bundle-driven chart:

```java
DatasetBundle bundle = DatasetBundle.of(
    companies,
    JoinBindings.of("employees", employees));

ChartData chart = PojoLensSql
    .parse("select title, count(*) as total from companies left join employees on id = companyId group by title")
    .chart(bundle, CompanyTitleTotal.class, ChartSpec.of(ChartType.BAR, "title", "total"));
```

Available preset shapes:
- category totals / counts
- time-series totals / counts
- grouped breakdowns with a `seriesField`

Time-series preset:

```java
ChartQueryPreset<PeriodHeadcount> preset = ChartQueryPresets
    .timeSeriesCounts("hireDate", TimeBucket.MONTH, "period", "headcount", PeriodHeadcount.class);

List<PeriodHeadcount> rows = preset.rows(source);
ChartData chart = preset.chart(source);
```

Explicit calendar preset:

```java
ChartQueryPreset<PeriodHeadcount> preset = ChartQueryPresets
    .timeSeriesCounts(
        "hireDate",
        TimeBucketPreset.week()
            .withZone("Europe/Amsterdam")
            .withWeekStart("sunday"),
        "period",
        "headcount",
        PeriodHeadcount.class);
```

Grouped breakdown preset:

```java
ChartQueryPreset<DepartmentStatusCount> preset = ChartQueryPresets
    .groupedBreakdown("department", "active", Metric.COUNT, null, "headcount", DepartmentStatusCount.class);

ChartData chart = preset.chart(source);
```

Policy examples:

```java
ChartSpec stacked = ChartSpec.of(ChartType.BAR, "period", "payroll", "department")
    .withStacked(true);

ChartSpec percentStacked = ChartSpec.of(ChartType.AREA, "period", "payroll", "department")
    .withStacked(true)
    .withPercentStacked(true);

ChartSpec zeroFill = ChartSpec.of(ChartType.BAR, "period", "payroll", "department")
    .withNullPointPolicy(NullPointPolicy.ZERO);
```

## Scatter Charts

Scatter charts require both `xField` and `yField` to be numeric. The x values
are stored in `ChartDataset.xValues` (not in the categorical `labels` list).
`ChartJsAdapter` zips them into `[{x: ..., y: ...}]` point arrays and sets the
x-axis to `type: "linear"`.

```java
// Both x and y must be numeric fields
ChartData chart = PojoLensChart.toChartData(
    points,
    ChartSpec.of(ChartType.SCATTER, "x", "y")
        .withSortedLabels(true)          // sorts by x ascending
        .withAxisLabels("X Axis", "Y Axis"));

ChartJsPayload payload = ChartJsAdapter.toPayload(chart);
// payload.type() == "scatter"
// payload.data().datasets().get(0).data() == [{x: 1.0, y: 10.0}, ...]
```

Scatter constraints:
- `xField` value must be numeric; non-numeric x fields throw `IllegalArgumentException`
- stacking and percent-stacking are not supported on scatter
- multi-series scatter uses categorical label grouping (unchanged from line/bar behavior)

Natural chart inference contract:

- chart phrases set chart type only
- 2 `show` outputs infer `xField`, `yField`
- 3 `show` outputs infer `xField`, `seriesField`, `yField`
- `pie` charts require exactly 2 outputs
- explicit `ChartSpec` still overrides inference when you want a different mapping




