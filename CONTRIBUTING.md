# Contributing

## Local Validation

Run from repository root:

```bash
mvn -B -ntp test
```

## Lint Gate

Generate lint report:

```bash
mvn -B -ntp -Plint verify -DskipTests
```

Check staged lint baseline (fails only on new violations or stale resolved entries):

```bash
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .
```

Intentionally refresh baseline (planned rebaseline only):

```bash
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline
```

## Static Analysis

SpotBugs report (non-blocking rollout stage):

```bash
mvn -B -ntp -Pstatic-analysis verify -DskipTests
```

## Benchmark Guardrails

Build benchmark runner:

```bash
mvn -B -ntp -Pbenchmark-runner -DskipTests package
```

Resolve benchmark jar (Bash):

```bash
BENCHMARK_JAR="$(find target -maxdepth 1 -type f -name '*-benchmarks.jar' | head -n 1)"
```

Resolve benchmark jar (PowerShell):

```powershell
$BENCHMARK_JAR = (Get-ChildItem target -Filter *-benchmarks.jar | Select-Object -First 1).FullName
```

Core benchmark strict check:

```bash
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks.json
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks.json benchmarks/thresholds.json target/benchmark-report.csv --strict
```

Chart benchmark strict + parity checks:

```bash
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/charts/chart-benchmarks.json
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/chart-benchmark-report.csv --strict
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.ChartParityChecker target/benchmarks/charts/chart-benchmarks.json target/benchmarks/charts/chart-parity-report.csv
```

Cache concurrency benchmark flow:

```bash
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks-cache.json
```

## Benchmark Reproducibility

Keep deterministic profile constants in `BenchmarkProfiles` unchanged unless intentionally re-baselining thresholds:

- `profile=deterministic-v1`
- `seed=20260301`
- fixed epoch baselines (`1700000000000`, `1735689600000`)
