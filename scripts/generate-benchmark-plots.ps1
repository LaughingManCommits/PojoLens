$ErrorActionPreference = "Stop"

$jar = Get-ChildItem "target/*-benchmarks.jar" | Select-Object -First 1
if (-not $jar) {
    throw "Benchmark jar not found. Run: mvn -B -ntp -Pbenchmark-runner -DskipTests package"
}

java -cp $jar.FullName laughing.man.commits.benchmark.BenchmarkMetricsPlotGenerator `
  target/benchmarks.json `
  benchmarks/thresholds.json `
  target/benchmarks/charts/chart-benchmarks.json `
  benchmarks/chart-thresholds.json `
  target/benchmarks/charts/images

