# SPIKE: CSV Input Surface

## Question

Can `pojo-lens` support CSV input while still staying true to its current
purpose as a POJO-first in-memory query engine?

Short answer:

- yes, if CSV is treated as an input adapter into the existing engine
- no, if CSV support turns the project into a general tabular ingestion or
  dataframe platform

## Thesis

CSV support can fit this repo if it is explicitly scoped as:

- CSV -> typed rows or row objects -> existing `pojo-lens` engine

It starts to drift from repo purpose if it becomes:

- a broad flat-file ingestion stack
- a schema-discovery platform
- a full dataframe engine
- a file-processing toolkit with lots of formats and pipeline stages

## Why This Is Attractive

Many users have data in CSV before they have POJOs.

That creates friction today:

- they must define a model first
- they must parse and coerce types themselves
- only then can they use fluent or SQL-like queries

A CSV entry point would lower that setup cost and would pair well with both:

- SQL-like for text-first querying
- a future plain-English surface for non-SQL users

## Why This Is Risky

The repo purpose is still POJO-first.

If CSV becomes a first-class raw-table engine, the product story changes from:

- query existing Java objects

to:

- ingest, type, manage, and query external tabular files

That is a materially larger surface area.

## Recommendation

Support CSV only as a bounded adapter layer.

Recommended rule:

- CSV support should feed the existing engine
- CSV support should not replace the POJO-first core story

That means the first versions should optimize for:

- loading CSV into typed rows
- predictable schema and type mapping
- reuse of existing fluent, SQL-like, chart, report, and explain features

Not for:

- arbitrary file lakes
- large-scale streaming ETL
- write-back
- Excel parity
- multi-format ingestion abstraction

## Best-Fit Product Position

Recommended positioning:

- core product remains POJO-first
- CSV support is an onboarding and adapter convenience

That means docs should describe CSV as:

- "load tabular files into the same in-memory query engine"

not:

- "pojo-lens is now a file analytics platform"

## Viable Architecture Options

### Option A: CSV -> POJO Mapping

Flow:

1. read CSV
2. map rows into user-provided class or record
3. query those objects through existing fluent or SQL-like paths

Example:

```java
List<Employee> rows = PojoLensCsv
    .read(path, Employee.class)
    .rows();

List<Employee> filtered = PojoLensSql
    .parse("where department = 'Engineering' order by salary desc")
    .filter(rows, Employee.class);
```

Pros:

- best fit with current repo purpose
- strongest reuse of existing engine behavior
- typed projections and validation remain natural
- minimal new execution semantics

Cons:

- user must still define a class or record
- weak story for ad hoc unknown CSV shape exploration

### Option B: CSV -> Dynamic Row Object

Flow:

1. read CSV
2. create dynamic row objects with column metadata
3. query through a row abstraction that the engine can understand

Example:

```java
CsvDataset dataset = PojoLensCsv.read(path, CsvSchema.builder()
    .column("name", STRING)
    .column("salary", INTEGER)
    .column("department", STRING)
    .build());

List<RowRecord> rows = PojoLensSql
    .parse("where department = 'Engineering' order by salary desc")
    .filter(dataset.rows(), RowRecord.class);
```

Pros:

- better ad hoc exploration story
- less boilerplate for one-off files
- better fit for config-driven or UI-driven use cases

Cons:

- pulls the engine toward generic tabular modeling
- harder typed projection story
- greater pressure to add schema inference, type coercion, and column aliasing

### Option C: CSV -> Generated POJO/Record

Flow:

1. infer or accept schema
2. generate a row type or use a generated adapter
3. execute through existing engine

Pros:

- keeps a typed story
- lowers manual model effort

Cons:

- code generation and lifecycle complexity
- likely too heavy for an early version

## Recommendation On Architecture

Best first direction:

- Option A first
- Option B only if there is strong demand for ad hoc file exploration

Rationale:

- Option A stays closest to the current POJO-first identity
- Option A gives a clear incremental story
- Option B is where scope starts expanding quickly

## Candidate Public API Shapes

### Shape 1: Simple Loader

```java
List<Employee> rows = PojoLensCsv.read(path, Employee.class).rows();
```

This is the safest shape.

### Shape 2: Loader With Options

```java
List<Employee> rows = PojoLensCsv
    .read(path, Employee.class, CsvOptions.builder()
        .header(true)
        .delimiter(',')
        .trim(true)
        .build())
    .rows();
```

### Shape 3: Runtime-Scoped CSV Policy

```java
PojoLensRuntime runtime = PojoLensRuntime.builder()
    .csvDefaults(csv -> csv.header(true).trim(true))
    .build();

List<Employee> rows = runtime.csv()
    .read(path, Employee.class)
    .rows();
```

Recommendation:

- prefer runtime-scoped options for anything policy-like
- keep static convenience for obvious defaults

## What "All Features" Really Means

The phrase "all features we have for POJOs" needs to be split carefully.

### Realistic To Reuse Quickly

- filtering
- ordering
- grouping
- `HAVING`
- joins, if users bind multiple CSV-loaded datasets explicitly
- chart mapping
- report/preset helpers
- explain
- SQL-like execution once the rows are loaded
- future plain-English surface once the rows are loaded

### Requires Care

- strict type validation
- null handling
- date/time parsing
- number coercion
- enum parsing
- computed fields over CSV-origin values

### Not Truly Equivalent Without Extra Design

- field metamodel generation
- strongly typed selector ergonomics before mapping to classes
- "same as POJO" error quality when schema is inferred loosely
- type-safe projections if the source stays dynamic

So the honest answer is:

- yes for most execution features after loading
- no, not automatically for every typed API experience

## Scope Boundaries

Recommended in-scope:

- RFC 4180-style CSV support
- header-based column mapping
- explicit delimiter configuration
- explicit type mapping
- UTF-8 text input
- runtime-scoped coercion policy
- adapter-only loading into memory

Recommended out-of-scope for v1:

- Excel files
- multi-format abstraction
- automatic remote URL ingestion
- large-file out-of-core execution
- schema evolution tooling
- data cleansing pipelines
- malformed CSV repair heuristics
- write-back or export workflows

## Schema Strategy

This is the most important design decision after parsing.

Recommended v1 modes:

### Mode 1: User-Provided Type

```java
PojoLensCsv.read(path, Employee.class)
```

Best default.

### Mode 2: Explicit Schema

```java
PojoLensCsv.read(path, CsvSchema.builder()
    .column("name", STRING)
    .column("salary", INTEGER)
    .column("hireDate", LOCAL_DATE)
    .build())
```

Useful for ad hoc files without POJOs.

Recommendation:

- support explicit class mapping first
- support explicit schema second
- avoid automatic schema inference as the primary path

## Type System Concerns

CSV is stringly-typed at the file boundary.

That means CSV support needs explicit policy for:

- blank string vs null
- integer parsing
- decimal parsing
- locale-sensitive numbers
- booleans
- dates
- timestamps
- enum values

This is exactly where scope can expand quickly.

Recommended stance:

- keep defaults strict
- make coercion explicit
- fail fast with row/column-aware errors

## Error Model

Good CSV support lives or dies on error quality.

Desired errors:

- missing required column
- duplicate header
- invalid integer/date/boolean at row and column
- unexpected extra column when strict mapping is enabled
- unmapped column when explicit schema is required

Example:

- `CSV row 18 column salary: cannot parse '12k' as INTEGER`

## Relationship To SQL-like And Plain-English

CSV support is complementary to both text query surfaces.

Examples:

```java
List<Employee> rows = PojoLensCsv.read(path, Employee.class).rows();

List<Employee> result = PojoLensNatural
    .parse("show name, salary where department is Engineering")
    .filter(rows, Employee.class);
```

or

```java
List<Employee> result = PojoLensSql
    .parse("where department = 'Engineering'")
    .filter(rows, Employee.class);
```

That is a strong combined story:

- plain-English or SQL-like for query authoring
- CSV as data onboarding
- fluent as the underlying execution core

## Performance Considerations

CSV parsing cost is separate from query cost.

Recommended benchmark discipline:

- parse/load benchmarks separate from query execution benchmarks
- do not hide CSV parsing overhead inside existing query benchmarks
- keep execution parity measurements after the load boundary

That preserves the repo's current benchmark discipline around execution-only
measurements.

## Where This Gets Too Close To The Sun

This idea gets too close to the sun if any of these become primary goals:

- querying files directly without a clear in-memory load boundary
- building a dataframe-style generic table engine
- prioritizing file ingestion over querying existing Java objects
- adding many file formats just because CSV exists
- turning the runtime into a general ETL framework

If that happens, the project stops being clearly POJO-first.

## Where It Still Fits

This still fits the repo well if:

- CSV is framed as an adapter into the same in-memory engine
- POJO or explicit row schema remains central
- existing query semantics stay unchanged
- docs still describe the library primarily as querying Java objects

## Reevaluated Rollout

The original phase split still holds, but the repo now has enough concrete
implementation to tighten the order:

- `CSV-WP1` is complete: `PojoLensCsv.read(...)`, `CsvOptions`, strict typed
  loading, nested-path projection reuse, and aligned docs/tests are already in
  the repo.
- The remaining Phase 1 work should stay parser-hardening only; do not widen
  into runtime policy or dynamic schema before common exported CSV files load
  cleanly.
- The old Phase 2 bundle should be split so runtime ownership, coercion policy,
  and diagnostics can move independently.
- Dynamic-schema work remains explicitly gated; do not start it unless the
  typed-first path proves insufficient on real use cases.

## Active Work Package Board

### CSV-WP1: Typed Loader Foundation (Completed)

Status:

- completed `2026-04-09`

Goal:

- land a bounded CSV-to-typed-row adapter without changing query-core
  semantics

Scope:

- static `PojoLensCsv.read(path, rowType)` entry points
- narrow `CsvOptions` for header/delimiter/trim/skip-empty behavior
- strict header mapping, nested-path projection reuse, and row/column-aware
  coercion failures
- public docs and public-API coverage

Deliver:

- CSV can enter the existing engine as typed in-memory rows with no query-core
  changes

Success bar:

- fluent, SQL-like, chart, report, and natural surfaces all work only after the
  load, not through a second tabular engine

Findings:

- the current parser is still physical-line based, so quoted multiline fields
  are the next real compatibility gap
- the bounded adapter story is now proven in code and docs; the remaining work
  is about hardening and policy, not repositioning the library

### CSV-WP2: RFC 4180 Record Hardening (Completed)

Status:

- completed `2026-04-10`

Goal:

- finish Phase 1 by handling the common CSV shapes exported by spreadsheets and
  line-oriented tools without changing the adapter boundary

Scope:

- support quoted fields that span multiple physical lines
- keep escaped-quote and delimiter handling correct across embedded newlines
- preserve row/column diagnostics using the logical record start line
- lock BOM, CRLF, blank-line, and quoted-newline behavior in docs/tests

Deliver:

- common exported CSV files import cleanly into typed rows without widening the
  public model

Success bar:

- `PojoLensCsv.read(...)` still returns `List<T>` and no runtime/dynamic schema
  API is introduced

Findings:

- quoted fields may now span multiple physical lines while still loading into
  typed rows
- embedded line breaks are normalized to `\n`, blank lines inside quoted fields
  remain data, and blank lines between records still honor `skipEmptyLines`
- parser and coercion failures on multiline records now report the logical
  record start line instead of the later physical line where parsing stopped

### CSV-WP3: Runtime-Owned CSV Entry Point (Ready)

Status:

- ready

Goal:

- let applications configure CSV onboarding once per runtime instead of
  repeating `CsvOptions` at every call site

Scope:

- add a runtime-owned CSV entry point such as `runtime.csv()`
- let runtime defaults seed header/delimiter/trim/skip-empty behavior
- keep static `PojoLensCsv.read(...)` convenience intact
- support explicit per-call overrides on top of runtime defaults

Deliver:

- DI and multi-tenant callers can centralize file-boundary CSV policy without
  touching query execution

Success bar:

- no query-core changes and no inferred/dynamic schema surface

### CSV-WP4: Explicit Coercion Policy (Planned)

Status:

- planned

Goal:

- turn the current hard-coded null/date/enum/number parsing rules into explicit
  policy where real CSV variation demands it

Scope:

- add opt-in policy hooks for null tokens, date/time parsing, enum matching,
  and numeric coercion
- keep the existing strict defaults unchanged
- thread the policy through the static and runtime CSV entry points
- expand docs/tests around ambiguous value handling

Deliver:

- callers can adapt to common CSV conventions without preprocessing the file
  externally

Success bar:

- defaults stay strict and typed-first; policy is explicit rather than inferred

### CSV-WP5: Load Diagnostics And Observability (Planned)

Status:

- planned

Goal:

- make CSV onboarding inspectable in the same operational style as the rest of
  the runtime

Scope:

- expose load-level diagnostics such as row counts, resolved schema, rejected
  columns, and timing/coercion failures
- integrate with existing telemetry patterns or a narrow load report without
  pretending CSV load is a second query engine
- document which signals are for load troubleshooting vs query explain output

Deliver:

- operators can tell what a CSV load did and why it failed without stepping
  through parser internals

Success bar:

- diagnostics stay load-scoped and do not blur into raw-table execution
  features

### CSV-WP6: Explicit Dynamic Schema (Deferred)

Status:

- deferred pending typed-first demand

Goal:

- support ad hoc unknown-shape CSV exploration only if the typed-first adapter
  proves insufficient

Scope:

- design an explicit `CsvSchema`/dynamic row surface instead of silent
  inference
- document the feature gaps vs typed POJO flows up front
- keep joins/charts/reports as post-load reuse of the same engine, not a new
  tabular runtime

Deliver:

- an optional exploration path for unknown CSV shape with explicit tradeoffs

Success bar:

- do not open this package until `CSV-WP2` through `CSV-WP5` are complete or a
  real user need justifies the added scope risk

## Recommended Order

1. `CSV-WP2`
2. `CSV-WP3`
3. `CSV-WP4`
4. `CSV-WP5`
5. `CSV-WP6` only if the typed-first path still proves insufficient

## Initial Recommendation

Pursue CSV support only if it is framed as:

- a bounded input adapter
- typed first
- runtime-configurable where needed
- fully separate from the execution core

The safest statement of intent is:

- `pojo-lens` remains POJO-first, but can optionally load CSV into the same
  in-memory query engine

## Bottom Line

This is not too close to the sun if it stays a CSV-to-engine adapter.

It is too close to the sun if it becomes a general tabular ingestion platform.

If you want this feature, the correct first step is not:

- "query raw CSV like a dataframe"

The correct first step is:

- "load CSV into typed in-memory rows, then reuse the exact features and
  semantics we already have"
