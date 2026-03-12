# Runbook

## Local Validation

- Verified: Main test command: `mvn -B -ntp test`
- Verified: Doc consistency script: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-doc-consistency.ps1`
- Verified: Lint profile: `mvn -B -ntp -Plint verify -DskipTests`
- Verified: Lint baseline enforcement: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-lint-baseline.ps1 -Report target\checkstyle-result.xml -Baseline scripts\checkstyle-baseline.txt -RepoRoot .`
- Verified: Static analysis profile: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`

## Benchmark Flow

1. Verified: Build benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
2. Verified: Resolve the real benchmark jar from `target\*-benchmarks.jar` instead of hardcoding a versioned filename.
3. Verified: Core benchmark suite uses `@scripts/benchmark-suite-main.args`
4. Verified: Chart benchmark suite uses `@scripts/benchmark-suite-chart.args`
5. Verified: Hotspot suite uses `@scripts/benchmark-suite-hotspots.args`
6. Verified: Streams baseline suite uses `@scripts/benchmark-suite-baseline.args`
7. Verified: Threshold checks use `benchmarks/thresholds.json` and `benchmarks/chart-thresholds.json`

## CI Summary

- Verified: `.github/workflows/ci.yml` runs `lint-workflows`, `test`, `lint-java`, `static-analysis`, `doc-consistency`, `benchmark-guardrail`, `cache-concurrency-stress`, `chart-artifacts`, and `benchmark-plot-artifacts`.
- Verified: CI resolves benchmark jar paths dynamically instead of assuming a fixed versioned filename.

## No Service Startup Or Deploy Runbook

- Verified: This repo builds a library jar only.
- Verified: No app entry point, Dockerfile, Compose file, Kubernetes manifest, or Terraform config was found.
