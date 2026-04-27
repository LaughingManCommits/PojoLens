# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Run the final `RELEASE.md` guardrails; cut if green, otherwise fix the blocker before WP18-WP19.

## Focus

- `2026-04-27`: The repo-local Checkstyle profile is now clean at `report=0 baseline=0`; release guardrails remain next.
- `2026-04-27`: SpotBugs Java 25 support and the staged Checkstyle baseline script are both restored; release guardrails remain next.
- `2026-04-27`: WP20 landed; WP18 and WP19 remain next after the release cut.
- `2026-04-26`: WP11-WP17 are complete; WP6/WP8/WP9 benchmark backfill stays deferred until after release.

## Facts

- `2026-04-27`: `pojo-lens/pom.xml` now points `-Plint` at `config/checkstyle/checkstyle.xml` instead of stock `sun_checks.xml`, and the repo-local rule set is clean.
- `2026-04-27`: `scripts/checkstyle-baseline.txt` is now intentionally empty because the current `-Plint` report is empty (`report=0 baseline=0 new=0 fixed=0`).
- `2026-04-27`: `scripts/check-lint-baseline.ps1` now handles zero-violation reports when writing the staged baseline.
- `2026-04-27`: `mvn -B -ntp -Pstatic-analysis verify -DskipTests` now passes on Java 25 after upgrading SpotBugs from `4.8.6.6` to `4.9.8.3`.
- `2026-04-27`: `SqlLikeFieldMessages` now owns shared SQL-like unknown-field assembly, and `NaturalQueryResolutionSupport` now reuses one ambiguous/unknown field-term helper pair.
- `2026-04-27`: WP20 targeted message-contract tests passed at 126/126.
- `2026-04-26`: Release benchmark guardrails and the latest `SCATTER size=100000` parity rerun passed at ratio `1.526`.
- `2026-04-26`: The strict no-warmup chart parity row is locally noisy; rerun before treating a lone miss as a regression.
- `2026-04-26`: Local JFR lacks `jdk.CPUTimeSample` on this Windows host, and benchmark commands should use `$env:JAVA_HOME\bin\java.exe`.

## Validate

- After code changes: `mvn -B -ntp test`.
- After lint-baseline refreshes: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`.
- After static-analysis build changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-27` `mvn -B -ntp -Plint verify -DskipTests`, the refreshed baseline check (`0/0`), and full `mvn -B -ntp test` all passed after switching to the repo-local Checkstyle config and fixing the empty-baseline write path.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
