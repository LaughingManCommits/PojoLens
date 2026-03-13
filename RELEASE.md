# Release Guide

## 1) Verify

```bash
mvn -B -ntp test
```

## 2) Review release docs

Confirm these are aligned:
- `README.md`
- `MIGRATION.md`
- `RELEASE.md`
- `docs/sql-like.md`
- `docs/charts.md`
- `docs/benchmarking.md`

## 3) Benchmark guard

Run all benchmark and parity commands from `docs/benchmarking.md` and attach produced CSV/JSON artifacts to the release evidence.

When building the benchmark runner locally, resolve the real jar from `target/*-benchmarks.jar` instead of hardcoding a versioned filename.

Minimum required checks:
- strict threshold check against `benchmarks/thresholds.json`
- chart threshold + parity checks against `benchmarks/chart-thresholds.json`
- benchmark metric plot generation

### 3.0) Reproducibility profile

Benchmark inputs use deterministic profile constants from `BenchmarkProfiles`:
- `profile=deterministic-v1`
- `seed=20260301`
- fixed epoch baselines (`1700000000000`, `1735689600000`)

Keep this profile unchanged for CI/local comparability unless intentionally re-baselining thresholds.

## 3.1) Backward-Compatibility Policy (Current Phase)

This project is still in a rapid-evolution phase. Compatibility policy:
- `QueryBuilder` interface methods are the supported user-facing Java API.
- Concrete builder internals (`FilterQueryBuilder` helper/state methods) may change between minor releases.
- SQL-like grammar is additive where possible, but strict validation rules may tighten.
- Breaking changes must be documented in `MIGRATION.md`.

## 3.2) Documented Limitations Check

Before release, confirm limitations docs match implementation:
- SQL-like supports limited `WHERE ... IN (select oneField ...)` subqueries.
- SQL-like chained joins are supported when each `JOIN ... ON ...` references the current plan correctly.
- SQL-like unsupported: aggregate, grouped, or joined subquery plans.
- SQL-like `HAVING` supports boolean predicates (`AND` and `OR`).
- Aggregate-query constraints and grouped field aliasing restrictions.
- Time bucket date-type requirement.
- Builder mutability/threading contract.

## 3.3) Release Note Items (Current)

- HAVING support now has fluent + SQL-like parity:
  - Fluent API: `addHaving(...)`
  - SQL-like: `... GROUP BY ... HAVING ...`
- HAVING v1 scope:
  - Allowed references: grouped fields, aggregate aliases, aggregate expressions
  - Boolean support: `AND` and `OR`
- Grouped aggregate ordering uses typed comparison (numeric/date/boolean aware).

## 4) Commit and tag

```bash
git add .
git commit -m "Release <version>"
git tag -a v<version> -m "PojoLens v<version>"
```

## 5) Push

```bash
git push
git push origin v<version>
```

## 6) Cleanup checklist

- [ ] Run full validation (`mvn -B -ntp test` and benchmark checks from `docs/benchmarking.md`).
- [ ] Confirm docs are in sync (`README.md`, `MIGRATION.md`, `RELEASE.md`, `docs/*.md`).
- [ ] Confirm CI workflow updates are intentional (`.github/workflows/ci.yml`).
- [ ] Remove stale comments/terminology and dead code paths found during release prep.

