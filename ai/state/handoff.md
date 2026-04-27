# Handoff

## Resume
1. Load hot context files.
2. Check `git status --short`.
3. Cut the release from `RELEASE.md` when the user wants it; otherwise continue with WP18.

## Focus
- `2026-04-27`: `FluentWindowSupport` now defers `RawQueryRow` wrapping until window values are populated.
- `2026-04-27`: `pojo-lens/pom.xml` still uses SpotBugs `check` in `-Pstatic-analysis` with `failThreshold=High`, and the gate is clean.
- `2026-04-27`: Release cut remains user-controlled; WP18 is next after release.

## Facts
- `2026-04-27`: `-Plint` points at `config/checkstyle/checkstyle.xml`.
- `2026-04-27`: `-Pstatic-analysis verify -DskipTests` passes cleanly on Java 25.
- `2026-04-27`: `scripts/checkstyle-baseline.txt` is intentionally empty because the lint report is clean.

## Validate
- After code changes: `mvn -B -ntp test`.
- After lint changes: `mvn -B -ntp -Plint verify -DskipTests`.
- After static-analysis changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.

## Cold Pointers
- `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- `README.md`, `docs/**` for docs work
- `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md` for release and benchmarks
