# SQL-like Query Guide

## Supported Grammar (v1)

- `SELECT` (optional, supports `AS` aliases)
- chained `JOIN` clauses (`INNER`, `LEFT`, `RIGHT`) with deterministic `ON <lhs> = <rhs>` binding
- `WHERE`
- aggregate functions: `COUNT(*)`, `SUM(field)`, `AVG(field)`, `MIN(field)`, `MAX(field)`
- rank window functions: `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()` with `OVER (PARTITION BY ... ORDER BY ...)`
- aggregate window functions: `COUNT(field|*)`, `SUM(field)`, `AVG(field)`, `MIN(field)`, `MAX(field)` with `OVER (...)`
- `GROUP BY`
- `HAVING` (`AND`/`OR` predicates)
- `QUALIFY` (`AND`/`OR` predicates against window outputs)
- time bucket function: `bucket(dateField, 'day|week|month|quarter|year'[, 'Zone/Id'[, 'monday|...']]) as alias`
- `ORDER BY`
- `LIMIT`
- `OFFSET`

Current non-goals:
- full SQL-engine subqueries

Supported operators in `WHERE`:
- `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`
- `CONTAINS`
- `MATCHES`

## HAVING v1 Contract

`HAVING` is defined for grouped/aggregated SQL-like queries and is evaluated after aggregation.

Clause order:
- `SELECT ... FROM/implicit source ... WHERE ... GROUP BY ... HAVING ... ORDER BY ... LIMIT ... OFFSET`

Allowed references in `HAVING`:
- aggregate expressions: `COUNT(*)`, `SUM(field)`, `AVG(field)`, `MIN(field)`, `MAX(field)`
- aggregate aliases defined in `SELECT`
- grouped fields listed in `GROUP BY`

Disallowed references in `HAVING`:
- non-grouped, non-aggregated source fields
- unknown names
- ambiguous names

Boolean support in v1:
- `AND` is supported
- `OR` is supported

Validation errors for invalid `HAVING`:
- `HAVING requires GROUP BY or aggregate SELECT output`
- `Unknown HAVING reference '<name>'`
- `Invalid HAVING reference '<name>': expected grouped field or aggregate output`

## QUALIFY v1 Contract

`QUALIFY` is defined for non-aggregate SQL-like queries and is evaluated after window computation.

Clause order:
- `SELECT ... FROM/implicit source ... WHERE ... QUALIFY ... ORDER BY ... LIMIT ... OFFSET`

Allowed references in `QUALIFY`:
- window aliases defined in `SELECT`
- direct rank-window expressions that match a selected window expression

Disallowed references in `QUALIFY`:
- non-window source fields
- unknown names
- subqueries
- grouped/aggregate query shapes

Validation errors for invalid `QUALIFY`:
- `QUALIFY requires at least one window SELECT output`
- `QUALIFY is only supported for non-aggregate SQL-like queries`
- `Unknown field '<name>' in QUALIFY clause`

## Window Functions v1 Contract

Window functions are supported for non-aggregate SQL-like query shapes and execute after `WHERE` and before `QUALIFY`.

Rank windows:
- `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`
- require `OVER(... ORDER BY ...)`

Aggregate windows:
- `COUNT(field)`, `COUNT(*)`, `SUM(field)`, `AVG(field)`, `MIN(field)`, `MAX(field)`
- `SUM/AVG/MIN/MAX` require numeric value fields
- currently support one frame mode only:
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`

Unsupported window frame expressions fail fast with actionable parser errors.

## Execution Model

For new SQL-like code, use `PojoLensSql.parse(...)` and `PojoLensSql.template(...)`.
The duplicate `PojoLens.parse(...)` facade was removed in the pre-adoption
simplification.

`PojoLensSql.parse(...)` produces a SQL-like query contract that:
- parses and validates query text
- binds into the fluent pipeline
- executes against in-memory rows

Bind-first typed execution:
- `bindTyped(rows, Projection.class).filter()` captures projection type once at bind-time
- `ORDER BY` direction is applied automatically during typed execution
- join queries use `bindTyped(rows, Projection.class, joinSources).filter()`
- bundle-backed joins use `bindTyped(datasetBundle, Projection.class).filter()`
- reusable computed fields attach via `.computedFields(registry)`
- chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the source explicitly

Strict validation rules:
- unknown fields are rejected
- `@Exclude` fields are not queryable
- select output names must be unique (including alias collisions)
- join source binding is required for SQL-like joins
- HAVING references must resolve to grouped fields or aggregate outputs
- QUALIFY references must resolve to selected window outputs
- named parameters must be fully bound before execution
- strict parameter typing can be enabled per query or runtime when early type mismatch failures are preferred

Guardrails:
- max query length: `4000`
- max tokens: `512`
- max predicates: `100`
- max order fields: `20`
- max select fields: `100`

Sort limitation:
- `ORDER BY` must use one global direction (all `ASC` or all `DESC`)

## Current Limitations

- SQL-like subqueries currently support only `WHERE <field> IN (select <field> ...)`.
- Subqueries do not support nested joins or aggregate/grouped subquery plans yet.
- SQL-like aggregate queries require explicit `SELECT` fields.
- SQL-like aggregate `ORDER BY` must reference a group-by field or aggregate output alias/name.
- Window functions currently support rank windows and aggregate windows, but only for non-aggregate query shapes.
- Aggregate windows currently support only `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
- Window functions currently run in non-aggregate queries (no `GROUP BY`/aggregate metrics in the same query).
- `QUALIFY` requires at least one selected window output and currently applies only to non-aggregate query shapes.
- Time bucket input fields must be `java.util.Date` values.
- Time bucket defaults are `UTC` + ISO-week (`MONDAY`) unless explicit SQL-like bucket arguments override them.
- `weekStart` is supported only for `bucket(..., 'week', ...)`.

## Recipe Catalog

All recipes below are executable and covered by docs tests (`SqlLikeDocsExamplesTest`).

### Recipe: Parameterized Filters

```java
List<Employee> rows = PojoLensSql
    .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
    .params(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
    .filter(source, Employee.class);
```

### Recipe: Offset Pagination

```java
List<Employee> rows = PojoLensSql
    .parse("where active = true order by salary desc limit 20 offset 40")
    .filter(source, Employee.class);
```

Use `ORDER BY` with `LIMIT/OFFSET` for deterministic page windows.

### Recipe: Parameterized Offset Pagination

```java
List<Employee> rows = PojoLensSql
    .parse("where active = true order by salary desc limit :limit offset :offset")
    .params(Map.of("limit", 20, "offset", 40))
    .filter(source, Employee.class);
```

### Recipe: Keyset/Cursor Pagination Pattern

```java
List<Employee> rows = PojoLensSql
    .parse("where active = true and ((salary < :lastSalary) or (salary = :lastSalary and id < :lastId)) "
        + "order by salary desc, id desc limit 20")
    .params(Map.of("lastSalary", 120000, "lastId", 1))
    .filter(source, Employee.class);
```

Keyset guidance:
- include all sort fields in the cursor
- include a deterministic tie-breaker field (for example `id`)
- keep `ORDER BY` direction and comparison operators aligned with cursor direction

### Recipe: Window Ranking (`ROW_NUMBER`)

```java
List<DepartmentSalaryRank> rows = PojoLensSql
    .parse("select department as dept, name, salary, "
        + "row_number() over (partition by department order by salary desc) as rn "
        + "where active = true order by dept asc, rn asc")
    .filter(source, DepartmentSalaryRank.class);
```

Window notes:
- include `ORDER BY` inside every `OVER(...)` clause to keep ranking deterministic
- non-unique window sort values are stabilized by original source row order
- query-level `ORDER BY` can reference window aliases (for example `order by rn asc`)

### Recipe: Dense Rank Per Group (`DENSE_RANK`)

```java
List<DepartmentDenseRank> rows = PojoLensSql
    .parse("select department as dept, name, salary, "
        + "dense_rank() over (partition by department order by salary desc) as dr "
        + "where active = true order by dept asc, dr asc, name asc")
    .filter(source, DepartmentDenseRank.class);
```

### Recipe: Top N Per Group with `QUALIFY`

```java
List<DepartmentSalaryRank> rows = PojoLensSql
    .parse("select department as dept, name, salary, "
        + "row_number() over (partition by department order by salary desc) as rn "
        + "where active = true qualify rn <= 1 order by dept asc")
    .filter(source, DepartmentSalaryRank.class);
```

### Recipe: Running Total (`SUM(...) OVER (...)`)

```java
List<DepartmentRunningTotal> rows = PojoLensSql
    .parse("select department as dept, name, salary, "
        + "sum(salary) over (partition by department order by salary desc "
        + "rows between unbounded preceding and current row) as runningTotal "
        + "where active = true order by dept asc, runningTotal asc")
    .filter(source, DepartmentRunningTotal.class);
```

### Recipe: First-Class Keyset Cursor API

```java
SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder()
    .put("salary", 120000)
    .put("id", 1)
    .build();

List<Employee> rows = PojoLensSql
    .parse("where active = true order by salary desc, id desc limit 20")
    .keysetAfter(cursor)
    .filter(source, Employee.class);
```

Tokenized cursor flow:

```java
String token = cursor.toToken();
SqlLikeCursor decoded = PojoLens.parseKeysetCursor(token);
```

Cursor contract:
- cursor field names must match query `ORDER BY` fields exactly
- cursor values must be non-null
- `keysetAfter(...)` resolves the "next page" window
- `keysetBefore(...)` resolves the "previous page" window
- token format is opaque Base64URL (`v1`) and preserves common scalar value types

### Recipe: Typed SQL Parameters (`SqlParams`)

```java
List<Employee> rows = PojoLensSql
    .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
    .params(SqlParams.builder()
        .put("dept", "Engineering")
        .put("minSalary", 120000)
        .put("active", true)
        .build())
    .filter(source, Employee.class);
```

### Recipe: Strict Parameter Typing

Per query:

```java
List<Employee> rows = PojoLensSql
    .parse("where salary >= :minSalary and active = :active order by salary desc")
    .strictParameterTypes()
    .params(Map.of("minSalary", 120000, "active", true))
    .filter(source, Employee.class);
```

Per runtime:

```java
PojoLensRuntime runtime = PojoLens.newRuntime();
runtime.setStrictParameterTypes(true);

List<Employee> rows = runtime
    .parse("where salary >= :minSalary and active = :active order by salary desc")
    .params(Map.of("minSalary", 120000, "active", true))
    .filter(source, Employee.class);
```

### Recipe: Streaming Execution Output

```java
try (Stream<Employee> rows = PojoLensSql
        .parse("where active = true limit 200")
        .stream(source, Employee.class)) {
    List<String> names = rows.map(r -> r.name).toList();
}
```

Streaming notes:
- simple non-joined/non-aggregate SQL-like queries can stream rows lazily
- complex query shapes (join/group/having/qualify/ordered windows) fall back to list-backed streams
- `bindTyped(...).stream()` is available for bind-first SQL-like flows

### Recipe: Lint Mode

Per query:

```java
SqlLikeQuery query = PojoLensSql
    .parse("select * from companies where title = 'Engineer' limit 5")
    .lintMode();

List<SqlLikeLintWarning> warnings = query.lintWarnings();
Map<String, Object> explain = query.explain();
```

Suppress a known warning code when the pattern is intentional:

```java
SqlLikeQuery query = PojoLensSql
    .parse("select * from companies limit 5")
    .lintMode()
    .suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD);
```

Per runtime:

```java
PojoLensRuntime runtime = PojoLens.newRuntime();
runtime.setLintMode(true);

SqlLikeQuery query = runtime.parse("select * from companies limit 5");
```

### Recipe: Runtime Policy Presets

Use presets when you want a preconfigured runtime and still keep manual overrides available afterward.

```java
PojoLensRuntime devRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
PojoLensRuntime prodRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);
PojoLensRuntime testRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.TEST);
```

Preset intent:

- `DEV`: caches enabled, cache stats enabled, strict parameter typing enabled, lint mode enabled
- `PROD`: caches enabled, cache stats disabled, strict parameter typing disabled, lint mode disabled
- `TEST`: caches disabled, strict parameter typing enabled, lint mode enabled

Manual overrides still apply after preset selection:

```java
PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);
runtime.setLintMode(true);
runtime.setStrictParameterTypes(true);
```

### Recipe: Runtime API Introspection and Preset Re-Application

If you need to inspect or re-apply runtime configuration programmatically:

```java
PojoLensRuntime runtime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);

boolean strict = runtime.isStrictParameterTypes();
boolean lint = runtime.isLintMode();
QueryTelemetryListener listener = runtime.getTelemetryListener();
ComputedFieldRegistry registry = runtime.getComputedFieldRegistry();

runtime.applyPreset(PojoLensRuntimePreset.PROD); // reapplies preset and resets caches/stats
```

Equivalent preset creation through the facade is also available:

```java
PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
```

### Recipe: WHERE IN Subquery

Self-source subquery:

```java
List<Employee> rows = PojoLensSql
    .parse("where department in (select department where active = true)")
    .filter(source, Employee.class);
```

Named source subquery using runtime join-source bindings:

```java
List<Company> rows = PojoLensSql
    .parse("where id in (select companyId from employees where title = 'Engineer')")
    .filter(companies, Map.of("employees", employees), Company.class);
```

Current subquery scope:

- only `WHERE ... IN (select oneField ...)`
- subquery `SELECT` must contain exactly one simple field
- subquery `FROM <source>` must resolve from provided join-source bindings
- aggregate, grouped, and join subqueries are not supported yet

### Recipe: Query Template with Parameter Schema

```java
SqlLikeTemplate template = PojoLensSql.template(
    "where department = :dept and salary >= :minSalary and active = :active order by salary desc",
    "dept", "minSalary", "active");

List<Employee> engineeringRows = template
    .bind(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
    .filter(source, Employee.class);

List<Employee> financeRows = template
    .bind(SqlParams.builder()
        .put("dept", "Finance")
        .put("minSalary", 80000)
        .put("active", true)
        .build())
    .filter(source, Employee.class);
```

### Recipe: Alias Projections

```java
List<EmployeeSummary> rows = PojoLensSql
    .parse("select name as employeeName, salary as annualSalary where salary >= 100000 order by salary asc")
    .filter(source, EmployeeSummary.class);
```

### Recipe: Computed Field Registry

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build();

List<AdjustedSalaryRow> rows = PojoLensSql
    .parse("select name, adjustedSalary where adjustedSalary >= 120000 order by adjustedSalary desc")
    .computedFields(registry)
    .filter(source, AdjustedSalaryRow.class);
```

### Recipe: Typed Bind-First Execution

```java
SqlLikeQuery query = PojoLensSql.parse("where salary >= :minSalary order by salary asc")
    .params(Map.of("minSalary", 90000));

List<Employee> rows = query
    .bindTyped(source, Employee.class)
    .filter();
```

### Recipe: Fluent vs SQL-like Parity Assertions

Use `FluentSqlLikeParity` in migration tests when you want to compare a fluent query and its SQL-like equivalent with either exact-order or order-agnostic assertions.

```java
List<DepartmentHeadcount> fluentRows = PojoLensCore.newQueryBuilder(source)
    .addGroup("department")
    .addCount("headcount")
    .initFilter()
    .filter(DepartmentHeadcount.class);

List<DepartmentHeadcount> sqlLikeRows = PojoLensSql
    .parse("select department, count(*) as headcount group by department")
    .filter(source, DepartmentHeadcount.class);

FluentSqlLikeParity.assertUnorderedEquals(
    fluentRows,
    sqlLikeRows,
    row -> row.department + ":" + row.headcount);
```

Fixture-backed parity uses the same named immutable snapshot for both executions:

```java
QuerySnapshotFixture snapshot = QuerySnapshotFixture.of("employees-dev", source);

FluentSqlLikeParity.assertUnorderedEquals(
    snapshot,
    DepartmentHeadcount.class,
    builder -> builder.addGroup("department").addCount("headcount"),
    PojoLensSql.parse("select department, count(*) as headcount group by department"),
    row -> row.department + ":" + row.headcount);
```

### Recipe: SQL-like Joins

Runtime map bindings:

```java
Map<String, List<?>> joinSources = new HashMap<>();
joinSources.put("employees", employees);

List<Company> rows = PojoLensSql
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(companies, joinSources, Company.class);
```

Multi-join example:

```java
Map<String, List<?>> joinSources = new HashMap<>();
joinSources.put("employees", employees);
joinSources.put("badges", badges);

List<Company> rows = PojoLensSql
    .parse("select * from companies "
            + "left join employees on companies.id = employees.companyId "
            + "left join badges on employees.id = badges.employeeId "
            + "where code = 'A1'")
    .filter(companies, joinSources, Company.class);
```

Notes:
- each new `JOIN` can reference fields already present in the current plan
- qualifying previous-source fields like `employees.id` is supported for later joins
- deterministic merged-field names still follow the fluent join collision rules (`child_...`)

Typed bindings:

```java
JoinBindings joinBindings = JoinBindings.builder()
    .add("employees", employees)
    .build();

List<Company> rows = PojoLensSql
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(companies, joinBindings, Company.class);
```

Dataset bundle execution:

```java
DatasetBundle bundle = PojoLens.bundle(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = PojoLensSql
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(bundle, Company.class);
```

### Recipe: HAVING Queries

```java
List<DepartmentHeadcount> rows = PojoLensSql
    .parse("select department, count(*) as headcount group by department having headcount >= 2 order by headcount desc")
    .filter(source, DepartmentHeadcount.class);
```

Grouped aliases and aggregate-expression ordering are also supported:

```java
List<DepartmentHeadcountByAlias> rows = PojoLensSql
    .parse("select department as dept, count(*) as headcount "
            + "group by dept having dept = 'Engineering' "
            + "order by sum(salary) desc")
    .filter(source, DepartmentHeadcountByAlias.class);
```

### Recipe: Chart Payload Mapping

```java
ChartData chart = PojoLensSql
    .parse("select department, count(*) as headcount group by department order by headcount desc")
    .chart(source, DepartmentHeadcount.class, ChartSpec.of(ChartType.BAR, "department", "headcount"));
```

### Recipe: Explicit Calendar Time Bucket

```java
List<WeeklyPayroll> rows = PojoLensSql
    .parse("select bucket(hireDate,'week','Europe/Amsterdam','sunday') as period, sum(salary) as payroll group by period")
    .filter(source, WeeklyPayroll.class);
```

### Recipe: Execution Explain with Stage Row Counts

```java
Map<String, Object> explain = PojoLensSql
    .parse("where active = true order by salary desc limit 2")
    .explain(source, Employee.class);

// explain.get("normalizedQuery"): "where active = true order by salary desc limit 2"
// explain.get("resolvedSortDirection"): "DESC"
// explain.get("projectionMode"): "direct"
// explain.get("joinSourceBindings"): {}
// explain.get("parameterSnapshot"): {}
// explain.get("lintWarnings"): only present when lint mode is enabled
// explain.get("stageRowCounts"):
// where={applied=true, before=4, after=3}
// group={applied=false, before=3, after=3}
// having={applied=false, before=3, after=3}
// qualify={applied=false, before=3, after=3}
// order={applied=true, before=3, after=3}
// limit={applied=true, before=3, after=2}
```

For parameterized queries, `parameterSnapshot` reports parameter names with redacted metadata such as `status`, `shape`, `type`, and collection `size` when applicable. For JOIN queries, `joinSourceBindings` reports whether each named JOIN source is currently bound.

Lint mode adds deterministic non-blocking warnings to `explain()` under `lintWarnings`. Current warning codes:

- `EQ-SQL-LINT-001`: broad `select *`
- `EQ-SQL-LINT-002`: `limit`/`offset` without `order by`
- `EQ-SQL-LINT-003`: inline string literal filter values; prefer named parameters

### Anti-Patterns

Avoid these patterns when writing SQL-like integrations:
- string concatenation for dynamic values; use named parameters instead
- bind-first SQL-like calls that repeat sort and projection class; use `bindTyped(...)`
- ad-hoc join maps for long-lived code paths; prefer `JoinBindings`
- rebuilding the same multi-source snapshot repeatedly; prefer `DatasetBundle`
- deep `offset` pagination on high-cardinality feeds when cursor semantics are available

## SQL-like Error Reference

Parse errors include deterministic location text:
- `... at line <n>, column <n> (index <n>)`
- messages now start with a stable code and end with a troubleshooting link:
  - `EQ-SQL-...: <message> Troubleshooting: docs/sql-like.md#error-code-eq-sql-...`

### Code Summary

| Code | Meaning | Typical fix |
| --- | --- | --- |
| `EQ-SQL-API-001` | Query input was `null`. | Pass a non-null SQL-like string to `PojoLensSql.parse(...)`. |
| `EQ-SQL-API-002` | Query input was blank after trimming. | Pass non-empty SQL-like text. |
| `EQ-SQL-PAR-001` | General SQL-like parse/syntax failure. | Fix the clause/token called out by line, column, and snippet. |
| `EQ-SQL-PAR-002` | Query length exceeded the parser limit. | Shorten the query or split logic across reusable templates. |
| `EQ-SQL-PAR-003` | Token count exceeded the parser limit. | Reduce query size or expression complexity. |
| `EQ-SQL-PAR-004` | Clause-specific item/predicate limit exceeded. | Reduce `SELECT`, `GROUP BY`, `ORDER BY`, `WHERE`, or `HAVING` breadth. |
| `EQ-SQL-VAL-001` | Unknown field/reference during validation. | Fix the field name or use the suggestion in the message. |
| `EQ-SQL-VAL-002` | Duplicate `SELECT` output name. | Rename aliases so each projected column is unique. |
| `EQ-SQL-VAL-003` | Missing JOIN source binding. | Provide the JOIN rows in `joinSources` or `JoinBindings`. |
| `EQ-SQL-VAL-004` | JOIN source rows were empty/invalid for validation. | Bind a non-empty list containing at least one non-null row. |
| `EQ-SQL-VAL-005` | Invalid, ambiguous, or unsupported `HAVING` reference. | Restrict `HAVING` to grouped fields and aggregate outputs. |
| `EQ-SQL-VAL-006` | Aggregate or `GROUP BY` semantics are invalid. | Add required aggregates/groups or remove unsupported combinations. |
| `EQ-SQL-VAL-007` | Computed `SELECT` projection is invalid. | Use computed expressions only in non-aggregate queries and add `AS`. |
| `EQ-SQL-VAL-008` | Time-bucket validation failed. | Use a `Date` field, give it an alias, and include the alias in `GROUP BY`. |
| `EQ-SQL-VAL-009` | Expression reference/operator validation failed. | Use valid numeric expressions and supported comparison operators. |
| `EQ-SQL-VAL-010` | Subquery shape/source is unsupported. | Restrict subqueries to `WHERE field IN (select oneField ...)` and bind named `FROM` sources. |
| `EQ-SQL-VAL-011` | Field reference is ambiguous in a multi-join context. | Qualify the field with `<source>.<field>` or use the deterministic merged field name. |
| `EQ-SQL-PRM-001` | Required named parameter is missing. | Supply all referenced parameters. |
| `EQ-SQL-PRM-002` | Unknown named parameter was provided. | Remove unexpected parameter names or update the query/template. |
| `EQ-SQL-PRM-003` | Query execution started with unresolved parameters. | Call `params(...)` before `filter()`, `bindTyped()`, or `chart()`. |
| `EQ-SQL-PRM-004` | Parameter name was blank/invalid. | Use non-blank parameter names in maps and `SqlParams`. |
| `EQ-SQL-PRM-005` | A bound parameter type/value is invalid for its usage. | For filters/HAVING, pass a value compatible with the referenced field type (or disable strict mode). For pagination params, pass non-negative integer values. |
| `EQ-SQL-CUR-001` | Keyset cursor paging was requested without `ORDER BY`. | Add deterministic `ORDER BY` fields before applying keyset cursor paging. |
| `EQ-SQL-CUR-002` | Cursor fields do not match `ORDER BY` fields. | Provide cursor values for every `ORDER BY` field using matching names. |
| `EQ-SQL-CUR-003` | Cursor token is invalid/malformed. | Use tokens generated by `SqlLikeCursor.toToken()` and pass them unchanged. |
| `EQ-SQL-CUR-004` | Cursor value is invalid for keyset paging. | Use non-null cursor values and supported tokenizable scalar types. |
| `EQ-SQL-BIND-001` | `ORDER BY` directions are mixed. | Use all `ASC` or all `DESC`. |
| `EQ-SQL-BIND-002` | Boolean expression exploded during normalization. | Simplify nested `AND`/`OR` logic. |
| `EQ-SQL-JOIN-001` | Duplicate typed JOIN binding name. | Register each JOIN source once. |
| `EQ-SQL-JOIN-002` | Typed JOIN binding name was blank. | Use a non-blank JOIN source name. |
| `EQ-SQL-RUN-001` | Aliased/computed projection failed at runtime. | Ensure projection fields exist and accept the projected values. |
| `EQ-SQL-RUN-002` | Runtime expression identifier resolution failed. | Verify computed expressions reference valid source fields. |

### Error Code EQ-SQL-API-001

Meaning:
- SQL-like query text was `null`.

Fix:
- Pass a non-null string to `PojoLensSql.parse(...)` or `PojoLensSql.template(...)`.

### Error Code EQ-SQL-API-002

Meaning:
- SQL-like query text was blank after trimming.

Fix:
- Pass non-empty SQL-like text.

### Error Code EQ-SQL-PAR-001

Meaning:
- Generic parser failure for malformed SQL-like syntax.

Fix:
- Use the clause, line, column, and caret snippet in the message to correct the query.

### Error Code EQ-SQL-PAR-002

Meaning:
- Query text exceeded the maximum supported length.

Fix:
- Shorten the query or split it into reusable templates/steps.

### Error Code EQ-SQL-PAR-003

Meaning:
- Tokenization exceeded the supported token budget.

Fix:
- Reduce query size or expression complexity.

### Error Code EQ-SQL-PAR-004

Meaning:
- A clause exceeded one of the parser guardrails such as max predicates or max selected/order/grouped fields.

Fix:
- Reduce `WHERE`/`HAVING` predicate count or trim `SELECT`, `GROUP BY`, or `ORDER BY` breadth.

### Error Code EQ-SQL-VAL-001

Meaning:
- A field/reference does not exist in the allowed validation scope.

Fix:
- Correct the name, use the deterministic suggestion hint if present, or expose the field in the projection.

### Error Code EQ-SQL-VAL-002

Meaning:
- Two `SELECT` outputs resolved to the same name.

Fix:
- Rename aliases so projected output names are unique.

### Error Code EQ-SQL-VAL-003

Meaning:
- A query references a JOIN source that was not bound at execution time.

Fix:
- Supply the JOIN rows via `Map<String, List<?>>` or `JoinBindings`.

### Error Code EQ-SQL-VAL-004

Meaning:
- JOIN source rows could not be validated because the bound list was empty or all-null.

Fix:
- Provide at least one non-null row so field metadata can be inferred.

### Error Code EQ-SQL-VAL-005

Meaning:
- `HAVING` references are invalid for the grouped/aggregated query shape.

Fix:
- Restrict `HAVING` to grouped fields, aggregate aliases, or aggregate expressions.

### Error Code EQ-SQL-VAL-006

Meaning:
- Aggregate query semantics are inconsistent, such as missing aggregates or non-grouped selected fields.

Fix:
- Ensure grouped queries include aggregates and that non-aggregated selected fields also appear in `GROUP BY`.

### Error Code EQ-SQL-VAL-007

Meaning:
- A computed `SELECT` expression is used in an unsupported context.

Fix:
- Use computed projections only for non-aggregate queries and always provide `AS <alias>`.

### Error Code EQ-SQL-VAL-008

Meaning:
- Time-bucket configuration is invalid.

Fix:
- Bucket only `Date` fields, alias the bucket output, and group by that alias.

### Error Code EQ-SQL-VAL-009

Meaning:
- Expression identifiers/operators failed validation.

Fix:
- Use supported numeric expressions and supported comparison operators.

### Error Code EQ-SQL-PRM-001

Meaning:
- Required SQL-like parameter(s) were missing.

Fix:
- Supply all named parameters used by the query.

### Error Code EQ-SQL-PRM-002

Meaning:
- Extra parameters were supplied that the query does not reference.

Fix:
- Remove unknown parameter names or update the query/template schema.

### Error Code EQ-SQL-PRM-003

Meaning:
- Execution started before named parameters were fully resolved.

Fix:
- Call `params(...)` before running `filter()`, `bindTyped()`, `chart()`, or `explain(...)`.

### Error Code EQ-SQL-PRM-004

Meaning:
- A parameter name was blank or invalid.

Fix:
- Use non-blank parameter names in `Map` inputs and `SqlParams`.

### Error Code EQ-SQL-PRM-005

Meaning:
- A bound parameter value is invalid for its usage:
- strict parameter typing detected a mismatch with a referenced field/output type, or
- pagination parameter values for `LIMIT`/`OFFSET` were not non-negative integers.

Fix:
- Pass a value compatible with the referenced field type, or leave strict parameter typing disabled if coercion-on-compare is acceptable.
- For `LIMIT`/`OFFSET` parameters, pass non-negative integer values (for example `20`, `0`, `100L`).

### Error Code EQ-SQL-CUR-001

Meaning:
- A keyset cursor method was called on a query without `ORDER BY`.

Fix:
- Add deterministic `ORDER BY` fields and then call `keysetAfter(...)` or `keysetBefore(...)`.

### Error Code EQ-SQL-CUR-002

Meaning:
- Cursor values do not match the query `ORDER BY` fields.

Fix:
- Provide one non-null cursor value for each ordered field, using the same field names.

### Error Code EQ-SQL-CUR-003

Meaning:
- Cursor token decoding failed due to invalid format/version/value encoding.

Fix:
- Use tokens produced by `SqlLikeCursor.toToken()` and keep them unmodified in transport/storage.

### Error Code EQ-SQL-CUR-004

Meaning:
- Cursor values were empty, null, or unsupported for tokenization.

Fix:
- Build cursors with non-null values and supported scalar types (for example `String`, `Boolean`, numeric types, `Date`).

### Error Code EQ-SQL-BIND-001

Meaning:
- `ORDER BY` uses mixed sort directions.

Fix:
- Use all `ASC` or all `DESC` in the same SQL-like query.

### Error Code EQ-SQL-BIND-002

Meaning:
- Boolean expression normalization produced too many groups.

Fix:
- Simplify nested `AND`/`OR` logic or break the query apart.

### Error Code EQ-SQL-JOIN-001

Meaning:
- The same typed JOIN source name was registered more than once.

Fix:
- Add each JOIN source binding only once.

### Error Code EQ-SQL-JOIN-002

Meaning:
- A typed JOIN source name was blank.

Fix:
- Use a non-blank source name when building `JoinBindings`.

### Error Code EQ-SQL-RUN-001

Meaning:
- Runtime projection of aliased/computed rows failed.

Fix:
- Ensure the projection class contains matching writable fields with compatible types.

### Error Code EQ-SQL-RUN-002

Meaning:
- Runtime evaluation of a computed expression could not resolve one of its identifiers.

Fix:
- Ensure computed expressions reference valid fields from the source rows.

