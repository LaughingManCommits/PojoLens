# Spring Boot Starter Quickstart Example

This is the smallest runnable Spring Boot starter example in this repo.
It shows one SQL-like PojoLens query flow end-to-end, with the starter runtime
only supplying integration defaults:

- the app executes one SQL-like top-paid query over in-memory POJOs
- the app also exposes one grouped salary-summary query by department
- starter auto-configures `PojoLensRuntime`
- the endpoint returns typed rows

For the full dashboard, reports, JDBC, and Query Studio workflow, use:
`examples/spring-boot-starter-risk-console`.

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml spring-boot:run
```

## Try It

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
