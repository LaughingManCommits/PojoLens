# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Follow the dependency-ordered roadmap in `TODO.md`: WP21 -> WP25 -> WP22 -> WP23 -> WP24 -> WP18.
4. Treat `Release Gate` as the final roadmap package and cut from `RELEASE.md` when the user wants it.

## Focus
- `2026-04-27`: `FluentWindowSupport` now defers `RawQueryRow` wrapping until window values are populated.
- `2026-04-27`: `pojo-lens/pom.xml` still uses SpotBugs `check` in `-Pstatic-analysis` with `failThreshold=High`, and the gate is clean.
- `2026-04-27`: Window snapshots now reuse source types without losing computed-field-aware names on materialized rows; the rerun window suite stayed green after that optimization.
- `2026-04-29`: Release cut remains user-controlled and now sits as the final roadmap package after WP26.
- `2026-04-29`: WP26 wrapper guidance landed: `ReportDefinition` / `SavedReport` are the default reusable-contract story, while chart/stats presets remain advanced convenience sugar.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-27`: `scripts/checkstyle-baseline.txt` is intentionally empty because the lint report is clean.
- `2026-04-27`: The latest window review run used `size=10000` with `-wi 1 -i 5 -r 200ms` and landed at `0.623 ms/op` baseline, `1.825 ms/op` rank, and `1.843 ms/op` running total.
- `2026-04-29`: `TODO.md` is now ordered by dependency: WP21, WP25, WP22, WP23, WP24, WP18, then `Release Gate`.
- `2026-04-29`: WP26 completed as guidance-first collapse: no wrapper deprecations, default reusable contracts are `ReportDefinition` / `SavedReport`, and preset wrappers are advanced convenience only.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
