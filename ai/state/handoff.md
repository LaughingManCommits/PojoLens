# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Run the final `RELEASE.md` guardrails; cut if green, otherwise fix the blocker before WP18-WP19.

## Focus

- `2026-04-27`: Java 25 static-analysis compatibility is restored after upgrading `spotbugs-maven-plugin` to `4.9.8.3`; release guardrails remain next.
- `2026-04-27`: WP20 landed, and the remaining post-release backlog is now WP18 plus WP19.
- `2026-04-26`: WP17 landed. Internal Java 25 cleanup replaced repetitive utility/cursor dispatch with non-preview switch expressions and refreshed targeted regressions.
- `2026-04-26`: Release Gate is down to final guardrails.
- `2026-04-26`: WP6/WP8/WP9 benchmark-backfill tasks remain deferred until after the release cut.
- `2026-04-26`: WP11-WP17 are complete.
- `2026-04-25`: Java 25 upgrade is complete across the Maven build and CI workflows.

## Facts

- `2026-04-27`: `mvn -B -ntp -Pstatic-analysis verify -DskipTests` now passes again on Java 25 after upgrading `spotbugs-maven-plugin` from `4.8.6.6` to `4.9.8.3`.
- `2026-04-27`: The Checkstyle profile still carries a large baseline-backed style backlog; this slice intentionally left it unchanged.
- `2026-04-27`: `SqlLikeFieldMessages` now owns shared SQL-like unknown-field assembly, and `NaturalQueryResolutionSupport` now reuses one ambiguous/unknown field-term helper pair.
- `2026-04-27`: WP20 targeted message-contract tests passed at 126/126.
- `2026-04-27`: `mvn -B -ntp test` passed at 1077/1077 after WP20.
- `2026-04-26`: WP17 moved the remaining internal dispatch sites to non-preview switch-based logic and kept targeted regressions green.
- `2026-04-26`: Release benchmark guardrails and the latest `SCATTER size=100000` parity rerun passed at ratio `1.526`.
- `2026-04-26`: The strict no-warmup chart parity row is locally noisy; rerun before treating a lone miss as a regression.
- `2026-04-26`: Local JFR lacks `jdk.CPUTimeSample` on this Windows host, and benchmark commands should use `$env:JAVA_HOME\bin\java.exe`.

## Validate

- After code changes: `mvn -B -ntp test`.
- After static-analysis build changes: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-27` `mvn -B -ntp -Pstatic-analysis verify -DskipTests` and `mvn -B -ntp test` both passed after the SpotBugs Java 25 compatibility fix.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
