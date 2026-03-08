$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$readmePath = Join-Path $root "README.md"
$migrationPath = Join-Path $root "MIGRATION.md"
$releasePath = Join-Path $root "RELEASE.md"
$sqlLikePath = Join-Path $root "docs/sql-like.md"
$chartsPath = Join-Path $root "docs/charts.md"
$benchmarkingPath = Join-Path $root "docs/benchmarking.md"
$benchmarkMainArgsPath = Join-Path $root "scripts/benchmark-suite-main.args"

function Require-File([string]$path) {
    if (-not (Test-Path $path)) {
        throw "[doc-check] Missing required file: $path"
    }
    return Get-Content -Raw -Path $path
}

function Require-Substring([string]$doc, [string]$name, [string]$needle, [System.Collections.Generic.List[string]]$errors) {
    if (-not $doc.Contains($needle)) {
        $errors.Add("${name}: missing required text: $needle")
    }
}

function Require-Pattern([string]$doc, [string]$name, [string]$pattern, [System.Collections.Generic.List[string]]$errors) {
    if (-not [regex]::IsMatch($doc, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $errors.Add("${name}: missing required pattern: $pattern")
    }
}

$readme = Require-File $readmePath
$migration = Require-File $migrationPath
$release = Require-File $releasePath
$sqlLike = Require-File $sqlLikePath
$charts = Require-File $chartsPath
$benchmarking = Require-File $benchmarkingPath
$benchmarkMainArgs = Require-File $benchmarkMainArgsPath
$errors = [System.Collections.Generic.List[string]]::new()

# HAVING boolean support invariants.
Require-Pattern $migration "MIGRATION.md" "HAVING.*AND.*/.OR|HAVING.*AND.*OR" $errors
Require-Pattern $release "RELEASE.md" "HAVING.*Boolean support:.*AND.*OR" $errors
Require-Pattern $sqlLike "docs/sql-like.md" "Boolean support.*AND.*OR|AND.*OR.*supported" $errors

# Chart type/policy invariants.
foreach ($chartType in @("BAR", "LINE", "PIE", "AREA", "SCATTER")) {
    Require-Substring $charts "docs/charts.md" "- ``$chartType``" $errors
    Require-Substring $migration "MIGRATION.md" "``$chartType``" $errors
}
Require-Pattern $charts "docs/charts.md" "does not ship chart rendering|will not implement native image/chart rendering|no internal renderer" $errors
Require-Pattern $migration "MIGRATION.md" "will not implement native image/chart rendering|not implement native image/chart rendering" $errors
Require-Pattern $charts "docs/charts.md" "percentStacked.*requires.*stacked=true" $errors
Require-Pattern $migration "MIGRATION.md" "percentStacked.*requires.*stacked=true" $errors

# Benchmark command coverage invariants.
Require-Substring $benchmarkMainArgs "scripts/benchmark-suite-main.args" "SqlLikePipelineJmhBenchmark.parseAndFilterBooleanDepth" $errors
Require-Substring $benchmarkMainArgs "scripts/benchmark-suite-main.args" "SqlLikePipelineJmhBenchmark.parseAndFilterHavingComputed" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "BenchmarkMetricsPlotGenerator" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "benchmarks/chart-thresholds.json" $errors
Require-Pattern $benchmarking "docs/benchmarking.md" "BenchmarkThresholdChecker.*--strict" $errors
Require-Pattern $benchmarking "docs/benchmarking.md" "CI Gates|CI gates" $errors

# README should link to split docs.
Require-Substring $readme "README.md" "docs/sql-like.md" $errors
Require-Substring $readme "README.md" "docs/charts.md" $errors
Require-Substring $readme "README.md" "docs/benchmarking.md" $errors

if ($errors.Count -gt 0) {
    Write-Host "[doc-check] FAILED"
    foreach ($e in $errors) {
        Write-Host "- $e"
    }
    exit 1
}

Write-Host "[doc-check] OK: README/MIGRATION/RELEASE invariants satisfied."
