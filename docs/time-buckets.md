# Time Buckets and Calendar Presets

`PojoLens` time buckets default to deterministic `UTC` + ISO-week behavior.

Use `TimeBucketPreset` when you need explicit calendar semantics:
- bucket granularity: `HOUR`, `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`
- timezone: `ZoneId`
- week start: `MONDAY` by default, configurable for `WEEK`

## SQL-like

Supported forms:
- `bucket(dateField, 'hour') as period`
- `bucket(dateField, 'month') as period`
- `bucket(dateField, 'month', 'Europe/Amsterdam') as period`
- `bucket(dateField, 'week', 'Europe/Amsterdam', 'sunday') as period`

```java
List<WeeklyHeadcount> rows = PojoLensSql
    .parse("select bucket(hireDate,'week','Europe/Amsterdam','sunday') as period, count(*) as headcount group by period")
    .filter(source, WeeklyHeadcount.class);
```

Notes:
- bucket source fields may be `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, or `ZonedDateTime`
- hour buckets are formatted as `YYYY-MM-DDTHH`
- buckets are wall-clock periods in the preset timezone: in a daylight-saving
  fall-back, both occurrences of the repeated local hour share one `HOUR`
  bucket; use the default `UTC` zone when every elapsed hour must stay separate
- `LocalDate` and `LocalDateTime` inputs are interpreted in the active bucket preset timezone
- `Date`, `Instant`, `OffsetDateTime`, and `ZonedDateTime` inputs are normalized into the active bucket preset timezone before bucketing
- timezone is optional; default is `UTC`
- week start is optional; default is `MONDAY`
- week start is valid only for `WEEK` buckets

## Date Parts

SQL-like date-part functions `year`, `quarter`, `month`, `day`, `hour`, `minute`,
and `day_of_week` (ISO, 1 = Monday) read a date/time value with the same types, zone
normalization, and `UTC` default as buckets. The optional zone is a text literal:
`month(hireDate, 'Europe/Amsterdam')`.

Buckets and date parts answer different questions:

- a bucket is a label that includes the year (`bucket(hireDate, 'month')` gives
  `2026-03`), so it groups a timeline
- a date part is a number that repeats every year (`month(hireDate)` gives `3`), so it
  compares or groups seasons across years

They always agree: `month(x, zone)` is the month in `bucket(x, 'month', zone)`.

```sql
where month(hireDate) in (6, 7, 8)
where day_of_week(hireDate, 'Europe/Amsterdam') >= 6
```

Group by a date part directly (`group by month(hireDate)`), or register it as a
computed field to reuse it across queries:

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("hireMonth", "month(hireDate)", Integer.class)
    .build();

List<MonthlyHires> rows = PojoLensSql
    .parse("select hireMonth, count(*) as hires group by hireMonth order by hireMonth")
    .computedFields(registry)
    .filter(source, MonthlyHires.class);
```

## Natural

Supported forms:
- `bucket hire date by hour as period`
- `bucket hire date by month as period`
- `bucket hire date by month in Europe/Amsterdam as period`
- `bucket hire date by week in Europe/Amsterdam starting sunday as period`

```java
List<WeeklyHeadcount> rows = PojoLensNatural
    .parse("show bucket hire date by week in Europe/Amsterdam starting sunday as period, "
        + "count of employees as headcount group by period sort by period ascending")
    .filter(source, WeeklyHeadcount.class);
```

Notes:
- the natural bucket phrase lowers to the same `TimeBucketPreset` path used by SQL-like queries
- bucket outputs should use `as <alias>`
- grouped natural queries must include the bucket alias in `group by`
- timezone defaults to `UTC`
- week buckets default to `MONDAY`

## Advanced Chart Preset Convenience

```java
ChartQueryPreset<WeeklyHeadcount> preset = ChartQueryPresets
    .timeSeriesCounts(
        "hireDate",
        TimeBucketPreset.week()
            .withZone("Europe/Amsterdam")
            .withWeekStart("sunday"),
        "period",
        "headcount",
        WeeklyHeadcount.class);
```

## Explain Output

Natural and SQL-like `explain()` payloads include time bucket entries in:

`<alias>:<field>:<bucket>:<zoneId>:<weekStart>`

Example:
- `period:hireDate:WEEK:Europe/Amsterdam:SUNDAY`


