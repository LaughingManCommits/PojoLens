$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$pomPath = Join-Path $root "pom.xml"
$readmePath = Join-Path $root "README.md"
$contributingPath = Join-Path $root "CONTRIBUTING.md"
$changelogPath = Join-Path $root "CHANGELOG.md"
$migrationPath = Join-Path $root "MIGRATION.md"
$releasePath = Join-Path $root "RELEASE.md"
$entryPointsPath = Join-Path $root "docs/entry-points.md"
$usecasesPath = Join-Path $root "docs/usecases.md"
$reusableWrappersPath = Join-Path $root "docs/reusable-wrappers.md"
$reportsPath = Join-Path $root "docs/reports.md"
$chartsPath = Join-Path $root "docs/charts.md"
$computedFieldsPath = Join-Path $root "docs/computed-fields.md"
$timeBucketsPath = Join-Path $root "docs/time-buckets.md"
$tabularSchemaPath = Join-Path $root "docs/tabular-schema.md"
$telemetryPath = Join-Path $root "docs/telemetry.md"
$cachingPath = Join-Path $root "docs/caching.md"
$metamodelPath = Join-Path $root "docs/metamodel.md"
$modulesPath = Join-Path $root "docs/modules.md"
$sqlLikePath = Join-Path $root "docs/sql-like.md"
$benchmarkingPath = Join-Path $root "docs/benchmarking.md"
$benchmarkMainArgsPath = Join-Path $root "scripts/benchmark-suite-main.args"
$quickstartPomPath = Join-Path $root "examples/spring-boot-starter-quickstart/pom.xml"
$basicPomPath = Join-Path $root "examples/spring-boot-starter-basic/pom.xml"

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
$readme = Require-File $readmePath
$contributing = Require-File $contributingPath
$changelog = Require-File $changelogPath
$migration = Require-File $migrationPath
$release = Require-File $releasePath
$entryPoints = Require-File $entryPointsPath
$usecases = Require-File $usecasesPath
$reusableWrappers = Require-File $reusableWrappersPath
$reports = Require-File $reportsPath
$charts = Require-File $chartsPath
$computedFields = Require-File $computedFieldsPath
$timeBuckets = Require-File $timeBucketsPath
$tabularSchema = Require-File $tabularSchemaPath
$telemetry = Require-File $telemetryPath
$caching = Require-File $cachingPath
$metamodel = Require-File $metamodelPath
$modules = Require-File $modulesPath
$sqlLike = Require-File $sqlLikePath
$benchmarking = Require-File $benchmarkingPath
$benchmarkMainArgs = Require-File $benchmarkMainArgsPath
$quickstartPom = Require-File $quickstartPomPath
$basicPom = Require-File $basicPomPath
$errors = [System.Collections.Generic.List[string]]::new()

# Current release/version examples should track the root POM version.
Require-Substring $readme "README.md" "<version>$projectVersion</version>" $errors
Require-Substring $modules "docs/modules.md" "<version>$projectVersion</version>" $errors
Require-Substring $release "RELEASE.md" "Maven version: ``$projectVersion``" $errors
Require-Substring $release "RELEASE.md" "Git tag: ``release-$projectVersion``" $errors
Require-Substring $changelog "CHANGELOG.md" "## [$projectVersion]" $errors
Require-Substring $quickstartPom "examples/spring-boot-starter-quickstart/pom.xml" "<version>$projectVersion</version>" $errors
Require-Substring $basicPom "examples/spring-boot-starter-basic/pom.xml" "<version>$projectVersion</version>" $errors

# Public entry docs should keep the stable surface centered on SQL-like and guided natural language.
$publicEntryDocs = @(
    @{ Name = "README.md"; Text = $readme },
    @{ Name = "MIGRATION.md"; Text = $migration },
    @{ Name = "docs/entry-points.md"; Text = $entryPoints },
    @{ Name = "docs/usecases.md"; Text = $usecases },
    @{ Name = "docs/reusable-wrappers.md"; Text = $reusableWrappers },
    @{ Name = "docs/reports.md"; Text = $reports },
    @{ Name = "docs/charts.md"; Text = $charts },
    @{ Name = "docs/computed-fields.md"; Text = $computedFields },
    @{ Name = "docs/time-buckets.md"; Text = $timeBuckets },
    @{ Name = "docs/tabular-schema.md"; Text = $tabularSchema },
    @{ Name = "docs/telemetry.md"; Text = $telemetry },
    @{ Name = "docs/caching.md"; Text = $caching },
    @{ Name = "docs/metamodel.md"; Text = $metamodel }
)
$internalApiPatterns = @(
    "\bPojoLensCore\b",
    "\bQueryBuilder\b",
    "\bFluentQueryDefinition\b",
    "ReportDefinition\.fluent",
    "laughing\.man\.commits\.internal"
)
foreach ($doc in $publicEntryDocs) {
    foreach ($pattern in $internalApiPatterns) {
        Forbid-Pattern $doc.Text $doc.Name $pattern $errors
    }
}

# Benchmark command drift should use dynamic jar resolution in process docs.
Require-Pattern $contributing "CONTRIBUTING.md" 'BENCHMARK_JAR=.*\*-benchmarks\.jar' $errors
Require-Pattern $release "RELEASE.md" 'target/\*-benchmarks\.jar' $errors
Forbid-Pattern $contributing "CONTRIBUTING.md" 'pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar' $errors
Forbid-Pattern $release "RELEASE.md" 'Release\s+\d+\.\d+\.\d+|v\d+\.\d+\.\d+' $errors
Forbid-Pattern $release "RELEASE.md" 'pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar' $errors

# SQL-like capability drift checks.
Require-Pattern $migration "MIGRATION.md" 'supports uncorrelated .*WHERE \.\.\. IN \(select \.\.\.\).*subqueries' $errors
Require-Pattern $migration "MIGRATION.md" 'supports bounded uncorrelated .*EXISTS \(select' $errors
Require-Pattern $migration "MIGRATION.md" 'chained joins are supported' $errors
Require-Pattern $release "RELEASE.md" 'supports uncorrelated\s+`WHERE \.\.\. IN \(select \.\.\.\)` subqueries' $errors
Require-Pattern $release "RELEASE.md" 'bounded uncorrelated\s+`WHERE \[NOT\] EXISTS \(select \.\.\.\)` subqueries' $errors
Require-Pattern $release "RELEASE.md" 'chained joins are supported' $errors
Forbid-Pattern $migration "MIGRATION.md" 'does not support subqueries or multi-join SQL plans' $errors
Forbid-Pattern $migration "MIGRATION.md" '`EXISTS`, scalar, and broad nested SQL subquery plans are still unsupported' $errors
Forbid-Pattern $release "RELEASE.md" 'unsupported:\s*subqueries,\s*multi-join SQL plans' $errors
Forbid-Pattern $release "RELEASE.md" 'correlated, `EXISTS`, scalar, and broad nested SQL subqueries remain unsupported' $errors

# Source docs still define the canonical behavior.
Require-Substring $sqlLike "docs/sql-like.md" 'SQL-like subqueries support uncorrelated `WHERE <field> IN (select ...)`' $errors
Require-Substring $sqlLike "docs/sql-like.md" 'and `WHERE [NOT] EXISTS (select ...)` predicates.' $errors
Require-Substring $sqlLike "docs/sql-like.md" 'chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the source explicitly' $errors

# Benchmark guide and suite should still cover the guarded benchmark path.
Require-Substring $benchmarkMainArgs "scripts/benchmark-suite-main.args" "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "BenchmarkThresholdChecker" $errors
Require-Substring $benchmarking "docs/benchmarking.md" "benchmarks/chart-thresholds.json" $errors
Require-Pattern $benchmarking "docs/benchmarking.md" 'BENCHMARK_JAR=.*target/\*-benchmarks\.jar' $errors
Forbid-Pattern $benchmarking "docs/benchmarking.md" 'target/pojo-lens-\d+(?:\.\d+)+-benchmarks\.jar' $errors

if ($errors.Count -gt 0) {
    Write-Host "[doc-check] FAILED"
    foreach ($e in $errors) {
        Write-Host "- $e"
    }
    exit 1
}

Write-Host "[doc-check] OK: documentation invariants satisfied."
