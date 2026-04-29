# Facet Options

`FacetPresets` provides in-memory distinct-value counting for filter bars and
navigation facets. Results are sorted by count descending, then by value
ascending for ties.

## Basic Usage

```java
List<FacetOption> options = FacetPresets.distinctCounts("merchantRegion").options(rows);
// options == [FacetOption("EMEA", 342), FacetOption("APAC", 128), ...]
```

Each `FacetOption` carries:
- `value()` — the distinct field value (String; null if the field was null)
- `count()` — number of rows with that value

## Reusing a FacetQuery

`FacetPresets.distinctCounts()` returns a `FacetQuery` that can be applied to
different row lists:

```java
FacetQuery regionFacet = FacetPresets.distinctCounts("merchantRegion");

List<FacetOption> all     = regionFacet.options(allRows);
List<FacetOption> current = regionFacet.options(filteredRows);
```

## Multiple Facets from One Snapshot

```java
List<FacetOption> regions  = FacetPresets.distinctCounts("merchantRegion").options(rows);
List<FacetOption> statuses = FacetPresets.distinctCounts("status").options(rows);
List<FacetOption> bands    = FacetPresets.distinctCounts("riskBand").options(rows);
```

## Notes

- Field access uses reflection over declared instance fields. Fields do not
  need public visibility, and dot-separated nested paths are supported when
  each segment is readable.
- `static` and `@Exclude` fields are not part of the readable field graph.
- `null` rows in the list are skipped.
- `null` field values are counted under key `null`.
- Results are unmodifiable (`List.copyOf`).
