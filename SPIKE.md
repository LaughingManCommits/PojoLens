# SPIKE: Plain-English Query Surface

## Problem

`pojo-lens` already has two strong query-authoring paths:

- fluent for service-owned query logic in code
- SQL-like for dynamic or config-driven query text

That leaves a gap for users who need dynamic text queries but do not know SQL.
For those users, SQL-like is still more approachable than hand-written fluent
builder code, but it assumes SQL vocabulary and clause ordering.

The idea in this spike is to add a third authoring surface: a controlled
plain-English-like query language that compiles to the same underlying engine.

## Thesis

Add a new parser-driven surface for plain-English-like queries, while keeping
fluent as the underlying execution model and keeping SQL-like as the power-user
text surface.

This should be treated as a deterministic query language, not as unconstrained
natural language and not as a remote AI feature.

## Status

As of `2026-04-02`, this spike is mostly implemented.

Done:

- `PojoLensNatural`, `NaturalQuery`, `NaturalBoundQuery`, and `PojoLensRuntime.natural()`
- deterministic parsing/lowering into the shared execution path
- runtime-scoped `NaturalVocabulary`
- grouped aggregates, time buckets, and chart phrases
- explicit joins with `JoinBindings` / `DatasetBundle`
- deterministic windows plus alias-based `qualify`
- canonical natural-query docs in `docs/natural.md`

Remaining:

- computed fields in plain wording
- reusable natural templates and presets

## What This Is

- a new query authoring surface for non-SQL users
- a deterministic parser with documented grammar
- another explicit entry point, alongside `PojoLensCore` and `PojoLensSql`
- a translator to the same lower execution plan used by the current engine

## What This Is Not

- not a new execution engine
- not a conversational assistant
- not a free-form LLM dependency in the core library
- not fuzzy guessing that silently changes query meaning
- not a replacement for fluent or SQL-like

## Why It Fits This Repo

The current public story is:

- `PojoLensCore` for service-owned fluent query logic
- `PojoLensSql` for dynamic or config-driven text queries

A plain-English-like surface fits that same model:

- `PojoLensNatural` or similar for dynamic text queries authored by non-SQL users

This keeps the public surface coherent:

- fluent remains the core authoring and execution foundation
- SQL-like remains the expert text surface
- plain-English-like becomes the accessibility surface

## Recommendation

Do not market this as full natural language in the first version.

Recommend a controlled, plain-English query language with:

- fixed clause words
- explicit operators
- deterministic parsing
- strict validation
- helpful suggestions for unknown or ambiguous terms

That keeps the feature explainable, cacheable, testable, and safe for a Java
library that runs locally over POJOs.

## Candidate Public API

Recommended new entry point:

```java
List<Employee> rows = PojoLensNatural
    .parse("show name, salary where department is :dept and salary is at least :minSalary sort by salary descending limit 10")
    .params(Map.of("dept", "Engineering", "minSalary", 120000))
    .filter(source, Employee.class);
```

Grouped query:

```java
List<DepartmentHeadcount> rows = PojoLensNatural
    .parse("show department, count of employees as total group by department having total at least 5 sort by total descending")
    .filter(source, DepartmentHeadcount.class);
```

Chart query:

```java
ChartData chart = PojoLensNatural
    .parse("show department and count of employees group by department sort by count of employees descending as bar chart")
    .chart(source, Employee.class);
```

Runtime-scoped option for aliases, policy, telemetry, and caching:

```java
PojoLensRuntime runtime = PojoLensRuntime.builder()
    .build();

List<Employee> rows = runtime.natural()
    .parse("show active employees where salary is above 100000 sort by salary descending")
    .filter(source, Employee.class);
```

## Naming Options

Candidate names:

- `PojoLensNatural`
- `PojoLensPlain`
- `PojoLensIntent`

Recommendation:

- [x] public name: `PojoLensNatural`
- [x] documentation wording: "controlled plain-English query surface"

`PojoLensNatural` matches `PojoLensSql` well, while the docs can still be honest
that the grammar is controlled rather than free-form.

## Language Shape

The language should feel simpler than SQL-like, but still be structured.

Recommended clause words:

- `show`
- `where`
- `group by`
- `having`
- `sort by`
- `limit`
- `offset`
- `join`
- `with`
- `bucket by`
- `as`

Recommended operator phrases:

- `is`
- `is not`
- `at least`
- `more than`
- `less than`
- `between`
- `contains`
- `starts with`
- `ends with`
- `before`
- `after`
- `in`

Recommended aggregate phrases:

- `count of`
- `sum of`
- `average of`
- `minimum of`
- `maximum of`

Recommended chart phrases:

- `as bar chart`
- `as line chart`
- `as area chart`
- `as pie chart`
- `as scatter chart`

## Example Surface Comparison

Simple filter and sort:

Fluent:

```java
List<Employee> rows = PojoLensCore
    .newQueryBuilder(source)
    .where(Employee::getDepartment).eq("Engineering")
    .where(Employee::getSalary).gte(120000)
    .orderBy(Employee::getSalary, SortDirection.DESC)
    .limit(10)
    .filter(Employee.class);
```

SQL-like:

```java
List<Employee> rows = PojoLensSql
    .parse("where department = 'Engineering' and salary >= 120000 order by salary desc limit 10")
    .filter(source, Employee.class);
```

Plain-English-like:

```java
List<Employee> rows = PojoLensNatural
    .parse("show employees where department is Engineering and salary is at least 120000 sort by salary descending limit 10")
    .filter(source, Employee.class);
```

## Key Design Principle

Plain-English-like should be easier than SQL-like, but not ambiguous.

That means avoiding these in the first version:

- `top performers`
- `recent hires`
- `well paid employees`
- `big departments`
- `show me the best departments`

These phrases sound natural but require hidden business meaning or implicit sort
rules. The first version should reject them and explain why.

## Field Vocabulary Is Critical

Non-SQL users also often do not know Java field names.

That means a usable plain-English surface needs a vocabulary layer, not just a
parser.

Example:

- Java field: `salary`
- friendly labels: `salary`, `pay`, `annual pay`

- Java field: `active`
- friendly labels: `active`, `currently employed`

- Java field: `hireDate`
- friendly labels: `hire date`, `start date`

This likely belongs on `PojoLensRuntime`, because vocabulary can be:

- app-specific
- tenant-specific
- locale-specific
- domain-specific

Possible runtime concept:

```java
PojoLensRuntime runtime = PojoLensRuntime.builder()
    .naturalVocabulary(vocabulary -> vocabulary
        .field("salary", "salary", "pay", "annual pay")
        .field("hireDate", "hire date", "start date")
        .field("active", "active", "currently employed"))
    .build();
```

Without this layer, the parser will still feel technical.

## Architecture Direction

Recommended architecture:

1. plain-English text
2. tokenize and parse into a natural-query AST
3. bind field names, aliases, parameters, and vocabulary
4. normalize into a shared internal query model
5. lower into the same execution path used by the existing engine

Important point:

- do not build a third execution stack

The parser should compile down to the existing engine.

## Lowering Strategy Options

### Option A: Lower To SQL-like Text

Flow:

- plain-English parse
- emit SQL-like text
- feed that into `PojoLensSql`

Pros:

- fastest path to MVP
- reuses existing SQL-like validation and execution behavior

Cons:

- brittle string generation layer
- harder error mapping back to the original plain-English input
- leaks SQL-like semantics into what should be a first-class surface

### Option B: Lower To SQL-like AST Or Shared Normalized Plan

Flow:

- plain-English parse
- build a structured intermediate representation
- lower into existing normalized planner/execution structures

Pros:

- cleaner long-term architecture
- better explain output
- better validation and error mapping
- easier parity testing against fluent and SQL-like

Cons:

- more up-front design work

Recommendation:

- short-term MVP may bootstrap through existing SQL-like planning internals if
  that reduces risk
- target architecture should be a shared normalized query model, not generated
  SQL-like strings

Implemented outcome:

- [x] natural queries lower into the shared `QueryAst` / execution path rather than generated SQL-like text

## Feature Scope By Phase

### Phase 1: MVP `(Done)`

Support:

- [x] `show`
- [x] `where`
- [x] `sort by`
- [x] `limit`
- [x] `offset`
- [x] named parameters like `:minSalary`
- [x] projection aliases via `as`
- [x] strict field validation
- [x] `filter(...)`, `stream(...)`, `bindTyped(...)`, and `explain()`

Example:

- `show name, salary where department is :dept sort by salary descending limit 20`

### Phase 2: Aggregation And Charts `(Done)`

Support:

- [x] `group by`
- [x] `having`
- [x] `count of`, `sum of`, `average of`, `minimum of`, `maximum of`
- [x] chart phrases like `as bar chart`
- [x] time bucket phrases like `bucket hire date by month`

Example:

- `show department, count of employees as total group by department having total at least 5 as bar chart`

### Phase 3: Joins And Multi-source Queries `(Done)`

Support:

- [x] `join`
- [x] explicit source labels
- [x] join conditions in plain wording
- [x] compatibility with `JoinBindings`

Example:

- `from companies join employees on company id equals employee company id show company name where employee title is Engineer`

### Phase 4: Advanced Analytics `(Partially Done)`

Support:

- [x] window functions
- [x] `qualify`
- [ ] computed fields in plain wording
- [ ] reusable templates and presets

This is likely where complexity rises sharply.

## Error Model

The error model must be as important as the parser.

Desired behavior:

- unknown field -> suggest likely known field or alias
- ambiguous term -> list candidate fields
- unsupported phrase -> explain the supported form
- invalid grouped expression -> mirror current grouped validation discipline
- unsupported advanced feature -> point users to SQL-like or fluent

Example:

- input: `show employees where pay is huge`
- response: plain-English query term `huge` is not supported; use `at least`,
  `more than`, or a named parameter like `:minPay`

## Explain Story

This feature needs a strong `explain()` story, otherwise it will be hard to
debug and support.

Recommended explain payload additions:

- original plain-English text
- normalized query interpretation
- resolved field vocabulary mapping
- lowered internal plan
- optional equivalent SQL-like rendering for diagnostics

That gives operators and developers a bridge between the easy surface and the
technical one.

## Caching

This should mirror SQL-like behavior conceptually:

- parse cache for repeated text
- runtime-scoped configuration where needed
- deterministic parse results

This is another reason to avoid unconstrained LLM behavior in the core path.

## Relationship To SQL-like

This feature should not be positioned as "better SQL-like".

Recommended positioning:

- fluent: best for service-owned code
- SQL-like: best for power users and config-driven text queries
- plain-English-like: best for non-SQL users and guided text authoring

Power users should still be able to drop down to SQL-like when the query becomes
too advanced or too exact for the simpler language.

## Relationship To Fluent

Fluent remains the core execution foundation.

That means:

- correctness parity should be tested against fluent behavior
- performance should be judged by parser overhead plus existing engine cost
- the new language should not introduce new execution semantics

## Risks

- over-promising "natural language" and under-delivering deterministic behavior
- field-vocabulary complexity becoming larger than the parser itself
- ambiguity around joins, aggregates, and windows
- too much synonym support causing unstable parsing
- large maintenance burden if the surface tries to mimic conversational English

## Suggested Guardrails

- start with a documented controlled grammar
- reject ambiguous phrases rather than guessing
- keep named parameters identical to SQL-like
- make vocabulary explicit and runtime-configurable
- keep SQL-like as the escape hatch for advanced text queries
- add parity tests against fluent and SQL-like for each supported shape

## Initial Recommendation

Build this as a controlled plain-English query language with a new explicit
entry point and a runtime-scoped vocabulary layer.

Recommended first-cut strategy:

1. choose the public name and grammar philosophy
2. implement a minimal parser for projection, filtering, sorting, and limits
3. compile into the existing engine rather than creating a new runtime path
4. add `explain()` support that shows the normalized interpretation
5. expand to grouping, charts, and joins only after MVP parity is stable

## Open Questions

- [x] Public name: `PojoLensNatural`
- [x] Lowering path: shared normalized planner/execution model, not generated SQL-like text
- [x] Chart phrases landed in Phase 2
- [x] Field vocabulary is runtime-scoped on `PojoLensRuntime`; direct `PojoLensNatural.parse(...)` stays vocabulary-free
- How much synonym support is acceptable before parsing becomes unstable?
- Do we want locale support early, or should v1 be English-only?

## Bottom Line

This idea is strong, but it should be implemented as a controlled,
plain-English-like query language, not as unconstrained natural language.

That gives `pojo-lens` a very clear three-surface story:

- fluent for code-owned queries
- SQL-like for expert text queries
- plain-English-like for accessible text queries

All three should compile to the same underlying engine.
