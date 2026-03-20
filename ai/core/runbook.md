# Runbook

## Validation Commands

- tests: `mvn -B -ntp test`
- docs consistency: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-doc-consistency.ps1`
- lint: `mvn -B -ntp -Plint verify -DskipTests`
- lint baseline gate: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-lint-baseline.ps1 -Report target\checkstyle-result.xml -Baseline scripts\checkstyle-baseline.txt -RepoRoot .`
- static analysis: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`

## Benchmark Flow

1. Build runner: `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
2. Resolve `target/*-benchmarks.jar` dynamically.
3. Run suite args from `scripts/benchmark-suite-*.args`.
4. Check thresholds with `benchmarks/thresholds.json` or `benchmarks/chart-thresholds.json`.

## Release Flow (Maven Central)

1. Ensure required GitHub secrets are set:
   `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.
2. Ensure PGP public key is available on supported keyservers.
3. Trigger `.github/workflows/release.yml` by:
   - pushing tag `vX.Y.Z` (must match `pom.xml` version), or
   - manual `workflow_dispatch`.
4. Workflow runs tests then `mvn -B -ntp -Prelease-central -DskipTests deploy`.
