# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP26.
4. Treat `Release Gate` as last and cut from `RELEASE.md` when requested.

## Focus
- `2026-04-27`: Window execution now computes values before `RawQueryRow` wrapping, and the Java 25 static-analysis gate is clean.
- `2026-04-29`: Surface/tooling cleanup packages are complete: wrapper guidance is `ReportDefinition`/`SavedReport` first, docs are authoring-first with dedicated SQL-like, natural, and typed guides, `PojoLensFiles` owns CSV/TSV/JSON/JSONL with Excel as a non-goal, and build tooling is split between `metamodel` generation and `tooling` validation.
- `2026-04-29`: WP23 is complete. `TypedQuery` now covers joins, grouped aggregates, totals-style metrics, aggregate-output ordering, and `JoinBindings` / `DatasetBundle` filter, explain, and schema overloads on one typed surface.
- `2026-04-29`: WP24 is complete. `TypedQuery`/`TypedPredicate` now cover grouped `HAVING`, bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries, rank windows, aggregate window frames via `QueryWindowFrame`, and `QUALIFY` on the same immutable typed surface.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-29`: `TODO.md` order is WP26, then WP18, then Release Gate.
- `2026-04-29`: Current release/tag is `2026.04.29.1809` (`release-2026.04.29.1809`).
- `2026-04-29`: WP23 outcome: typed joins reuse `JoinBindings` and `DatasetBundle`; grouped queries stay on `TypedQuery` via `groupBy(...)`, `count(...)`, and `metric(...)`; aggregate aliases drive `orderBy(...)` / `orderByDesc(...)`; parity includes joined-grouped SQL-like coverage.
- `2026-04-29`: WP24 outcome: typed `having(...)` only accepts grouped-output predicates; windows reuse `window(...)` / `windowCountAll(...)` plus `TypedWindowOrder`; frames reuse `QueryWindowFrame`; bounded `IN` / `EXISTS` / `NOT EXISTS` reuse the grouped predicate path; correlated/scalar subqueries stay text-only by design.
- `2026-04-29`: CI sweep passed after removing an unused `CsvLoaderSupport` import; lint, static-analysis, docs, runtime jar, binary compat, cache stress, chart interop, and benchmarks are green.
- `2026-04-29`: `README.md` stays route-based and now includes a top `Quick Integration` section that points AI agents to `AGENTS.md`; do not reintroduce separate product-taxonomy sections there.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
