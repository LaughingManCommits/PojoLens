# Spring/JDBC Bridge

`PojoLensJdbc` is a thin bridge between `JdbcTemplate` and
`SqlLikeResultSetAdapter`. It lives in `pojo-lens-spring-boot-autoconfigure`
and requires `spring-jdbc` on the classpath (declared as optional in the
autoconfigure pom).

## Usage

Run a query and materialize rows directly — no `ResultSetExtractor` boilerplate:

```java
List<MyRow> rows = PojoLensJdbc.query(jdbcTemplate, sql, MyRow.class, param1, param2);
```

With pushdown audit metadata:

```java
SqlLikePushdownResult<MyRow> result = PojoLensJdbc.queryPushed(
    jdbcTemplate, sql, MyRow.class, List.of("filter", "order"), param1);
```

From a raw `ResultSet` (delegates to `SqlLikeResultSetAdapter`):

```java
List<MyRow> rows = PojoLensJdbc.read(resultSet, MyRow.class);
```

## Column Label Mapping

Follows `SqlLikeResultSetAdapter` rules: `snake_case`, `kebab-case`, and spaced
column labels map to camelCase Java field names automatically.

Examples: `merchant_name` → `merchantName`, `merchant name` → `merchantName`.

## Module

`PojoLensJdbc` is in `pojo-lens-spring-boot-autoconfigure`, not in the core
`pojo-lens` jar. Apps using the starter already have it on the classpath.

```xml
<dependency>
  <groupId>io.github.laughingmancommits</groupId>
  <artifactId>pojo-lens-spring-boot-starter</artifactId>
  <version>2026.04.17.1834</version>
</dependency>
```

## Relationship to SqlLikeResultSetAdapter

`SqlLikeResultSetAdapter` lives in the core `pojo-lens` jar and works directly
with a `ResultSet`. Use it when you hold the result set yourself or are not
using Spring. Use `PojoLensJdbc` when you have a `JdbcTemplate` and want a
one-liner.
