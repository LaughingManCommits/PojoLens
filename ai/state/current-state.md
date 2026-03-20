# Current State

## Repository Health

- Repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- Branch is `main`; release-coordinate and documentation refresh updates are now staged in the working tree after `b074644`.
- `TODO.md` currently reports: `No active work items.`
- Latest local validation on `2026-03-20`: `mvn -q test` passed with `488` tests.
- Java lint baseline was refreshed on `2026-03-20` to `11,513` entries in `scripts/checkstyle-baseline.txt`; baseline gate now passes with `new=0` and `fixed=0`.
- Core benchmark thresholds were rebaselined from CI on `2026-03-19` using `ceil(score * 1.5, 0.1ms)` and are still the active guardrail source in `benchmarks/thresholds.json`.

## Latest Landed Work (2026-03-20)

- Benchmark sources and docs were updated in `b074644` to execution-only methodology:
  - benchmark query/setup construction now happens in `@Setup`
  - `@Benchmark` methods measure execution (`filter`, `filterGroups`, `join`, `chart`, etc.) rather than setup + execution together
- `docs/benchmarking.md` now documents this methodology explicitly and updates representative strict-suite context.
- Existing warmed `-prof gc` computed-join comparison numbers in docs are marked as old-methodology values and should be refreshed before reuse as profiling baselines.
- Release coordinates were updated to GitHub namespace style for Central publishing prep:
  - Maven `groupId` changed from `laughing.man.commits` to `io.github.laughingmancommits` in `pom.xml`
  - README dependency snippet now matches the new `groupId`
  - `ai/core/repo-purpose.md` was aligned with the new coordinate
- Documentation refresh landed for release-readiness and readability:
  - `README.md` was rewritten for clearer onboarding (installation, quick starts, capability map, docs map)
  - `CONTRIBUTING.md` was reorganized with clearer lint/benchmark flows and Bash/PowerShell jar-resolution guidance
  - `RELEASE.md` now includes current Central namespace/publish guidance while preserving required benchmark/subquery/join invariants
  - `MIGRATION.md` now records the coordinate change and fixes malformed explicit-rule-group examples
  - `scripts/check-doc-consistency.ps1` now passes on the updated docs set
- Release automation prep now exists in the working tree:
  - `pom.xml` now includes required Central metadata (`name`, `description`, `url`, `licenses`, `developers`, `scm`)
  - new `release-central` Maven profile now attaches sources/javadocs, signs via GPG, and publishes through `central-publishing-maven-plugin` (`0.10.0`, `autoPublish=true`, `waitUntil=published`)
  - new GitHub Actions workflow `.github/workflows/release.yml` now supports tag (`v*`) or manual release with Central token + GPG secrets and enforces tag-to-POM-version parity before deploy
  - new helper script `scripts/export-release-secrets.ps1` now exports GPG private/public keys and writes a GitHub-secrets template/next-steps bundle under `target/release-secrets`
  - local validation for this setup passed: `mvn -B -ntp -Prelease-central -DskipTests package`
  - first CI deploy attempt showed `central-publishing` `401` because `${env.CENTRAL_TOKEN_USERNAME}` was passed literally; workflow now sets Central token env vars at job scope so Maven resolves `${env.*}` placeholders during the deploy step
  - `pom.xml` no longer sets deprecated `maven-gpg-plugin` `passphrase` config; workflow now supplies `MAVEN_GPG_PASSPHRASE` environment variable instead

## Active Work

- No active implementation work is currently in progress.

## Next Tasks

- If performance work resumes, keep WP19 parked unless there is a materially new structural hypothesis.
- Reopen WP18 only with fresh scatter/chart profiling that isolates a chart-specific bottleneck.
- Refresh warmed `-prof gc` computed-join baselines under the new execution-only methodology before using those values for profiling guidance.
- After any code change, rerun focused regressions plus `mvn -q test`.

## Current Risks

- Hot memory previously referenced uncommitted 2026-03-19 follow-ups; those are now committed and should no longer be treated as pending.
- Benchmark interpretation can drift if old setup-bundled and new execution-only numbers are mixed in the same decision.
