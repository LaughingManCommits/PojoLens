param(
    [string]$Report = "",
    [int]$QueryIterations = 3
)

$ErrorActionPreference = "Stop"

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

$python = Get-UsablePythonCommand
if (-not $python) {
    Write-Host "[ai-memory-benchmark] Python is required."
    exit 1
}

$scriptPath = Join-Path $PSScriptRoot "benchmark-ai-memory.py"
$arguments = @($scriptPath, "--query-iterations", "$QueryIterations")
if ($Report) {
    $arguments += @("--report", $Report)
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
