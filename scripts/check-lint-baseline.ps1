param(
    [string]$Report = "target/checkstyle-result.xml",
    [string]$Baseline = "scripts/checkstyle-baseline.txt",
    [string]$RepoRoot = ".",
    [switch]$WriteBaseline,
    [int]$MaxPrint = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RawPath,
        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $normalized = $RawPath.Replace("\", "/")
    try {
        $resolvedRoot = (Resolve-Path -Path $RootPath).Path.Replace("\", "/")
        $resolvedFile = (Resolve-Path -Path $RawPath).Path.Replace("\", "/")
        $prefix = "$resolvedRoot/"
        if ($resolvedFile.StartsWith($prefix)) {
            return $resolvedFile.Substring($prefix.Length)
        }
    } catch {
    }

    foreach ($marker in @("src/main/java/", "src/test/java/")) {
        $idx = $normalized.IndexOf($marker)
        if ($idx -ge 0) {
            return $normalized.Substring($idx)
        }
    }

    return $normalized
}

function Normalize-ViolationPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string]$Source
    )

    if ($Source -eq "com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocPackageCheck") {
        $separator = [System.IO.Path]::DirectorySeparatorChar
        $packageDir = Split-Path -Path ($FilePath.Replace("/", $separator)) -Parent
        if ([string]::IsNullOrWhiteSpace($packageDir)) {
            return "package-info.java"
        }
        return ($packageDir.Replace("\", "/").TrimEnd("/")) + "/package-info.java"
    }

    return $FilePath
}

function Load-ViolationKeys {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ReportPath,
        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    [xml]$reportDoc = Get-Content -Path $ReportPath -Raw
    $keys = New-Object "System.Collections.Generic.HashSet[string]"

    $fileNodes = $reportDoc.SelectNodes("/checkstyle/file")
    foreach ($fileNode in $fileNodes) {
        $rawFileName = [string]$fileNode.GetAttribute("name")
        if ([string]::IsNullOrWhiteSpace($rawFileName)) {
            continue
        }
        $fileName = Normalize-RelativePath -RawPath $rawFileName -RootPath $RootPath
        $errorNodes = $fileNode.SelectNodes("./error")
        foreach ($errorNode in $errorNodes) {
            $line = [string]$errorNode.GetAttribute("line")
            if ([string]::IsNullOrWhiteSpace($line)) {
                $line = "0"
            }
            $column = [string]$errorNode.GetAttribute("column")
            if ([string]::IsNullOrWhiteSpace($column)) {
                $column = "0"
            }
            $source = [string]$errorNode.GetAttribute("source")
            $normalizedPath = Normalize-ViolationPath -FilePath $fileName -Source $source
            $key = "$normalizedPath|$line|$column|$source"
            $null = $keys.Add($key)
        }
    }

    return ,$keys
}

if (-not (Test-Path -Path $Report)) {
    Write-Error "Report not found: $Report"
}

$reportKeys = Load-ViolationKeys -ReportPath $Report -RootPath $RepoRoot

if ($WriteBaseline) {
    $baselineDir = Split-Path -Path $Baseline -Parent
    if (-not [string]::IsNullOrWhiteSpace($baselineDir)) {
        New-Item -Path $baselineDir -ItemType Directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllLines($Baseline, ($reportKeys | Sort-Object))
    Write-Host "Wrote baseline: $Baseline ($($reportKeys.Count) entries)"
    exit 0
}

if (-not (Test-Path -Path $Baseline)) {
    Write-Error "Baseline file missing: $Baseline. Generate it with -WriteBaseline."
}

$baselineKeys = New-Object "System.Collections.Generic.HashSet[string]"
foreach ($line in Get-Content -Path $Baseline) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
        continue
    }
    $null = $baselineKeys.Add($trimmed)
}

$newViolations = New-Object System.Collections.Generic.List[string]
foreach ($item in $reportKeys) {
    if (-not $baselineKeys.Contains($item)) {
        $newViolations.Add($item)
    }
}

$fixedViolations = New-Object System.Collections.Generic.List[string]
foreach ($item in $baselineKeys) {
    if (-not $reportKeys.Contains($item)) {
        $fixedViolations.Add($item)
    }
}

Write-Host ("Lint baseline check summary: report={0} baseline={1} new={2} fixed={3}" -f `
    $reportKeys.Count, $baselineKeys.Count, $newViolations.Count, $fixedViolations.Count)

if ($fixedViolations.Count -gt 0) {
    Write-Host "Detected resolved violations still present in baseline. Refresh with -WriteBaseline."
}

if ($newViolations.Count -gt 0) {
    Write-Host "New checkstyle violations detected:"
    $newViolations | Sort-Object | Select-Object -First $MaxPrint | ForEach-Object {
        Write-Host "  + $_"
    }
    if ($newViolations.Count -gt $MaxPrint) {
        Write-Host ("  ... and {0} more" -f ($newViolations.Count - $MaxPrint))
    }
    exit 1
}

Write-Host "No new checkstyle violations."
exit 0
