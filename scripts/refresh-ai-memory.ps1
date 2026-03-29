param(
    [switch]$Check,
    [switch]$NoSQLite,
    [switch]$RequireSQLite,
    [switch]$CompactLog,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$aiDir = Join-Path $repoRoot "ai"
$indexDir = Join-Path $aiDir "indexes"
$memoryStatePath = Join-Path $aiDir "memory-state.json"
$schemaVersion = 5
$hotContextMaxLines = 240
$hotContextMaxBytes = 24KB
$hotContextFiles = @(
    (Join-Path $aiDir "core\agent-invariants.md"),
    (Join-Path $aiDir "core\repo-purpose.md"),
    (Join-Path $aiDir "state\current-state.md"),
    (Join-Path $aiDir "state\handoff.md")
)
$rootTextFiles = @(
    "AGENTS.md",
    "CONTRIBUTING.md",
    "MAINTENANCE.md",
    "MIGRATION.md",
    "README.md",
    "RELEASE.md",
    "TODO.md"
)
$moduleSpecs = @(
    [ordered]@{ path = "pojo-lens"; kind = "runtime-module"; role = "runtime"; published = $true },
    [ordered]@{ path = "pojo-lens-spring-boot-autoconfigure"; kind = "boot-autoconfigure-module"; role = "spring-boot-autoconfigure"; published = $true },
    [ordered]@{ path = "pojo-lens-spring-boot-starter"; kind = "boot-starter-module"; role = "spring-boot-starter"; published = $true },
    [ordered]@{ path = "pojo-lens-benchmarks"; kind = "benchmark-module"; role = "benchmark-tooling"; published = $false },
    [ordered]@{ path = "examples/spring-boot-starter-basic"; kind = "example-module"; role = "starter-dashboard-example"; published = $false },
    [ordered]@{ path = "examples/spring-boot-starter-quickstart"; kind = "example-module"; role = "starter-quickstart-example"; published = $false }
)
$symbolGroups = [ordered]@{
    "facades" = @("PojoLens", "PojoLensCore", "PojoLensSql", "PojoLensChart", "PojoLensRuntime", "PojoLensRuntimePreset")
    "fluent-engine" = @("QueryBuilder", "FilterQueryBuilder", "FilterImpl", "FastArrayQuerySupport", "FastStatsQuerySupport", "JoinEngine")
    "sql-like" = @("SqlLikeQuery", "SqlLikeTemplate", "SqlLikeBoundQuery", "SqlLikeCursor", "SqlLikeParser", "SqlLikeValidator", "SqlLikeBinder", "SqlExpressionEvaluator")
    "chart-and-stats" = @("ChartMapper", "ChartQueryPreset", "ChartQueryPresets", "ChartJsAdapter", "ChartJsPayload", "StatsViewPresets", "StatsViewPreset", "StatsTable", "StatsTablePayload", "TabularRows")
    "ecosystem" = @("DatasetBundle", "ReportDefinition", "SnapshotComparison", "QueryRegressionFixture", "FieldMetamodelGenerator", "QueryTelemetryListener")
    "spring-boot" = @("PojoLensProperties", "MicrometerQueryTelemetryListener", "PojoLensSpringBootAutoConfiguration", "PojoLensSpringBootStarterMarker")
    "examples" = @("BasicExampleApplication", "QuickstartExampleApplication", "QuickstartEmployeeController", "EmployeeDashboardService", "EmployeeQueryController", "EmployeeStore")
    "benchmark-tooling" = @("JmhRunner", "BenchmarkThresholdChecker", "BenchmarkMetricsPlotGenerator")
}

function Write-Utf8NoBom([string]$path, [string]$content) {
    $parent = Split-Path -Parent $path
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $content, $encoding)
}

function ConvertTo-RepoJson([object]$value) {
    return ($value | ConvertTo-Json -Depth 20)
}

function Get-RelPath([string]$path) {
    $rootPath = (Resolve-Path $repoRoot).Path.TrimEnd("\")
    $targetPath = (Resolve-Path $path).Path
    $rootUri = New-Object System.Uri(($rootPath + "\"))
    $targetUri = New-Object System.Uri($targetPath)
    return $rootUri.MakeRelativeUri($targetUri).ToString().Replace("\", "/")
}

function Read-Text([string]$path) {
    return [System.IO.File]::ReadAllText($path)
}

function Get-FileLineCount([string]$path) {
    return [System.IO.File]::ReadAllLines($path).Length
}

function Test-RepoPath([string]$relativePath) {
    if ($relativePath.Contains("*") -or $relativePath.StartsWith("target/")) {
        return $true
    }
    return (Test-Path (Join-Path $repoRoot $relativePath))
}

function Get-MarkdownFiles() {
    $files = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($relative in $rootTextFiles) {
        $path = Join-Path $repoRoot $relative
        if (Test-Path $path) {
            $files.Add((Resolve-Path $path).Path) | Out-Null
        }
    }
    if (Test-Path (Join-Path $repoRoot "docs")) {
        foreach ($file in Get-ChildItem (Join-Path $repoRoot "docs") -Filter *.md -File -Recurse) {
            $files.Add($file.FullName) | Out-Null
        }
    }
    foreach ($file in Get-ChildItem $aiDir -Filter *.md -File -Recurse) {
        $files.Add($file.FullName) | Out-Null
    }
    return @($files | Sort-Object)
}

function Get-ColdSearchFiles() {
    $files = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($path in Get-MarkdownFiles) {
        $files.Add($path) | Out-Null
    }
    $logRoot = Join-Path $aiDir "log"
    if (Test-Path $logRoot) {
        foreach ($file in Get-ChildItem $logRoot -Filter *.jsonl -File -Recurse) {
            $files.Add($file.FullName) | Out-Null
        }
    }
    return @($files | Sort-Object)
}

function Get-JavaFiles([string[]]$relativeRoots) {
    $files = New-Object System.Collections.Generic.List[string]
    foreach ($relativeRoot in $relativeRoots) {
        $path = Join-Path $repoRoot $relativeRoot
        if (-not (Test-Path $path)) {
            continue
        }
        foreach ($file in Get-ChildItem $path -Filter *.java -File -Recurse) {
            $files.Add($file.FullName)
        }
    }
    return @($files | Sort-Object -Unique)
}

function Get-HashInputs() {
    $files = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($path in Get-MarkdownFiles) {
        $files.Add($path) | Out-Null
    }
    foreach ($relative in @(
        "pom.xml",
        "pojo-lens/pom.xml",
        "pojo-lens-benchmarks/pom.xml",
        "pojo-lens-spring-boot-autoconfigure/pom.xml",
        "pojo-lens-spring-boot-starter/pom.xml",
        ".github/workflows/ci.yml",
        ".github/workflows/release.yml",
        "scripts/refresh-ai-memory.py",
        "scripts/refresh-ai-memory.ps1",
        "scripts/query-ai-memory.py",
        "scripts/query-ai-memory.ps1"
    )) {
        $path = Join-Path $repoRoot $relative
        if (Test-Path $path) {
            $files.Add((Resolve-Path $path).Path) | Out-Null
        }
    }
    foreach ($path in Get-JavaFiles @(
        "pojo-lens/src/main/java",
        "pojo-lens/src/test/java",
        "pojo-lens-benchmarks/src/main/java",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-spring-boot-autoconfigure/src/main/java",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/main/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/main/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/main/java",
        "examples/spring-boot-starter-quickstart/src/test/java"
    )) {
        $files.Add($path) | Out-Null
    }
    $aiAgents = Join-Path $aiDir "AGENTS.md"
    if (Test-Path $aiAgents) {
        $files.Add((Resolve-Path $aiAgents).Path) | Out-Null
    }
    $logRoot = Join-Path $aiDir "log"
    if (Test-Path $logRoot) {
        foreach ($file in Get-ChildItem $logRoot -Filter *.jsonl -File -Recurse) {
            $files.Add($file.FullName) | Out-Null
        }
    }
    return @($files | Sort-Object)
}

function Get-CombinedHash([string[]]$paths) {
    $lines = foreach ($path in $paths) {
        $fileHash = (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLowerInvariant()
        "{0}|{1}" -f (Get-RelPath $path), $fileHash
    }
    $payload = [System.Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))
    $hash = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($hash.ComputeHash($payload))).Replace("-", "").ToLowerInvariant()
    } finally {
        $hash.Dispose()
    }
}

function Get-IsoNow() {
    return (Get-Date).ToString("o")
}

function Get-GitOutput([string[]]$arguments) {
    try {
        return (& git @arguments 2>$null).Trim()
    } catch {
        return $null
    }
}

function Get-HotContextStats() {
    $fileStats = @()
    $totalLines = 0
    $totalBytes = 0
    foreach ($path in $hotContextFiles) {
        $lineCount = Get-FileLineCount $path
        $byteCount = ([System.IO.File]::ReadAllBytes($path)).Length
        $totalLines += $lineCount
        $totalBytes += $byteCount
        $fileStats += [ordered]@{
            path = Get-RelPath $path
            lines = $lineCount
            bytes = $byteCount
        }
    }
    return [ordered]@{
        files = $fileStats
        totalLines = $totalLines
        totalBytes = $totalBytes
        maxLines = $hotContextMaxLines
        maxBytes = $hotContextMaxBytes
        withinBudget = ($totalLines -le $hotContextMaxLines -and $totalBytes -le $hotContextMaxBytes)
    }
}

function Get-PomValue([xml]$xml, [string[]]$xpaths) {
    $namespace = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $namespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    foreach ($xpath in $xpaths) {
        $node = $xml.SelectSingleNode($xpath, $namespace)
        if ($node -and $node.InnerText.Trim()) {
            return $node.InnerText.Trim()
        }
    }
    return $null
}

function Parse-Pom([string]$path) {
    [xml]$xml = Read-Text $path
    $namespace = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $namespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    $modules = @()
    foreach ($node in $xml.SelectNodes("/m:project/m:modules/m:module", $namespace)) {
        $modules += $node.InnerText.Trim()
    }
    $profiles = @()
    foreach ($node in $xml.SelectNodes("/m:project/m:profiles/m:profile/m:id", $namespace)) {
        $profiles += $node.InnerText.Trim()
    }
    return [ordered]@{
        path = Get-RelPath $path
        groupId = Get-PomValue $xml @("/m:project/m:groupId", "/m:project/m:parent/m:groupId")
        artifactId = Get-PomValue $xml @("/m:project/m:artifactId")
        version = Get-PomValue $xml @("/m:project/m:version", "/m:project/m:parent/m:version")
        packaging = $( $packagingValue = Get-PomValue $xml @("/m:project/m:packaging"); if ($packagingValue) { $packagingValue } else { "jar" } )
        modules = $modules
        profiles = $profiles
        javaRelease = Get-PomValue $xml @("/m:project/m:properties/m:maven.compiler.release")
        springBootVersion = Get-PomValue $xml @("/m:project/m:properties/m:spring.boot.version")
    }
}

function Get-WorkflowJobs([string]$path) {
    $jobs = New-Object System.Collections.Generic.List[string]
    $inJobs = $false
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        if (-not $inJobs) {
            if ($line.Trim() -eq "jobs:") {
                $inJobs = $true
            }
            continue
        }
        if ($line -and -not $line.StartsWith(" ")) {
            break
        }
        if ($line -match "^  ([A-Za-z0-9_-]+):\s*$") {
            $jobs.Add($Matches[1])
        }
    }
    return @($jobs)
}

function Get-WorkflowJavaVersions([string]$path) {
    $text = Read-Text $path
    if ($text -match "java:\s*\[([^\]]+)\]") {
        $versions = @()
        foreach ($token in ($Matches[1] -split ",")) {
            $candidate = $token.Trim().Trim("'`"")
            if ($candidate -match "^\d+$") {
                $versions += [int]$candidate
            }
        }
        return $versions
    }
    return @()
}

function Get-ReleaseModules([string]$path) {
    $text = Read-Text $path
    if ($text -match "-pl\s+([^\s]+)\s+-am\s+-Prelease-central") {
        return @($Matches[1] -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }
    return @()
}

function Get-DocCategory([string]$relativePath) {
    $hotPaths = $hotContextFiles | ForEach-Object { Get-RelPath $_ }
    $highProductDocs = @(
        "docs/entry-points.md",
        "docs/reusable-wrappers.md",
        "docs/usecases.md",
        "docs/advanced-features.md",
        "docs/sql-like.md",
        "docs/charts.md"
    )
    if ($relativePath -eq "AGENTS.md") { return @("agent-guide", "high", $null) }
    if ($relativePath -eq "ai/AGENTS.md") { return @("ai-memory-guide", "high", "cold") }
    if ($hotPaths -contains $relativePath) { return @("ai-hot-context", "high", "hot") }
    if ($relativePath -eq "ai/state/recent-validations.md") { return @("ai-validation-history", "high", "warm") }
    if ($relativePath -eq "ai/state/benchmark-state.md") { return @("ai-benchmark-state", "medium", "cold") }
    if ($relativePath.StartsWith("ai/core/")) { return @("ai-core", "medium", "cold") }
    if ($relativePath.StartsWith("ai/state/")) { return @("ai-state", "medium", "cold") }
    if ($relativePath -eq "README.md") { return @("readme", "high", $null) }
    if ($relativePath -eq "TODO.md") { return @("planning", "high", $null) }
    if ($relativePath -eq "CONTRIBUTING.md") { return @("process-doc", "high", $null) }
    if ($relativePath -eq "RELEASE.md") { return @("release-doc", "high", $null) }
    if ($relativePath -eq "MIGRATION.md") { return @("process-doc", "medium", $null) }
    if ($relativePath -eq "MAINTENANCE.md") { return @("memory-maintenance", "medium", $null) }
    if ($relativePath.StartsWith("docs/")) {
        $relevance = if ($highProductDocs -contains $relativePath) { "high" } else { "medium" }
        return @("product-doc", $relevance, $null)
    }
    return @("document", "medium", $null)
}

function Get-TopLevelTypeKind([string]$path) {
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        if ($line -match "\b(class|interface|enum|record)\s+([A-Za-z0-9_]+)") {
            return $Matches[1]
        }
    }
    return "class"
}

function Find-JavaSymbol([string]$symbolName) {
    $candidates = Get-ChildItem $repoRoot -Filter "$symbolName.java" -File -Recurse | Where-Object {
        $_.FullName -notmatch "\\target\\" -and $_.FullName -notmatch "\\\.git\\" -and $_.FullName -notmatch "\\\.idea\\"
    }
    $preferred = $candidates | Where-Object { $_.FullName -match "src\\main\\java" }
    if ($preferred) {
        return ($preferred | Sort-Object FullName | Select-Object -First 1).FullName
    }
    if ($candidates) {
        return ($candidates | Sort-Object FullName | Select-Object -First 1).FullName
    }
    return $null
}

function Build-DocsIndex([string]$generatedAt, [string[]]$coldSearchFiles) {
    $coldSearchSet = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($path in $coldSearchFiles) {
        $coldSearchSet.Add((Get-RelPath $path)) | Out-Null
    }
    $documents = foreach ($path in Get-MarkdownFiles) {
        $relativePath = Get-RelPath $path
        $category = Get-DocCategory $relativePath
        [ordered]@{
            path = $relativePath
            category = $category[0]
            relevance = $category[1]
            loadTier = $category[2]
            lineCount = Get-FileLineCount $path
            byteCount = ([System.IO.File]::ReadAllBytes($path)).Length
            coldSearchEligible = $coldSearchSet.Contains($relativePath)
        }
    }
    return [ordered]@{
        generatedAt = $generatedAt
        documents = @($documents)
    }
}

function Build-FilesIndex([string]$generatedAt) {
    $moduleRoots = foreach ($spec in $moduleSpecs) {
        $modulePath = Join-Path $repoRoot $spec.path
        $sourceRoot = Join-Path $modulePath "src\main\java"
        $testRoot = Join-Path $modulePath "src\test\java"
        $resourceRoot = Join-Path $modulePath "src\main\resources"
        [ordered]@{
            path = $spec.path
            kind = $spec.kind
            role = $spec.role
            published = $spec.published
            sourceRoot = if (Test-Path $sourceRoot) { Get-RelPath $sourceRoot } else { $null }
            testRoot = if (Test-Path $testRoot) { Get-RelPath $testRoot } else { $null }
            resourceRoot = if (Test-Path $resourceRoot) { Get-RelPath $resourceRoot } else { $null }
            mainJavaFiles = if (Test-Path $sourceRoot) { (Get-ChildItem $sourceRoot -Filter *.java -File -Recurse).Count } else { 0 }
            testJavaFiles = if (Test-Path $testRoot) { (Get-ChildItem $testRoot -Filter *.java -File -Recurse).Count } else { 0 }
            resourceFiles = if (Test-Path $resourceRoot) { (Get-ChildItem $resourceRoot -File -Recurse).Count } else { 0 }
        }
    }
    $importantFiles = @(
        [ordered]@{ path = "pom.xml"; kind = "build" },
        [ordered]@{ path = "pojo-lens/pom.xml"; kind = "module-build" },
        [ordered]@{ path = "pojo-lens-spring-boot-autoconfigure/pom.xml"; kind = "module-build" },
        [ordered]@{ path = "pojo-lens-spring-boot-starter/pom.xml"; kind = "module-build" },
        [ordered]@{ path = "pojo-lens-benchmarks/pom.xml"; kind = "module-build" },
        [ordered]@{ path = ".github/workflows/ci.yml"; kind = "ci" },
        [ordered]@{ path = ".github/workflows/release.yml"; kind = "release-ci" },
        [ordered]@{ path = "README.md"; kind = "product-doc" },
        [ordered]@{ path = "CONTRIBUTING.md"; kind = "process-doc" },
        [ordered]@{ path = "RELEASE.md"; kind = "process-doc" },
        [ordered]@{ path = "TODO.md"; kind = "planning" },
        [ordered]@{ path = "MAINTENANCE.md"; kind = "memory-maintenance" },
        [ordered]@{ path = "ai/state/recent-validations.md"; kind = "ai-warm-state" },
        [ordered]@{ path = "scripts/refresh-ai-memory.py"; kind = "memory-script" },
        [ordered]@{ path = "scripts/query-ai-memory.py"; kind = "memory-script" },
        [ordered]@{ path = "scripts/check-doc-consistency.ps1"; kind = "validation-script" },
        [ordered]@{ path = "scripts/check-lint-baseline.ps1"; kind = "validation-script" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/PojoLens.java"; kind = "public-entry" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/PojoLensCore.java"; kind = "public-entry" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/PojoLensSql.java"; kind = "public-entry" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/PojoLensRuntime.java"; kind = "public-entry" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java"; kind = "engine" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/filter/FilterImpl.java"; kind = "engine" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java"; kind = "engine" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/sqllike/parser/SqlLikeParser.java"; kind = "engine" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/chart/ChartQueryPreset.java"; kind = "feature" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/stats/StatsViewPresets.java"; kind = "feature" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/report/ReportDefinition.java"; kind = "feature" },
        [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits/chartjs/ChartJsAdapter.java"; kind = "feature" },
        [ordered]@{ path = "examples/spring-boot-starter-basic/src/main/java/laughing/man/commits/examples/spring/boot/basic/EmployeeDashboardService.java"; kind = "example" },
        [ordered]@{ path = "examples/spring-boot-starter-quickstart/src/main/java/laughing/man/commits/examples/spring/boot/quickstart/QuickstartEmployeeController.java"; kind = "example" }
    ) | Where-Object { Test-RepoPath $_.path }
    return [ordered]@{
        generatedAt = $generatedAt
        notes = "Generated navigation anchors for the current multi-module layout. Markdown remains the source of truth; target/ outputs are excluded."
        counts = [ordered]@{
            modules = $moduleSpecs.Count
            mainJavaFiles = (Get-JavaFiles @("pojo-lens/src/main/java", "pojo-lens-benchmarks/src/main/java", "pojo-lens-spring-boot-autoconfigure/src/main/java", "pojo-lens-spring-boot-starter/src/main/java", "examples/spring-boot-starter-basic/src/main/java", "examples/spring-boot-starter-quickstart/src/main/java")).Count
            testJavaFiles = (Get-JavaFiles @("pojo-lens/src/test/java", "pojo-lens-benchmarks/src/test/java", "pojo-lens-spring-boot-autoconfigure/src/test/java", "pojo-lens-spring-boot-starter/src/test/java", "examples/spring-boot-starter-basic/src/test/java", "examples/spring-boot-starter-quickstart/src/test/java")).Count
            markdownDocs = (Get-MarkdownFiles).Count
            aiCoreFiles = (Get-ChildItem (Join-Path $aiDir "core") -Filter *.md -File).Count
            aiIndexFiles = (Get-ChildItem $indexDir -Filter *.json -File -ErrorAction SilentlyContinue).Count
        }
        roots = @(
            [ordered]@{ path = ".github/workflows"; kind = "ci" },
            [ordered]@{ path = "ai/core"; kind = "ai-core" },
            [ordered]@{ path = "ai/state"; kind = "ai-state" },
            [ordered]@{ path = "ai/indexes"; kind = "ai-indexes" },
            [ordered]@{ path = "ai/log"; kind = "ai-log" },
            [ordered]@{ path = "ai/log/archive"; kind = "ai-log-archive" },
            [ordered]@{ path = "benchmarks"; kind = "benchmark-config" },
            [ordered]@{ path = "docs"; kind = "documentation" },
            [ordered]@{ path = "scripts"; kind = "repo-scripts" },
            [ordered]@{ path = "pojo-lens/src/main/java/laughing/man/commits"; kind = "runtime-source-root" },
            [ordered]@{ path = "pojo-lens/src/test/java/laughing/man/commits"; kind = "runtime-test-root" },
            [ordered]@{ path = "pojo-lens-benchmarks/src/main/java/laughing/man/commits/benchmark"; kind = "benchmark-source-root" },
            [ordered]@{ path = "examples/spring-boot-starter-basic"; kind = "example" },
            [ordered]@{ path = "examples/spring-boot-starter-quickstart"; kind = "example" }
        )
        moduleRoots = @($moduleRoots)
        importantFiles = @($importantFiles)
    }
}

function Build-SymbolsIndex([string]$generatedAt) {
    $groups = @()
    foreach ($component in $symbolGroups.Keys) {
        $resolved = @()
        foreach ($symbol in $symbolGroups[$component]) {
            $path = Find-JavaSymbol $symbol
            if (-not $path) {
                continue
            }
            $resolved += [ordered]@{
                name = $symbol
                kind = Get-TopLevelTypeKind $path
                path = Get-RelPath $path
            }
        }
        if ($resolved.Count -gt 0) {
            $groups += [ordered]@{
                component = $component
                symbols = @($resolved)
            }
        }
    }
    return [ordered]@{
        generatedAt = $generatedAt
        groups = @($groups)
    }
}

function Get-TestCategory([string]$relativePath) {
    $name = [System.IO.Path]::GetFileName($relativePath)
    if ($relativePath.StartsWith("examples/")) { return "starter-example" }
    if ($relativePath.Contains("benchmark") -or $name.Contains("Benchmark")) { return "benchmark-tooling" }
    if ($relativePath.Contains("publicapi/") -or $name.StartsWith("PublicApi") -or $name.StartsWith("PublicSurface") -or $name.StartsWith("StablePublicApi")) { return "public-surface" }
    if ($name.EndsWith("DocsExamplesTest.java")) { return "docs-examples" }
    if ($relativePath.Contains("sqllike/") -or $name.StartsWith("SqlLike")) { return "sql-like" }
    if ($name.Contains("Cache") -or $name.Contains("Runtime") -or $name.Contains("AutoConfiguration") -or $name.Contains("StarterSmoke")) { return "runtime-and-cache" }
    if ($name.Contains("Chart") -or $name.Contains("Report") -or $name.Contains("Stats") -or $name.Contains("Snapshot") -or $name.Contains("RegressionFixture") -or $relativePath.Contains("/chart/")) { return "charts-stats-reports" }
    return "fluent-and-query-engine"
}

function Build-TestIndex([string]$generatedAt) {
    $testRoots = @()
    foreach ($relative in @(
        "pojo-lens/src/test/java",
        "pojo-lens/src/test/resources/fixtures",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-benchmarks/src/test/resources/fixtures",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/test/java"
    )) {
        if (Test-Path (Join-Path $repoRoot $relative)) {
            $testRoots += $relative
        }
    }
    $testFiles = Get-JavaFiles @(
        "pojo-lens/src/test/java",
        "pojo-lens-benchmarks/src/test/java",
        "pojo-lens-spring-boot-autoconfigure/src/test/java",
        "pojo-lens-spring-boot-starter/src/test/java",
        "examples/spring-boot-starter-basic/src/test/java",
        "examples/spring-boot-starter-quickstart/src/test/java"
    )
    $categories = [ordered]@{}
    foreach ($path in $testFiles) {
        $relativePath = Get-RelPath $path
        $category = Get-TestCategory $relativePath
        if (-not $categories.Contains($category)) {
            $categories[$category] = New-Object System.Collections.Generic.List[string]
        }
        $categories[$category].Add($relativePath)
    }
    $modules = foreach ($spec in $moduleSpecs) {
        $testRoot = Join-Path (Join-Path $repoRoot $spec.path) "src\test\java"
        if (Test-Path $testRoot) {
            [ordered]@{
                path = $spec.path
                testJavaFiles = (Get-ChildItem $testRoot -Filter *.java -File -Recurse).Count
            }
        }
    }
    return [ordered]@{
        generatedAt = $generatedAt
        testRoots = $testRoots
        counts = [ordered]@{
            testJavaFiles = $testFiles.Count
            testClasses = $testFiles.Count
        }
        modules = @($modules)
        suites = @(
            foreach ($key in ($categories.Keys | Sort-Object)) {
                [ordered]@{
                    category = $key
                    files = @($categories[$key] | Sort-Object)
                }
            }
        )
    }
}

function Build-ConfigIndex([string]$generatedAt) {
    $rootPom = Parse-Pom (Join-Path $repoRoot "pom.xml")
    $modulePoms = foreach ($spec in $moduleSpecs) {
        $path = Join-Path (Join-Path $repoRoot $spec.path) "pom.xml"
        if (Test-Path $path) {
            $pom = Parse-Pom $path
            [ordered]@{
                path = $pom.path
                artifactId = $pom.artifactId
                packaging = $pom.packaging
                role = $spec.role
                published = $spec.published
            }
        }
    }
    $ciPath = Join-Path $repoRoot ".github\workflows\ci.yml"
    $releasePath = Join-Path $repoRoot ".github\workflows\release.yml"
    return [ordered]@{
        generatedAt = $generatedAt
        build = [ordered]@{
            path = "pom.xml"
            groupId = $rootPom.groupId
            artifactId = $rootPom.artifactId
            version = $rootPom.version
            packaging = $rootPom.packaging
            javaRelease = if ($rootPom.javaRelease) { [int]$rootPom.javaRelease } else { $null }
            springBootVersion = $rootPom.springBootVersion
            modules = @($modulePoms)
            profiles = @($rootPom.profiles | Sort-Object -Unique)
        }
        ci = [ordered]@{
            workflows = @(
                [ordered]@{
                    path = ".github/workflows/ci.yml"
                    jobs = @(Get-WorkflowJobs $ciPath)
                    testJavaVersions = @(Get-WorkflowJavaVersions $ciPath)
                },
                [ordered]@{
                    path = ".github/workflows/release.yml"
                    jobs = @(Get-WorkflowJobs $releasePath)
                    triggerTags = @("v*")
                    manualDispatch = $true
                    releaseModules = @(Get-ReleaseModules $releasePath)
                }
            )
        }
        aiMemory = [ordered]@{
            sourceOfTruth = @("ai/core/*.md", "ai/state/*.md", "ai/log/events.jsonl", "ai/log/archive/*.jsonl")
            hotContext = @($hotContextFiles | ForEach-Object { Get-RelPath $_ })
            warmContext = @("ai/state/recent-validations.md")
            generatedIndexes = @(
                "ai/indexes/files-index.json",
                "ai/indexes/docs-index.json",
                "ai/indexes/symbols-index.json",
                "ai/indexes/test-index.json",
                "ai/indexes/config-index.json"
            )
            optionalColdSearchDb = "ai/indexes/cold-memory.db"
            refreshCommand = "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1"
            checkCommand = "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -Check"
            compactCommand = "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/refresh-ai-memory.ps1 -CompactLog"
            searchCommand = "pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/query-ai-memory.ps1 -Query <text>"
            eventRetention = [ordered]@{
                activeLog = "ai/log/events.jsonl"
                archivePattern = "ai/log/archive/*.jsonl"
                recentEntries = 12
            }
        }
        validationScripts = @(
            "scripts/check-doc-consistency.ps1",
            "scripts/check-doc-consistency.py",
            "scripts/check-lint-baseline.ps1",
            "scripts/refresh-ai-memory.ps1",
            "scripts/refresh-ai-memory.py"
        )
        memoryScripts = @(
            "scripts/query-ai-memory.ps1",
            "scripts/query-ai-memory.py"
        )
        releaseScripts = @("scripts/export-release-secrets.ps1")
        benchmarkConfigs = @(
            "benchmarks/thresholds.json",
            "benchmarks/chart-thresholds.json",
            "scripts/benchmark-suite-main.args",
            "scripts/benchmark-suite-chart.args",
            "scripts/benchmark-suite-hotspots.args",
            "scripts/benchmark-suite-baseline.args",
            "scripts/benchmark-suite-cache.args",
            "scripts/benchmark-suite-indexes.args",
            "scripts/benchmark-suite-streaming.args",
            "scripts/benchmark-suite-window.args"
        )
    }
}

function Get-JsonPaths([string]$json) {
    return @([regex]::Matches($json, '"path"\s*:\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
}

function Get-SqliteState([switch]$NoSQLiteMode) {
    if ($NoSQLiteMode) {
        return [ordered]@{
            status = "skipped"
            path = "ai/indexes/cold-memory.db"
            ftsEnabled = $false
            documents = 0
            sections = 0
        }
    }
    return [ordered]@{
        status = "unavailable"
        path = "ai/indexes/cold-memory.db"
        ftsEnabled = $false
        documents = 0
        sections = 0
        error = "No local SQLite backend was found (python/sqlite3 unavailable)."
    }
}

function Build-MemoryState([string]$generatedAt, [string]$inputsHash, [hashtable]$hotStats, [hashtable]$sqliteState, [string[]]$missingPaths) {
    $reasons = New-Object System.Collections.Generic.List[string]
    if (-not $hotStats.withinBudget) {
        $reasons.Add("hot-context-over-budget")
    }
    if ($missingPaths.Count -gt 0) {
        $reasons.Add("indexed-paths-missing")
    }
    if ($sqliteState.status -eq "unavailable") {
        $reasons.Add("sqlite-unavailable")
    }
    return [ordered]@{
        schemaVersion = $schemaVersion
        generatedAt = $generatedAt
        git = [ordered]@{
            headCommit = Get-GitOutput @("rev-parse", "HEAD")
            branch = Get-GitOutput @("branch", "--show-current")
            dirty = [bool](Get-GitOutput @("status", "--short"))
        }
        inputsHash = $inputsHash
        hotContext = $hotStats
        derivedArtifacts = [ordered]@{
            jsonIndexes = @(
                "ai/indexes/files-index.json",
                "ai/indexes/docs-index.json",
                "ai/indexes/symbols-index.json",
                "ai/indexes/test-index.json",
                "ai/indexes/config-index.json"
            )
            sqlite = $sqliteState
        }
        freshness = [ordered]@{
            status = if ($reasons.Count -eq 0) { "fresh" } else { "warning" }
            reasons = @($reasons)
            missingPaths = $missingPaths
        }
    }
}

function Get-UsablePythonCommand() {
    foreach ($name in @("py", "python", "python3")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if (-not $command) {
            continue
        }
        $source = $command.Source
        if ($source -match "WindowsApps\\python") {
            continue
        }
        if ($name -eq "py") {
            return @($source, "-3")
        }
        return @($source)
    }
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($userPath) {
        foreach ($entry in ($userPath -split ";" | Where-Object { $_ })) {
            foreach ($candidate in @("python.exe", "python3.exe", "py.exe")) {
                $candidatePath = Join-Path $entry $candidate
                if (-not (Test-Path $candidatePath)) {
                    continue
                }
                if ($candidatePath -match "WindowsApps\\(python|py)") {
                    continue
                }
                if ($candidate -eq "py.exe") {
                    return @($candidatePath, "-3")
                }
                return @($candidatePath)
            }
        }
    }
    return $null
}

function Invoke-PythonRefreshIfAvailable() {
    $python = Get-UsablePythonCommand
    if (-not $python) {
        return $false
    }
    $arguments = @((Join-Path $PSScriptRoot "refresh-ai-memory.py"))
    if ($Check) { $arguments += "--check" }
    if ($NoSQLite) { $arguments += "--no-sqlite" }
    if ($RequireSQLite) { $arguments += "--require-sqlite" }
    if ($CompactLog) { $arguments += "--compact-log" }
    if ($ForceFull) { $arguments += "--force-full" }
    $pythonArgs = @()
    if ($python.Length -gt 1) {
        $pythonArgs = @($python[1..($python.Length - 1)])
    }
    $pythonOutput = & $python[0] @pythonArgs @arguments 2>&1
    $exitCode = $LASTEXITCODE
    foreach ($line in $pythonOutput) {
        Write-Host $line
    }
    exit $exitCode
}

if (Invoke-PythonRefreshIfAvailable) {
    exit 0
}

if ($CompactLog) {
    Write-Host "[ai-memory] -CompactLog requires the Python backend."
    exit 1
}

if ($ForceFull) {
    Write-Host "[ai-memory] -ForceFull requires the Python backend."
    exit 1
}

if ($Check) {
    if (-not (Test-Path $memoryStatePath)) {
        Write-Host "[ai-memory] missing ai/memory-state.json"
        exit 1
    }
    $memoryState = Get-Content -Raw $memoryStatePath | ConvertFrom-Json
    $reasons = New-Object System.Collections.Generic.List[string]
    $expectedHash = Get-CombinedHash (Get-HashInputs)
    if ($memoryState.inputsHash -ne $expectedHash) {
        $reasons.Add("inputs-hash-mismatch")
    }
    if ($memoryState.schemaVersion -ne $schemaVersion) {
        $reasons.Add("schema-version-mismatch")
    }
    $hotStats = Get-HotContextStats
    if (-not $hotStats.withinBudget) {
        $reasons.Add("hot-context-over-budget")
    }
    $indexedPaths = New-Object System.Collections.Generic.List[string]
    foreach ($indexName in @("files-index.json", "docs-index.json", "symbols-index.json", "test-index.json", "config-index.json")) {
        $indexPath = Join-Path $indexDir $indexName
        if (-not (Test-Path $indexPath)) {
            $reasons.Add("missing-$indexName")
            continue
        }
        foreach ($path in Get-JsonPaths (Read-Text $indexPath)) {
            $indexedPaths.Add($path)
        }
    }
    $missingPaths = @($indexedPaths | Sort-Object -Unique | Where-Object { -not (Test-RepoPath $_) })
    if ($missingPaths.Count -gt 0) {
        $reasons.Add("indexed-paths-missing")
    }
    if ($memoryState.derivedArtifacts.sqlite.status -eq "built" -and -not (Test-Path (Join-Path $repoRoot "ai\indexes\cold-memory.db"))) {
        $reasons.Add("missing-sqlite-db")
    }
    if ($reasons.Count -gt 0) {
        Write-Host "[ai-memory] STALE"
        foreach ($reason in $reasons) {
            Write-Host "- $reason"
        }
        foreach ($path in $missingPaths) {
            Write-Host "- missing path: $path"
        }
        Write-Host "- hot context: $($hotStats.totalLines) lines / $($hotStats.totalBytes) bytes"
        exit 1
    }
    Write-Host "[ai-memory] OK"
    Write-Host "- inputs hash: $($memoryState.inputsHash)"
    Write-Host "- hot context: $($hotStats.totalLines) lines / $($hotStats.totalBytes) bytes"
    Write-Host "- sqlite: $($memoryState.derivedArtifacts.sqlite.status)"
    exit 0
}

$generatedAt = Get-IsoNow
$coldSearchFiles = Get-ColdSearchFiles
$inputsHash = Get-CombinedHash (Get-HashInputs)
$docsIndex = Build-DocsIndex $generatedAt $coldSearchFiles
$filesIndex = Build-FilesIndex $generatedAt
$symbolsIndex = Build-SymbolsIndex $generatedAt
$testIndex = Build-TestIndex $generatedAt
$configIndex = Build-ConfigIndex $generatedAt
$docsJson = ConvertTo-RepoJson $docsIndex
$filesJson = ConvertTo-RepoJson $filesIndex
$symbolsJson = ConvertTo-RepoJson $symbolsIndex
$testJson = ConvertTo-RepoJson $testIndex
$configJson = ConvertTo-RepoJson $configIndex
$missingPaths = @(
    ($docsJson, $filesJson, $symbolsJson, $testJson, $configJson |
        ForEach-Object { Get-JsonPaths $_ } |
        Sort-Object -Unique |
        Where-Object { -not (Test-RepoPath $_) })
)
$hotStats = Get-HotContextStats
$sqliteState = Get-SqliteState -NoSQLiteMode:$NoSQLite
$memoryState = Build-MemoryState $generatedAt $inputsHash $hotStats $sqliteState $missingPaths
$memoryJson = ConvertTo-RepoJson $memoryState

Write-Utf8NoBom (Join-Path $indexDir "docs-index.json") $docsJson
Write-Utf8NoBom (Join-Path $indexDir "files-index.json") $filesJson
Write-Utf8NoBom (Join-Path $indexDir "symbols-index.json") $symbolsJson
Write-Utf8NoBom (Join-Path $indexDir "test-index.json") $testJson
Write-Utf8NoBom (Join-Path $indexDir "config-index.json") $configJson
Write-Utf8NoBom $memoryStatePath $memoryJson

Write-Host "[ai-memory] refreshed markdown truth indexes"
Write-Host "[ai-memory] hot context: $($hotStats.totalLines) lines / $($hotStats.totalBytes) bytes"
Write-Host "[ai-memory] inputs hash: $inputsHash"
if ($missingPaths.Count -gt 0) {
    Write-Host "[ai-memory] missing indexed paths detected:"
    foreach ($path in $missingPaths) {
        Write-Host "- $path"
    }
    exit 1
}
if (-not $hotStats.withinBudget) {
    Write-Host "[ai-memory] hot context exceeds budget"
    exit 1
}
if ($sqliteState.status -eq "unavailable") {
    Write-Host "[ai-memory] sqlite unavailable: $($sqliteState.error)"
    if ($RequireSQLite) {
        exit 1
    }
}
exit 0
