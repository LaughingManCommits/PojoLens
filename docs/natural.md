# Natural Query Guide

## Canonical Grammar

- optional leading source clause: `from <source> [as <label>]`
- explicit joins: `join|left join|right join|inner join <source> [as <label>] on <lhs> equals <rhs>`
- `show` with explicit fields, aliases via `as`, aggregate phrases, time-bucket phrases, and deterministic window phrases
- `where`
- `group by`
- `having`
- `qualify`
- `sort by`
- `limit`
- `offset`
- terminal chart phrases: `as bar chart`, `as line chart`, `as area chart`, `as pie chart`, `as scatter chart`
- named parameters like `:minSalary`

Canonical operator phrases in `where`, `having`, and `qualify`:
- `is`
- `is not`
- `is at least` / `at least`
- `is at most` / `at most`
- `is more than` / `more than`
- `is less than` / `less than`
- `is above` / `above`
- `is below` / `below`
- `is before` / `before`
- `is after` / `after`
- `contains`
- `starts with`
- `ends with`

Canonical aggregate phrases:
- `count of`
- `sum of`
- `average of` / `avg`
- `minimum of` / `min`
- `maximum of` / `max`

Canonical time-bucket phrase:
- `bucket <date field> by day|week|month|quarter|year as <alias>`
- optional timezone: `bucket <date field> by month in Europe/Amsterdam as <alias>`
- optional week start for week buckets: `bucket <date field> by week in Europe/Amsterdam starting sunday as <alias>`

Canonical window phrases:
- `row number [by <field>] ordered by <field> [ascending|descending] [then <field> ...] as <alias>`
- `rank [by <field>] ordered by <field> [ascending|descending] [then <field> ...] as <alias>`
- `dense rank [by <field>] ordered by <field> [ascending|descending] [then <field> ...] as <alias>`
- `running count|sum|average|minimum|maximum of <field|employees> [by <field>] ordered by <field> [ascending|descending] [then <field> ...] as <alias>`

Join notes:
- source labels are explicit: `from companies as company join employees as employee ...`
- source-qualified field phrases use the source label or source name prefix: `company id`, `employee title`
- join conditions use `equals` and stay deterministic: `on company id equals employee company id`

Guide note:
- the main examples below prefer the shortest canonical phrasing
- a reader should be able to learn the surface from this section alone

## Accepted Aliases

These are supported, but they are secondary to the canonical grammar above.

Clause aliases:
- `grouped by`
- top-level `ordered by`
- `as a|an <type> chart`

Bounded filler words:
- `show me ...`
- leading `the`, `a`, or `an` before source/field references such as
  `show the employees`, `where the department is ...`, or
  `from the companies join the employees ...`
- bounded filter lead-ins after `show`: `who is|are`, `that is|are`, `which is|are`

Comparison aliases:
- `equals`
- `is equal to` / `equal to`
- `is not equal to` / `not equal to`
- `is greater than` / `greater than`
- `is greater than or equal to` / `greater than or equal to`
- `is less than or equal to` / `less than or equal to`

Operator inflection aliases:
- `containing`
- `starting with`
- `ending with`

Sort-direction aliases:
- `in ascending order`
- `in descending order`

Alias notes:
- aliases are optional sugar, not required syntax
- they normalize to the canonical grammar
- they are ignored only in bounded grammar slots, not stripped globally
- connector lead-ins lower to `where`; a bare field after them means boolean `true`, and `not <field>` means boolean `false`

## Non-goals

- free-form conversational language
- fuzzy guessing
- implicit business semantics such as `top performers` or `recent hires`
- direct frame control or inline window expressions in `qualify`

## Execution Model

Use `PojoLensNatural.parse(...)` for direct deterministic parsing:

```java
List<Employee> rows = PojoLensNatural
    .parse("show employees where department is :dept and salary is at least :minSalary "
        + "sort by salary descending limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

Use `PojoLensRuntime.natural()` when vocabulary, telemetry, lint mode, strict typing, caches, or computed fields should be runtime-scoped:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setNaturalVocabulary(NaturalVocabulary.builder()
    .field("salary", "annual pay", "pay")
    .field("department", "team")
    .build());

List<Employee> rows = runtime.natural()
    .parse("show employees where team is Engineering and active is true "
        + "sort by annual pay descending limit 10")
    .filter(source, Employee.class);
```

Use `PojoLensNatural.template(...)` or `runtime.natural().template(...)` when the same natural query should be rebound against a declared parameter schema:

```java
NaturalTemplate template = PojoLensNatural.template(
    "show employees where department is :dept and salary is at least :minSalary "
        + "sort by salary descending",
    "dept",
    "minSalary");

List<Employee> rows = template
    .bind(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

Use `ReportDefinition.natural(...)` when that parsed natural query should become
a reusable row/chart contract across multiple dataset snapshots:

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

If runtime vocabulary or computed fields should apply, parse through
`runtime.natural()` first and then wrap that `NaturalQuery` in
`ReportDefinition.natural(...)`.

Natural queries lower into the same shared engine used by fluent and SQL-like execution.

## Joins and Multi-source Queries

Joined natural queries stay explicit and use the same `JoinBindings` / `DatasetBundle` model as SQL-like execution.

Example joined query:

```java
DatasetBundle bundle = DatasetBundle.of(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = PojoLensNatural
    .parse("from companies as company join employees as employee "
        + "on company id equals employee company id "
        + "show company where employee title is Engineer")
    .filter(bundle, Company.class);
```

Join guidance:

- bind named secondary sources with `JoinBindings`
- promote repeated multi-source snapshots to `DatasetBundle`
- use source labels when a joined field needs to stay explicit
- unqualified joined fields are still allowed when the underlying joined field name is unique
- when the joined shape becomes deeply select-heavy or window-heavy, prefer [docs/sql-like.md](sql-like.md)

## Vocabulary Contract

`NaturalVocabulary` is runtime-scoped and explicit:

- exact field matches win before alias lookup
- alias resolution is deterministic
- ambiguous aliases fail with candidate fields
- unknown terms fail with the allowed-field set
- direct `PojoLensNatural.parse(...)` remains vocabulary-free; runtime-owned vocabulary applies through `PojoLensRuntime.natural()`

Registration shape:

```java
NaturalVocabulary vocabulary = NaturalVocabulary.builder()
    .field("salary", "annual pay", "pay")
    .field("hireDate", "hire date", "start date")
    .field("active", "active", "currently employed")
    .build();
```

## Templates and Computed Fields

Natural templates keep the same parameter-schema discipline as SQL-like templates:

- expected parameter names must exactly match the natural query placeholders
- missing or unknown bound parameters fail deterministically
- use `runtime.natural().template(...)` when runtime vocabulary or computed fields should apply

Computed-field phrasing is runtime-scoped and reuses the existing `ComputedFieldRegistry`.
Author the computed field by its registered name or the same spaced wording the normalizer would derive from it, such as `adjusted salary` -> `adjustedSalary`.

Example:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setComputedFieldRegistry(ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build());

List<AdjustedSalaryRow> rows = runtime.natural()
    .template(
        "show name, adjusted salary "
            + "where adjusted salary is at least :minSalary "
            + "sort by adjusted salary descending",
        "minSalary")
    .bind(Map.of("minSalary", 130000.0))
    .filter(source, AdjustedSalaryRow.class);
```

## Grouping and Time Buckets

Grouped natural queries use the same grouped validation discipline as SQL-like queries.

Example grouped aggregate:

```java
List<DepartmentHeadcountRow> rows = PojoLensNatural
    .parse("show department, count of employees as headcount "
        + "where active is true "
        + "group by department having headcount is at least 2 "
        + "sort by headcount descending")
    .filter(source, DepartmentHeadcountRow.class);
```

Example time bucket:

```java
List<PeriodPayrollRow> rows = PojoLensNatural
    .parse("show bucket hire date by month as period, sum of salary as payroll "
        + "group by period sort by period ascending")
    .filter(source, PeriodPayrollRow.class);
```

Time-bucket notes:

- the bucket source field must be a `java.util.Date`
- bucket outputs should use `as <alias>`
- grouped queries must include the bucket alias in `group by`
- timezone defaults to `UTC`
- week buckets default to `MONDAY`

## Windows and Qualify

Natural window phrasing stays narrow and deterministic.

Example top-per-department query:

```java
List<DepartmentTopRow> rows = PojoLensNatural
    .parse("show department as dept, name, salary, "
        + "row number by department ordered by salary descending then id ascending as rn "
        + "where active is true qualify rn is at most 1 sort by dept ascending")
    .filter(source, DepartmentTopRow.class);
```

Window notes:

- window outputs require `as <alias>`
- `qualify` currently filters on window output aliases, not inline window expressions
- running aggregate windows lower to `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`
- grouped queries and `qualify` stay separate; use [docs/sql-like.md](sql-like.md) when you need more exact analytic control

## Chart Phrase Contract

Natural chart phrases configure chart type only. Field mapping stays deterministic from the `show` outputs.

Default one-shot execution:

```java
ChartData chart = PojoLensNatural
    .parse("show department, count of employees as total "
        + "where active is true group by department sort by total descending as bar chart")
    .chart(source, DepartmentCount.class);
```

Alias-heavy example:

```java
List<Employee> rows = PojoLensNatural
    .parse("show me the employees who are active and the department containing ine "
        + "and the salary greater than or equal to 120000 "
        + "ordered by the salary in descending order")
    .filter(source, Employee.class);
```

Use bound execution when you want to bind rows plus projection once, then choose the terminal operation:

```java
ChartData chart = PojoLensNatural
    .parse("show department, count of employees as total "
        + "where active is true group by department sort by total descending as bar chart")
    .bindTyped(source, DepartmentCount.class)
    .chart();
```

Use explicit `ChartSpec` only when you want to override inferred field mapping:

```java
ChartData chart = PojoLensNatural
    .parse("show name, salary where active is true sort by salary descending limit 10")
    .chart(source, Employee.class, ChartSpec.of(ChartType.BAR, "name", "salary"));
```

Call-shape notes:

- `chart(source, Projection.class)` is the default one-shot path
- `bindTyped(...).chart()` follows the shared bound-query pattern and is useful when the same bound query may also call `filter()`, `iterator()`, or `stream()`
- `chart(..., ChartSpec)` is an explicit mapping override, not a different binding model

Inference rules:

- 2 outputs: first output -> `xField`, second output -> `yField`
- 3 outputs: first output -> `xField`, second output -> `seriesField`, third output -> `yField`
- `pie` charts require exactly 2 outputs
- wildcard `show employees` does not support inferred chart mapping

## Explain Output

Natural `explain()` adds plain-English-specific metadata on top of the shared explain payload:

- `equivalentSqlLike`
- `resolvedNaturalFields` when runtime vocabulary resolution is involved
- `resolvedEquivalentSqlLike` for execution-context explains
- shared `joinSourceBindings` when the query uses explicit joins
- `naturalChartType` / `resolvedNaturalChartSpec` when a chart phrase is present

Example:

```java
Map<String, Object> explain = PojoLensNatural
    .parse("show department, count of employees as total "
        + "where active is true group by department sort by total descending as bar chart")
    .explain(source, DepartmentCount.class);
```

## Current Limitations

- the language is controlled text, not free-form natural language
- window phrasing is intentionally narrow: one optional partition field, explicit `ordered by`, and a fixed running frame for aggregate windows
- `qualify` currently accepts window output aliases only, not inline window expressions
- direct `PojoLensNatural.parse(...)` does not apply runtime vocabulary
- direct `PojoLensNatural.template(...)` does not apply runtime vocabulary or runtime-scoped computed fields
- `schema(...)` remains structural and does not perform runtime vocabulary resolution
- inferred chart mapping requires explicit non-wildcard `show` outputs

## Error Reference

Parser errors use deterministic location text:
- `Natural query parse error: ... at line <n>, column <n>`

Common validation/runtime failures:
- unknown natural term:
  `Unknown natural field term '<term>' in natural query. Allowed fields: [...]`
- ambiguous natural term:
  `Ambiguous natural field term '<term>' in natural query. Candidates: [...]`
- missing join source binding:
  `Missing JOIN source binding for '<source>'`
- missing chart phrase for no-spec chart execution:
  `Natural query does not declare a chart phrase; add 'as <type> chart' or pass ChartSpec explicitly`
- invalid inferred chart shape:
  `Natural chart inference requires exactly 2 SHOW outputs (x,y) or 3 SHOW outputs (x,series,y)`

When a phrase becomes too advanced or too exact for the natural surface, prefer [docs/sql-like.md](sql-like.md) or fluent queries.
