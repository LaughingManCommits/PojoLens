# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP18.
4. Treat `Release Gate` as the final roadmap package and cut from `RELEASE.md` when the user wants it.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 static-analysis gate is clean.
- `2026-04-29`: Surface/tooling cleanup packages are complete: wrapper guidance is `ReportDefinition`/`SavedReport` first, docs are authoring-first, `PojoLensFiles` owns CSV/TSV/JSON/JSONL with Excel as a non-goal, and build tooling is split between `metamodel` generation and `tooling` validation.
- `2026-04-29`: WP23 is complete. `TypedQuery` now covers joins, grouped aggregates, totals-style metrics, aggregate-output ordering, and `JoinBindings` / `DatasetBundle` filter, explain, and schema overloads on one typed surface.
- `2026-04-29`: WP24 is complete. `TypedQuery`/`TypedPredicate` now cover grouped `HAVING`, bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries, rank windows, aggregate window frames via `QueryWindowFrame`, and `QUALIFY` on the same immutable typed surface.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-29`: `TODO.md` order is WP18, then `Release Gate`.
- `2026-04-29`: WP23 outcome: typed joins reuse `JoinBindings` and `DatasetBundle`, grouped queries reuse the same `TypedQuery` surface via `groupBy(...)`, `count(...)`, and `metric(...)`, aggregate aliases can drive `orderBy(...)` / `orderByDesc(...)`, and parity coverage now includes joined-grouped execution against equivalent SQL-like queries.
- `2026-04-29`: WP24 outcome: typed `having(...)` accepts grouped-output predicates only, typed windows reuse `window(...)` / `windowCountAll(...)` plus `TypedWindowOrder`, aggregate frames reuse `QueryWindowFrame`, bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries lower through the existing grouped predicate path, and only correlated/scalar subqueries plus broader user-authored text flows remain text-surface-only by design.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
