# CSV Adapter Guide

`PojoLensCsv` is a boundary adapter that loads UTF-8 CSV files into typed rows
before they enter the existing in-memory engine.

It is not a second query engine. Once the rows are loaded, all fluent, SQL-like,
and plain-English query features work on them exactly as they do on any other
`List<T>`.

## When To Use

Use `PojoLensCsv` only at the file boundary - when data starts as a CSV file
and you need to bring it into the engine as typed rows.

For data that is already in Java objects or collections, use `PojoLensCore`,
`PojoLensSql`, or `PojoLensNatural` directly.

## Basic Usage

```java
List<Employee> rows = PojoLensCsv.read(Path.of("employees.csv"), Employee.class);
```

After loading, reuse the existing engine normally:

```java
List<Employee> result = PojoLensSql
    .parse("where department = 'Engineering' order by salary desc")
    .filter(rows, Employee.class);
```

Or with plain-English queries:

```java
List<Employee> result = PojoLensNatural
    .parse("show employees where department is Engineering sort by salary descending")
    .filter(rows, Employee.class);
```

## Options

Use `CsvOptions` only when the defaults do not match the file:

```java
List<Employee> rows = PojoLensCsv.read(
    Path.of("employees.csv"),
    Employee.class,
    CsvOptions.builder()
        .delimiter(';')
        .trim(true)
        .build()
);
```

| Option | Default | Description |
|---|---|---|
| `header` | `true` | First row is a header row with column names. |
| `delimiter` | `','` | Field separator character. |
| `trim` | `true` | Trim leading/trailing whitespace from each value. |
| `skipEmptyLines` | `true` | Skip blank lines while parsing. |

`CsvOptions.defaults()` returns the pre-built instance with all defaults applied.

## Quoted Fields

Quoted fields may contain delimiters, escaped quotes (`""`), and embedded line
breaks.

When a quoted field spans multiple physical lines, `PojoLensCsv` keeps reading
until the logical record closes. Embedded line breaks are normalized to `\n` in
the loaded `String` values.

Blank physical lines are skipped only between records when `skipEmptyLines` is
enabled. Blank lines inside a quoted field remain part of that field's value.

## Header Mapping

The adapter maps CSV column names to Java field names by exact match.
Header-mode reads reject duplicate or unknown columns.
Primitive-backed target fields are required; nullable object-typed fields may
be omitted and will default to `null`.

A CSV with columns `id,name,department,salary` maps directly to a POJO with
fields of those names:

```java
public class Employee {
    public int id;
    public String name;
    public String department;
    public int salary;

    public Employee() {}
}
```

### Nested Field Paths

Nested object fields use dot notation in the header:

```text
id,address.city,address.geo.countryCode
7,Amsterdam,NL
```

```java
public class Row {
    public int id;
    public Address address;

    public Row() {}
}

public class Address {
    public String city;
    public Geo geo;

    public Address() {}
}

public class Geo {
    public String countryCode;

    public Geo() {}
}
```

The adapter creates the nested objects and sets each field by path.

## Supported Field Types

The adapter coerces string values to these target types:

| Java type | Parse rule |
|---|---|
| `String` | Raw string value. |
| `int` / `Integer` | `Integer.valueOf(value)`. |
| `long` / `Long` | `Long.valueOf(value)`. |
| `double` / `Double` | `Double.valueOf(value)`. |
| `float` / `Float` | `Float.valueOf(value)`. |
| `short` / `Short` | `Short.valueOf(value)`. |
| `byte` / `Byte` | `Byte.valueOf(value)`. |
| `boolean` / `Boolean` | `true/false/1/0/yes/no/on/off` (case-insensitive). |
| `char` / `Character` | Single character only. |
| `LocalDate` | ISO-8601 date (`2024-01-15`). |
| `LocalDateTime` | ISO-8601 date-time (`2024-01-15T10:30:00`). |
| `OffsetDateTime` | ISO-8601 with offset (`2024-01-15T10:30:00+01:00`). |
| `ZonedDateTime` | ISO-8601 with zone (`2024-01-15T10:30:00+01:00[Europe/Amsterdam]`). |
| `Instant` | ISO-8601 instant (`2024-01-15T10:30:00Z`) or offset. |
| `java.util.Date` | ISO-8601 instant, offset, zoned, date-time, or date. |
| `Enum` | Exact enum constant name, case-sensitive. |

Blank values coerce to `null` for object-typed fields. Blank values for
primitive fields throw an error immediately.

## Error Model

All errors include row number and column name for fast diagnosis.
For multiline quoted records, the row number is the logical record start line.

| Error condition | Example message |
|---|---|
| Invalid integer | `CSV row 18 column salary: cannot parse '12k' as Integer` |
| Invalid boolean | `CSV row 3 column active: cannot parse 'maybe' as Boolean` |
| Blank value for primitive field | `CSV row 5 column id: blank value is not allowed for primitive targets` |
| Missing required header column | `CSV header for Employee is missing required columns: salary` |
| Duplicate header column | `CSV header column 'id' is duplicated` |
| Header column not mapped to row type | `CSV header column 'unknown' does not map to Employee` |
| Wrong column count in a data row | `CSV row 7 has 3 columns; expected 4` |
| Unmatched quote | `CSV row 2 has an unmatched quote` |

## What This Adapter Is Not

- It does not stream rows lazily or process out-of-core data.
- It does not infer types from CSV content.
- It does not support Excel, TSV as a distinct format, or multi-format abstraction.
- It does not write back or export.
- It does not perform schema evolution or data cleansing.

All of these would expand the adapter beyond its purpose as a boundary helper.

## See Also

- Entry point selection: [docs/entry-points.md](entry-points.md)
- Product surface classification: [docs/product-surface.md](product-surface.md)
- SQL-like querying after load: [docs/sql-like.md](sql-like.md)
- Natural querying after load: [docs/natural.md](natural.md)
