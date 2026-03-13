# Contributing

## Local Validation

Run from repository root:

```bash
mvn -B -ntp test
```

Lint gate (staged, fail on new violations only):

```bash
mvn -B -ntp -Plint verify -DskipTests
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .
```

Refresh lint baseline intentionally (only when doing a planned lint cleanup/rebaseline):

```bash
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline
```

Static analysis report (SpotBugs, non-blocking rollout stage):

```bash
mvn -B -ntp -Pstatic-analysis verify -DskipTests
```

Benchmark guardrail flow (release-quality check):

```bash
mvn -B -ntp -Pbenchmark-runner -DskipTests package
BENCHMARK_JAR="$(find target -maxdepth 1 -type f -name '*-benchmarks.jar' | head -n 1)"
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks.json
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks.json benchmarks/thresholds.json target/benchmark-report.csv --strict
```

Chart benchmark/parity flow:

```bash
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/charts/chart-benchmarks.json
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/chart-benchmark-report.csv --strict
java -cp "$BENCHMARK_JAR" laughing.man.commits.benchmark.ChartParityChecker target/benchmarks/charts/chart-benchmarks.json target/benchmarks/charts/chart-parity-report.csv
```

Cache concurrency benchmark flow (parse/plan hot set):

```bash
java -jar "$BENCHMARK_JAR" @scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks-cache.json
```

## Benchmark Reproducibility

Keep deterministic benchmark profile constants in `BenchmarkProfiles` unchanged unless intentionally re-baselining thresholds:

- `profile=deterministic-v1`
- `seed=20260301`
- fixed epoch baselines (`1700000000000`, `1735689600000`)

