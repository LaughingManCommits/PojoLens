# Spring Boot Starter Quickstart Example

This is the smallest runnable Spring Boot starter example in this repo.
It shows one PojoLens query flow end-to-end:

- starter auto-configures `PojoLensRuntime`
- the app executes one SQL-like top-paid query over in-memory POJOs
- the endpoint returns typed rows

For the full dashboard/presets/charts workflow, use:
`examples/spring-boot-starter-basic`.

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml spring-boot:run
```

## Try It

```bash
curl "http://localhost:8080/api/employees/top-paid?minSalary=100000&limit=3"
curl "http://localhost:8080/api/employees/runtime"
```

## Test

```bash
mvn -B -ntp -f examples/spring-boot-starter-quickstart/pom.xml test
```
