#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="$(ls target/*-benchmarks.jar | head -n 1)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "Benchmark jar not found. Run: mvn -B -ntp -Pbenchmark-runner -DskipTests package" >&2
  exit 1
fi

java -cp "${JAR_PATH}" laughing.man.commits.benchmark.BenchmarkMetricsPlotGenerator \
  target/benchmarks.json \
  benchmarks/thresholds.json \
  target/benchmarks/charts/chart-benchmarks.json \
  benchmarks/chart-thresholds.json \
  target/benchmarks/charts/images
