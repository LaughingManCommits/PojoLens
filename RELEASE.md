# Release Guide

## Versioning

PojoLens uses date-based releases.

- Maven version: `YYYY.MM.DD.HHmm`
- Git tag: `release-<version>`

Example:
- `pom.xml`: `2026.03.28.1919`
- git tag: `release-2026.03.28.1919`

## 1) Pre-Release Validation

```bash
mvn -B -ntp test
mvn -B -ntp -Plint verify -DskipTests
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .
```

## 2) Documentation Alignment

Before tagging, confirm these are current and mutually aligned:
- `README.md`
- `MIGRATION.md`
- `RELEASE.md`
- `docs/sql-like.md`
- `docs/charts.md`
- `docs/benchmarking.md`
- `docs/public-api-stability.md`

## 3) Benchmark Guardrail Evidence

Run benchmark and parity checks from `docs/benchmarking.md` and archive produced
JSON/CSV outputs as release evidence.

Minimum required checks:
- strict core thresholds (`benchmarks/thresholds.json`)
- strict chart thresholds (`benchmarks/chart-thresholds.json`)
- chart parity check
- benchmark metrics plot generation

When building the benchmark runner locally, resolve the real jar from
`target/*-benchmarks.jar` instead of hardcoding a versioned filename.

## 4) Maven Central Namespace and Coordinates

Current release coordinates:
- `groupId`: `io.github.laughingmancommits`
- `artifactId`: `pojo-lens`
- `artifactId`: `pojo-lens-spring-boot-autoconfigure`
- `artifactId`: `pojo-lens-spring-boot-starter`

Namespace ownership must be verified in Sonatype Central before publishing.

If using GitHub namespace verification:
- use namespace `io.github.<github-username>`
- complete verification in Central Portal (code-hosting verification flow)
- keep coordinates in `pom.xml` aligned with verified namespace

## 5) Publish to Maven Central

After namespace verification and publish credentials are configured in
`~/.m2/settings.xml`:

```bash
mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central clean deploy -DskipTests
```

The `release-central` profile attaches source/javadoc jars, signs artifacts,
and publishes via the Central plugin.
Benchmark and example modules remain non-published.

### 5.1) Pipeline Release (GitHub Actions)

Release workflow: `.github/workflows/release.yml`

Triggers:
- tag push `release-*`
- manual `workflow_dispatch`

Required repository secrets:
- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`

Optional helper for generating `GPG_PRIVATE_KEY` export + template files:

```powershell
./scripts/export-release-secrets.ps1 -ListKeys
./scripts/export-release-secrets.ps1 -KeyId <KEY_ID>
```

The release job validates `release-<version>` vs `pom.xml` version, runs tests,
and executes:

```bash
mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests deploy
```

The Central plugin is configured with `autoPublish=true` and
`waitUntil=published`, so successful pipeline runs should complete publication
without manual portal confirmation.

## 6) Git Tag and Push

```bash
git add .
git commit -m "Release <version>"
git tag -a release-<version> -m "PojoLens release <version>"
git push
git push origin release-<version>
```

## 7) Release Checklist

- [ ] Full validation passed (`test`, lint baseline gate, benchmark guardrails).
- [ ] Coordinates and namespace are verified and aligned with `pom.xml`.
- [ ] Docs are synced (`README`, `MIGRATION`, `RELEASE`, `docs/*.md`).
- [ ] If this is the first public `release-*` tag, binary compatibility
      enforcement is now expected to start from `release-*` baselines in CI.
- [ ] SQL-like limitations text is still accurate: supports limited `WHERE ... IN (select oneField ...)` subqueries.
- [ ] SQL-like join capability text is still accurate: chained joins are supported when each `JOIN ... ON ...` references the current plan correctly.
- [ ] CI/workflow changes are intentional.
- [ ] Release tag pushed and visible.
