param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ArgsList
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
    return $null
}

$python = Get-UsablePythonCommand
if (-not $python) {
    Write-Error "Python 3 was not found on PATH."
}

$scriptPath = Join-Path $PSScriptRoot "claude-orchestrator.py"
$pythonArgs = @()
if ($python.Length -gt 1) {
    $pythonArgs = @($python[1..($python.Length - 1)])
}

& $python[0] @pythonArgs $scriptPath @ArgsList
exit $LASTEXITCODE
