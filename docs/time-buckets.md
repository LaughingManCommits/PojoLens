# Time Buckets and Calendar Presets

`PojoLens` time buckets default to deterministic `UTC` + ISO-week behavior.

Use `TimeBucketPreset` when you need explicit calendar semantics:
- bucket granularity: `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`
- timezone: `ZoneId`
- week start: `MONDAY` by default, configurable for `WEEK`

## Fluent

```java
List<WeeklyHeadcount> rows = PojoLens.newQueryBuilder(source)
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
List<WeeklyHeadcount> rows = PojoLens
    .parse("select bucket(hireDate,'week','Europe/Amsterdam','sunday') as period, count(*) as headcount group by period")
    .filter(source, WeeklyHeadcount.class);
```

Notes:
- timezone is optional; default is `UTC`
- week start is optional; default is `MONDAY`
- week start is valid only for `WEEK` buckets

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

Fluent and SQL-like `explain()` payloads include time bucket entries in:

`<alias>:<field>:<bucket>:<zoneId>:<weekStart>`

Example:
- `period:hireDate:WEEK:Europe/Amsterdam:SUNDAY`

