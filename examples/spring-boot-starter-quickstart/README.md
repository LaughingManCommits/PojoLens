# Spring Boot Starter Quickstart Example

This is the smallest runnable example showing PojoLens SQL-like queries on in-memory POJOs.
Use it to understand how the starter auto-configures `PojoLensRuntime` and execute queries.

**What you'll see:**
- Four query patterns: filtering (salary), grouping (department aggregation), ranges, and exact matches
- Typed result objects mapped directly from queries
- Parameter normalization (clamping, defaulting) in action
- Optional virtual-thread mode to test the web boundary

**What this is NOT:**
- A full analytics dashboard (see `examples/spring-boot-starter-risk-console` for that)
- A production setup with JDBC, auth, or error handling
- A query builder UI (use Query Studio in the full example for that)

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml spring-boot:run
```

## Quick Start: Try These First

After the app starts, run these in order to see the core patterns:

1. **See the data:**
   ```bash
   curl "http://localhost:8080/api/employees"
   ```
   Shows the 5 employees in memory. Names: Ava, Milan, Lina, Noah, Sara; departments: Engineering, Finance, Support, Marketing.

2. **Try a simple filter:**
   ```bash
   curl "http://localhost:8080/api/employees/top-paid?minSalary=100000&limit=3"
   ```
   Returns employees with salary >= $100k, ordered by salary (descending). Shows the SQL-like `.where()` clause in action.

3. **Then try aggregation:**
   ```bash
   curl "http://localhost:8080/api/employees/department-salary-summary?minSalary=90000"
   ```
   Groups by department, counts headcount, and computes average/max salary. Shows `.group by()` and aggregate functions.

4. **Check runtime config:**
   ```bash
   curl "http://localhost:8080/api/employees/runtime"
   ```
   Confirms the runtime is initialized and shows feature flags (caching, virtual threads, etc.).

## All Endpoints

### Top-Paid Employees
```bash
curl "http://localhost:8080/api/employees/top-paid?minSalary=100000&limit=3"
```

### Employees by Department
Fetch employees from a specific department, ordered by salary (descending). The `limit` parameter defaults to 10 and is capped at 25.
```bash
curl "http://localhost:8080/api/employees/by-department?department=Engineering&limit=2"
```

### Employees by Salary Range
Fetch employees within a salary range, ordered by salary (descending). The `limit` parameter defaults to 10 and is capped at 25. The salary range is inclusive. Note: `minSalary` is clamped to 0 (negative values become 0), and `maxSalary` is normalized to be at least `minSalary` (if `maxSalary < minSalary`, it becomes `minSalary`).
```bash
curl "http://localhost:8080/api/employees/by-salary-range?minSalary=100000&maxSalary=140000&limit=2"
```

### Department Salary Summary
```bash
curl "http://localhost:8080/api/employees/department-salary-summary?minSalary=90000"
```

### Runtime Info
```bash
curl "http://localhost:8080/api/employees/runtime"
```

## Optional Virtual-Thread Profile

Run the example with Spring virtual threads enabled:

```bash
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml spring-boot:run "-Dspring-boot.run.profiles=virtual"
```

Use this only to evaluate the blocking web boundary. The PojoLens query engine
itself is still in-memory and CPU-bound. `/api/employees/runtime` now exposes
`virtualThreadsEnabled` and `requestThreadVirtual` so the running mode is
visible from the example itself.

## Test

```bash
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml test
```

## Code Structure: Where to Look

**Start with these two files:**

- **`QuickstartEmployeeController.java`** - The REST endpoints.
  - Four `@GetMapping` methods for different query patterns.
  - Each method shows the SQL-like query string, parameter binding via `.params(Map.of(...))`, and filtering via `.filter(employees, ResultClass)`.
  - Notice the parameter clamping and defaulting logic (for example, `Math.max(1, Math.min(limit, 25))`).

- **`QuickstartEmployeeTypes.java`** - The data models.
  - `Employee` - the raw POJO (id, name, department, salary).
  - `EmployeeView` - the result type for individual employee queries.
  - `DepartmentSalarySummaryView` - the result type for aggregated queries.
  - `RuntimeInfo` - metadata about the runtime state.

**The query engine itself** is hidden in the `PojoLensRuntime` (auto-configured by the starter). You do not need to touch it to extend this example - just call `.parse()` and `.filter()` with your POJO list and result type.

## How to Extend It

**To add a new query endpoint:**

1. **Define the SQL-like query string** in the controller (as a private static final string).
2. **Add a new `@GetMapping` method** that calls `pojoLensRuntime.parse(query).params(...).filter(employees, ResultType.class)`.
3. **Create a result type class** in `QuickstartEmployeeTypes.java` if needed (annotate fields with `@CsvName` to map columns).
4. **Add a curl example** in this README under "All Endpoints".

**Example: Add a query for employees in a specific salary band:**
- Query string: `"select id, name, salary where salary >= :minSalary and salary <= :maxSalary order by salary desc limit :limit"`
- Method: `@GetMapping("/by-salary-band") public List<EmployeeView> bySalaryBand(...)`
- Reuse `EmployeeView` as the result type (same columns).
- Add the curl example to the README.
