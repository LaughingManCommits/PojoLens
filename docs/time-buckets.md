# Time Buckets and Calendar Presets

`PojoLens` time buckets default to deterministic `UTC` + ISO-week behavior.

Use `TimeBucketPreset` when you need explicit calendar semantics:
- bucket granularity: `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`
- timezone: `ZoneId`
- week start: `MONDAY` by default, configurable for `WEEK`

## Fluent

```java
List<WeeklyHeadcount> rows = PojoLensCore.newQueryBuilder(source)
    .addTimeBucket(
        "hireDate",
        TimeBucketPreset.week()
            .withZone("Europe/Amsterdam")
            .withWeekStart("sunday"),
        "period")
    .addCount("headcount")
    .initFilter()
    .filter(WeeklyHeadcount.class);
```

## SQL-like

Supported forms:
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
- `LocalDate` and `LocalDateTime` inputs are interpreted in the active bucket preset timezone
- `Date`, `Instant`, `OffsetDateTime`, and `ZonedDateTime` inputs are normalized into the active bucket preset timezone before bucketing
- timezone is optional; default is `UTC`
- week start is optional; default is `MONDAY`
- week start is valid only for `WEEK` buckets

## Natural

Supported forms:
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
- the natural bucket phrase lowers to the same `TimeBucketPreset` path used by fluent and SQL-like queries
- bucket outputs should use `as <alias>`
- grouped natural queries must include the bucket alias in `group by`
- timezone defaults to `UTC`
- week buckets default to `MONDAY`

## Chart Presets

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

Fluent, natural, and SQL-like `explain()` payloads include time bucket entries in:

`<alias>:<field>:<bucket>:<zoneId>:<weekStart>`

Example:
- `period:hireDate:WEEK:Europe/Amsterdam:SUNDAY`


