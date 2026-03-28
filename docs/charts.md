# Chart Data Guide

## Contract (v1)

`PojoLens` chart support is data-contract mapping only (no internal renderer).

Supported v1 chart types:
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
- x-axis: `String`, `Number`, `Date` (formatted to string)
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

Wrapper choice:
- for docs and new code, treat `ReportDefinition<T>` as the default reusable
  contract
- `ChartQueryPreset<T>` is the specialized chart-first reusable wrapper
- `ReportDefinition<T>` is the general reusable wrapper when the same query may feed chart and non-chart consumers
- wrapper selection guide: [docs/reusable-wrappers.md](reusable-wrappers.md)

## API Entry Points

Recommended defaults:
- start from `PojoLensCore.newQueryBuilder(...)` for fluent query-owned chart flows
- start from `PojoLensSql.parse(...)` for SQL-like chart flows
- use `PojoLensChart.toChartData(...)` when rows already exist and only chart mapping remains
- for multi-source SQL-like chart execution, start with `JoinBindings` and
  promote to `DatasetBundle` when the same snapshot is reused

- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `ChartJsAdapter.toPayload(ChartData)`
- `Filter.chart(Class<T>, ChartSpec)`
- `Filter.chart(Sort, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, JoinBindings, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, Map<String,List<?>>, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(DatasetBundle, Class<T>, ChartSpec)`
- `ChartQueryPresets.categoryCounts(...)`
- `ChartQueryPresets.categoryTotals(...)`
- `ChartQueryPresets.timeSeriesCounts(...)`
- `ChartQueryPresets.timeSeriesTotals(...)`
- `TimeBucketPreset` for explicit timezone/week-start chart presets
- `ChartQueryPresets.groupedBreakdown(...)`
- `ChartQueryPreset.schema()`
- `ChartQueryPreset.mapChartSpec(...)`
- `ChartQueryPreset.chartJs(...)`
- `ChartQueryPreset.reportDefinition()`
- `ReportDefinition.mapChartSpec(...)`
- `ReportDefinition.chartJs(...)`

## Examples

Fluent chart:

```java
ChartData chart = PojoLensCore.newQueryBuilder(employees)
    .addGroup("department")
    .addMetric("salary", Metric.SUM, "payroll")
    .addOrder("payroll")
    .initFilter()
    .chart(Sort.DESC, DepartmentPayrollRow.class, ChartSpec.of(ChartType.BAR, "department", "payroll"));
```

SQL-like chart:

```java
ChartData chart = PojoLensSql
    .parse("select department, count(*) as headcount group by department order by headcount desc")
    .chart(source, DepartmentHeadcount.class, ChartSpec.of(ChartType.BAR, "department", "headcount"));
```

Preset-driven chart:

```java
ChartQueryPreset<DepartmentHeadcount> preset = ChartQueryPresets
    .categoryCounts("department", "headcount", DepartmentHeadcount.class);

List<DepartmentHeadcount> rows = preset.rows(source);
ChartData chart = preset.chart(source);
```

Projection-free preset with built-in Chart.js payload:

```java
ChartJsPayload payload = ChartQueryPresets
    .categoryTotals("department", Metric.SUM, "salary", "payroll")
    .chartJs(source);
```

Preset with a customized title/axis contract:

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


