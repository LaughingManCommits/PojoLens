# Spring Boot Starter Basic Example

This example shows how to wire `pojo-lens-spring-boot-starter` into a Spring Boot app and use the auto-configured `PojoLensRuntime`.

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml spring-boot:run
```

## Try it

Top paid employees in a department:

```bash
curl "http://localhost:8080/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2"
```

Inspect runtime starter settings:

```bash
curl "http://localhost:8080/api/employees/runtime"
```

## What this demonstrates

- `PojoLensRuntime` is provided by Boot auto-configuration from the starter.
- Runtime behavior is controlled with `pojo-lens.*` properties (`preset`, strict/lint modes, telemetry).
- SQL-like queries are executed directly against in-memory POJO lists with typed projection.
