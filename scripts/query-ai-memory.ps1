param(
    [Parameter(Mandatory = $true)]
    [string]$Query,
    [int]$Limit = 8,
    [string]$DatabasePath = ""
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

if (Invoke-PythonQueryIfAvailable) {
    exit 0
}

$matches = @()
foreach ($path in Get-ColdSearchFiles) {
    $hits = Select-String -Path $path -Pattern $Query -SimpleMatch -ErrorAction SilentlyContinue
    foreach ($hit in $hits) {
        $matches += $hit
        if ($matches.Count -ge $Limit) {
            break
        }
    }
    if ($matches.Count -ge $Limit) {
        break
    }
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
    $rootUri = New-Object System.Uri(((Resolve-Path $repoRoot).Path.TrimEnd("\") + "\"))
    $targetUri = New-Object System.Uri((Resolve-Path $match.Path).Path)
    $relative = $rootUri.MakeRelativeUri($targetUri).ToString().Replace("\", "/")
    Write-Host ("{0}. {1}:{2}" -f $index, $relative, $match.LineNumber)
    Write-Host ("   hit: {0}" -f $match.Line.Trim())
    $index += 1
}
