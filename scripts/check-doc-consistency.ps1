$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$pomPath = Join-Path $root "pom.xml"
$contributingPath = Join-Path $root "CONTRIBUTING.md"
$migrationPath = Join-Path $root "MIGRATION.md"
$releasePath = Join-Path $root "RELEASE.md"
$sqlLikePath = Join-Path $root "docs/sql-like.md"
$benchmarkingPath = Join-Path $root "docs/benchmarking.md"
$benchmarkMainArgsPath = Join-Path $root "scripts/benchmark-suite-main.args"

function Require-File([string]$path) {
    if (-not (Test-Path $path)) {
        throw "[doc-check] Missing required file: $path"
    }
    return Get-Content -Raw -Path $path
}

function Add-Error([System.Collections.Generic.List[string]]$errors, [string]$message) {
    $errors.Add($message)
}

function Require-Substring([string]$doc, [string]$name, [string]$needle, [System.Collections.Generic.List[string]]$errors) {
    if (-not $doc.Contains($needle)) {
        Add-Error $errors "${name}: missing required text: $needle"
    }
}

function Require-Pattern([string]$doc, [string]$name, [string]$pattern, [System.Collections.Generic.List[string]]$errors) {
    if (-not [regex]::IsMatch($doc, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        Add-Error $errors "${name}: missing required pattern: $pattern"
    }
}

function Forbid-Pattern([string]$doc, [string]$name, [string]$pattern, [System.Collections.Generic.List[string]]$errors) {
    if ([regex]::IsMatch($doc, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        Add-Error $errors "${name}: contains forbidden pattern: $pattern"
    }
}

[xml]$pom = Get-Content -Raw -Path $pomPath
$projectVersion = $pom.project.version
$contributing = Require-File $contributingPath
$migration = Require-File $migrationPath
$release = Require-File $releasePath
$sqlLike = Require-File $sqlLikePath
$benchmarking = Require-File $benchmarkingPath
$benchmarkMainArgs = Require-File $benchmarkMainArgsPath
$errors = [System.Collections.Generic.List[string]]::new()

# Benchmark command drift should use dynamic jar resolution in process docs.
Require-Pattern $contributing "CONTRIBUTING.md" 'BENCHMARK_JAR=.*\*-benchmarks\.jar' $errors
Require-Pattern $release "RELEASE.md" 'target/\*-benchmarks\.jar' $errors
Forbid-Pattern $contributing "CONTRIBUTING.md" 'pojo-lens-\d+\.\d+\.\d+-benchmarks\.jar' $errors
Forbid-Pattern $release "RELEASE.md" 'Release\s+\d+\.\d+\.\d+|v\d+\.\d+\.\d+' $errors

# SQL-like capability drift checks.
Require-Pattern $migration "MIGRATION.md" 'supports uncorrelated single-column .*IN \(select .*subqueries' $errors
Require-Pattern $migration "MIGRATION.md" 'chained joins are supported' $errors
Require-Pattern $release "RELEASE.md" 'supports uncorrelated single-column .*IN \(select .*subqueries' $errors
Require-Pattern $release "RELEASE.md" 'chained joins are supported' $errors
Forbid-Pattern $migration "MIGRATION.md" 'does not support subqueries or multi-join SQL plans' $errors
Forbid-Pattern $release "RELEASE.md" 'unsupported:\s*subqueries,\s*multi-join SQL plans' $errors

# Source docs still define the canonical behavior.
Require-Substring $sqlLike "docs/sql-like.md" 'SQL-like subqueries currently support only uncorrelated single-column `WHERE <field> IN (select ...)` subqueries.' $errors
Require-Substring $sqlLike "docs/sql-like.md" 'chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the source explicitly' $errors

# Benchmark guide and suite should still cover the guarded benchmark path.
Require-Substring $benchmarkMainArgs "scripts/benchmark-suite-main.args" "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "BenchmarkThresholdChecker" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "benchmarks/chart-thresholds.json" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "target/pojo-lens-$projectVersion-benchmarks.jar" $errors

if ($errors.Count -gt 0) {
    Write-Host "[doc-check] FAILED"
    foreach ($e in $errors) {
        Write-Host "- $e"
    }
    exit 1
}

Write-Host "[doc-check] OK: documentation invariants satisfied."
