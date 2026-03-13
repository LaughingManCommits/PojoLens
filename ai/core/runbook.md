# Runbook

Validation commands:

- tests: `mvn -B -ntp test`
- docs check: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-doc-consistency.ps1`
- lint: `mvn -B -ntp -Plint verify -DskipTests`
- lint baseline: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-lint-baseline.ps1 -Report target\checkstyle-result.xml -Baseline scripts\checkstyle-baseline.txt -RepoRoot .`
- static analysis: `mvn -B -ntp -Pstatic-analysis verify -DskipTests`

Benchmark flow:

1. build the runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
2. resolve `target/*-benchmarks.jar` dynamically
3. run suites from `@scripts/benchmark-suite-main.args`, `@scripts/benchmark-suite-chart.args`, `@scripts/benchmark-suite-hotspots.args`, or `@scripts/benchmark-suite-baseline.args`
4. check thresholds with `benchmarks/thresholds.json` or `benchmarks/chart-thresholds.json`

CI:

- `.github/workflows/ci.yml` runs workflow lint, tests, Java lint, SpotBugs, doc consistency, benchmark guardrails, cache stress, and chart artifact jobs

No deploy runbook exists because the repository produces a library jar only.
