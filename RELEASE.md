# Release Guide

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

## 3) Benchmark Guardrail Evidence

Run benchmark and parity checks from `docs/benchmarking.md` and archive produced JSON/CSV outputs as release evidence.

Minimum required checks:
- strict core thresholds (`benchmarks/thresholds.json`)
- strict chart thresholds (`benchmarks/chart-thresholds.json`)
- chart parity check
- benchmark metrics plot generation

When building the benchmark runner locally, resolve the real jar from `target/*-benchmarks.jar` instead of hardcoding a versioned filename.

## 4) Maven Central Namespace and Coordinates

Current release coordinates:
- `groupId`: `io.github.laughingmancommits`
- `artifactId`: `pojo-lens`

Namespace ownership must be verified in Sonatype Central before publishing.

If using GitHub namespace verification:
- use namespace `io.github.<github-username>`
- complete verification in Central Portal (code-hosting verification flow)
- keep coordinates in `pom.xml` aligned with verified namespace

## 5) Publish to Maven Central

After namespace verification and publish credentials are configured in `~/.m2/settings.xml`:

```bash
mvn -B -ntp clean deploy -DskipTests
```

Then finalize publication in Central Portal if not using auto-publish.

## 6) Git Tag and Push

```bash
git add .
git commit -m "Release <version>"
git tag -a v<version> -m "PojoLens v<version>"
git push
git push origin v<version>
```

## 7) Release Checklist

- [ ] Full validation passed (`test`, lint baseline gate, benchmark guardrails).
- [ ] Coordinates and namespace are verified and aligned with `pom.xml`.
- [ ] Docs are synced (`README`, `MIGRATION`, `RELEASE`, `docs/*.md`).
- [ ] SQL-like limitations text is still accurate: supports limited `WHERE ... IN (select oneField ...)` subqueries.
- [ ] SQL-like join capability text is still accurate: chained joins are supported when each `JOIN ... ON ...` references the current plan correctly.
- [ ] CI/workflow changes are intentional.
- [ ] Release tag pushed and visible.
