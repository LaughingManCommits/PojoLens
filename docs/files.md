# File Boundary Loader Guide

`PojoLensFiles` is the single public file-boundary loader surface in PojoLens.

Use it when data starts as a file and needs to become typed in-memory rows
before normal SQL-like, natural, or typed execution.

Current format coverage:
- CSV via `PojoLensFiles.csv(...)`
- TSV via `PojoLensFiles.tsv(...)`
- JSON object/array files via `PojoLensFiles.json(...)`
- JSONL object streams via `PojoLensFiles.jsonl(...)`

`PojoLensCsv` remains available as the stable CSV-only convenience entry point
over the same loader support. It is not a separate product story.

## Default Routes

| If you need... | Default route | Why |
| --- | --- | --- |
| CSV file to typed rows | `PojoLensFiles.csv(path, rowType)` | Keeps CSV on the shared file-boundary surface. |
| TSV file to typed rows | `PojoLensFiles.tsv(path, rowType)` | Keeps tab-delimited loading on the same boundary surface instead of adding a peer `PojoLensTsv` entry point. |
| JSON file to typed rows | `PojoLensFiles.json(path, rowType)` | Keeps row-oriented JSON onboarding on the same bounded surface instead of adding a peer `PojoLensJson` entry point. |
| JSONL file to typed rows | `PojoLensFiles.jsonl(path, rowType)` | Keeps one-object-per-line loading on the same surface for logs, exports, and streaming snapshots. |
| Runtime-owned file defaults | `runtime.files().csv(...)`, `tsv(...)`, `json(...)`, or `jsonl(...)` | Lets format-specific loader defaults live on `PojoLensRuntime` while the format stays explicit at the call site. |
| Structured load diagnostics | `csvWithReport(...)`, `tsvWithReport(...)`, `jsonWithReport(...)`, or `jsonlWithReport(...)` | Keeps file-boundary troubleshooting on the loader surface. |

## Layering Rules

- Use file loaders only at the file boundary.
- Once rows are loaded, switch back to `PojoLensSql`, `PojoLensNatural`,
  `TypedQuery`, or `ReportDefinition`.
- Keep format choice explicit at the call site.
- Do not create peer top-level product stories per file format.

## Delimited Text: CSV And TSV

CSV and TSV currently share the same typed-row loader model:
- `CsvOptions` for header, delimiter, trim, empty-line, and coercion settings
- `CsvCoercionPolicy` for explicit boundary-only conversion rules
- `CsvLoadResult`, `CsvLoadReport`, and `CsvLoadException` for diagnostics

`PojoLensFiles.tsv(...)` always uses a tab delimiter, even when you pass a
`CsvOptions` instance with a different delimiter. Other options such as
`trim`, `header`, `skipEmptyLines`, and `coercionPolicy` still apply.

## Runtime Defaults

`runtime.files()` reuses `PojoLensRuntime` CSV defaults for delimited text
loading:
- `runtime.files().csv(...)` uses the runtime defaults as-is
- `runtime.files().tsv(...)` reuses the same defaults but forces the delimiter
  to `\t`

This keeps one runtime-owned delimited-text defaults model while the format
itself stays explicit.

## JSON And JSONL

JSON and JSONL share a row-oriented object loader model:
- `JsonOptions` for single-object acceptance, blank-line handling, unknown-field
  policy, and enum case sensitivity
- `JsonLoadResult`, `JsonLoadReport`, and `JsonLoadException` for diagnostics

`PojoLensFiles.json(...)` accepts either:
- a JSON array of row objects
- one JSON object when `JsonOptions.allowSingleObject()` is enabled

`PojoLensFiles.jsonl(...)` expects one JSON object per nonblank line.

`runtime.files().json(...)` and `runtime.files().jsonl(...)` reuse
`PojoLensRuntime` JSON defaults:
- `runtime.files().json(...)` uses `runtime.getJsonDefaults()`
- `runtime.files().jsonl(...)` uses the same defaults for JSONL object rows

Schema stays explicit:
- unknown fields fail by default
- primitive-backed target fields must be present
- diagnostics stay at the file boundary rather than widening query `explain(...)`

## Follow-On Execution

Use the loader only to get typed rows into memory, then switch back to the
normal execution surface.

SQL-like:

```java
List<Employee> rows = PojoLensFiles.json(Path.of("employees.json"), Employee.class);

List<Employee> filtered = PojoLensSql
    .parse("where department = 'Engineering' order by salary desc")
    .filter(rows, Employee.class);
```

Natural:

```java
List<Employee> rows = PojoLensFiles.jsonl(Path.of("employees.jsonl"), Employee.class);

List<Employee> filtered = PojoLensNatural
    .parse("show employees where active is true sort by salary descending")
    .filter(rows, Employee.class);
```

Typed:

```java
List<Employee> rows = PojoLensFiles.csv(Path.of("employees.csv"), Employee.class);

List<Employee> filtered = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.DEPARTMENT.eq("Engineering"))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .filter(rows);
```

## Explicit Non-Goal: Excel

Excel remains a deliberate non-goal for this loader surface.

Reason:
- workbook/sheet selection
- formula and formatting semantics
- cell-type and merged-cell behavior

Those concerns turn the adapter into a document model instead of a bounded
row-file boundary. Revisit it only if a row-first contract can stay as narrow
as the current CSV/TSV/JSON/JSONL surface.

## See Also

- CSV format reference: [csv.md](csv.md)
- Entry-point selection: [entry-points.md](entry-points.md)
- Product surface map: [product-surface.md](product-surface.md)
