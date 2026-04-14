param(
    [Parameter(Mandatory = $true)]
    [string]$Query,
    [int]$Limit = 8,
    [string]$DatabasePath = "",
    [string]$Tier = "",
    [string]$Kind = "",
    [string[]]$Path = @(),
    [switch]$Json
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$defaultDb = if ($DatabasePath) { $DatabasePath } else { Join-Path $repoRoot "ai\indexes\cold-memory.db" }

function Get-UsablePythonCommand() {
    foreach ($name in @("py", "python", "python3")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if (-not $command) {
            continue
        }
        if ($command.Source -match "WindowsApps\\python") {
            continue
        }
        if ($name -eq "py") {
            return @($command.Source, "-3")
        }
        return @($command.Source)
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

function Invoke-PythonQueryIfAvailable() {
    $python = Get-UsablePythonCommand
    if (-not $python) {
        return $false
    }
    $scriptPath = Join-Path $PSScriptRoot "query-ai-memory.py"
    $arguments = @($scriptPath, $Query, "--limit", "$Limit")
    if ($DatabasePath) {
        $arguments += @("--db", $DatabasePath)
    }
    if ($Tier) {
        $arguments += @("--tier", $Tier)
    }
    if ($Kind) {
        $arguments += @("--kind", $Kind)
    }
    foreach ($pathGlob in $Path) {
        if ($pathGlob) {
            $arguments += @("--path", $pathGlob)
        }
    }
    if ($Json) {
        $arguments += "--json"
    }
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

function Get-ColdSearchFiles() {
    $files = New-Object System.Collections.Generic.List[string]
    foreach ($relative in @(
        "AGENTS.md",
        "CONTRIBUTING.md",
        "MAINTENANCE.md",
        "MIGRATION.md",
        "README.md",
        "RELEASE.md",
        "TODO.md"
    )) {
        $path = Join-Path $repoRoot $relative
        if (Test-Path $path) {
            $files.Add($path)
        }
    }
    if (Test-Path (Join-Path $repoRoot "docs")) {
        foreach ($file in Get-ChildItem (Join-Path $repoRoot "docs") -Filter *.md -File -Recurse) {
            $files.Add($file.FullName)
        }
    }
    if (Test-Path (Join-Path $repoRoot "ai")) {
        foreach ($file in Get-ChildItem (Join-Path $repoRoot "ai") -Filter *.md -File -Recurse) {
            $files.Add($file.FullName)
        }
        $logRoot = Join-Path $repoRoot "ai\log"
        if (Test-Path $logRoot) {
            foreach ($file in Get-ChildItem $logRoot -Filter *.jsonl -File -Recurse) {
                $files.Add($file.FullName)
            }
        }
    }
    return @($files | Sort-Object -Unique)
}

function Test-FallbackTier([string]$relativePath, [switch]$IncludeArchiveFallback) {
    $pathTier = "cold"
    if ($relativePath -eq "ai/core/agent-invariants.md" -or
        $relativePath -eq "ai/core/repo-purpose.md" -or
        $relativePath -eq "ai/state/current-state.md" -or
        $relativePath -eq "ai/state/handoff.md") {
        $pathTier = "hot"
    } elseif ($relativePath -eq "ai/state/recent-validations.md" -or $relativePath -like "ai/log/events.jsonl") {
        $pathTier = "warm"
    } elseif ($relativePath -like "ai/log/archive/*.jsonl") {
        $pathTier = "archive"
    }
    if (-not $Tier) {
        return $IncludeArchiveFallback -or $pathTier -ne "archive"
    }
    $tiers = @($Tier -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    return $tiers -contains $pathTier
}

function Test-FallbackKind([string]$relativePath) {
    if (-not $Kind) {
        return $true
    }
    $kinds = @($Kind -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $pathKind = "document"
    if ($relativePath -like "ai/core/*") {
        $pathKind = "ai-core"
    } elseif ($relativePath -like "ai/orchestrator/*") {
        $pathKind = "ai-orchestrator"
    } elseif ($relativePath -like "ai/state/*") {
        $pathKind = "ai-state"
    } elseif ($relativePath -like "ai/log/archive/*-summary.md") {
        $pathKind = "ai-archive-summary"
    } elseif ($relativePath -like "ai/log/archive/*.jsonl") {
        $pathKind = "ai-log-archive"
    } elseif ($relativePath -eq "ai/log/events.jsonl") {
        $pathKind = "ai-log"
    } elseif ($relativePath -eq "CONTRIBUTING.md") {
        $pathKind = "process-doc"
    } elseif ($relativePath -eq "RELEASE.md") {
        $pathKind = "release-doc"
    }
    return $kinds -contains $pathKind
}

function Test-FallbackPath([string]$relativePath) {
    if (-not $Path -or $Path.Count -eq 0) {
        return $true
    }
    foreach ($pattern in $Path) {
        if ($relativePath -like $pattern) {
            return $true
        }
    }
    return $false
}

function Find-FallbackMatches([switch]$IncludeArchiveFallback) {
    $matches = @()
    foreach ($path in Get-ColdSearchFiles) {
        $rootUri = New-Object System.Uri(((Resolve-Path $repoRoot).Path.TrimEnd("\") + "\"))
        $targetUri = New-Object System.Uri((Resolve-Path $path).Path)
        $relative = $rootUri.MakeRelativeUri($targetUri).ToString().Replace("\", "/")
        if (-not (Test-FallbackTier $relative -IncludeArchiveFallback:$IncludeArchiveFallback) -or
            -not (Test-FallbackKind $relative) -or
            -not (Test-FallbackPath $relative)) {
            continue
        }
        $hits = Select-String -Path $path -Pattern $Query -SimpleMatch -ErrorAction SilentlyContinue
        foreach ($hit in $hits) {
            $matches += [ordered]@{
                path = $relative
                lineNumber = $hit.LineNumber
                summary = $hit.Line.Trim()
            }
            if ($matches.Count -ge $Limit) {
                break
            }
        }
        if ($matches.Count -ge $Limit) {
            break
        }
    }
    return $matches
}

if (Invoke-PythonQueryIfAvailable) {
    exit 0
}

$matches = Find-FallbackMatches
if ($matches.Count -eq 0 -and -not $Tier) {
    $matches = Find-FallbackMatches -IncludeArchiveFallback
}

if ($matches.Count -eq 0) {
    Write-Host "[ai-search] no matches"
    if (-not (Test-Path $defaultDb)) {
        Write-Host "[ai-search] sqlite database not available; using text fallback only."
    }
    exit 1
}

if (-not (Test-Path $defaultDb)) {
    Write-Host "[ai-search] sqlite database not available; using text fallback results."
}

$index = 1
foreach ($match in $matches) {
    if ($Json) {
        continue
    }
    Write-Host ("{0}. {1}:{2}" -f $index, $match.path, $match.lineNumber)
    Write-Host ("   hit: {0}" -f $match.summary)
    $index += 1
}

if ($Json) {
    $matches | ConvertTo-Json -Depth 5
}
