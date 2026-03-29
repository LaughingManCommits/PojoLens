param(
    [Parameter(ParameterSetName = "run", Mandatory = $true, Position = 0)]
    [string]$Prompt,
    [Parameter(ParameterSetName = "end")]
    [switch]$EndSession,
    [string]$SessionId = "",
    [string]$Model = "",
    [switch]$Json,
    [string]$StatePath = "ai/state/claude-session-usage.json",
    [string]$LedgerPath = "ai/log/claude-usage.jsonl"
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$path) {
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }
    $repoRoot = Split-Path -Parent $PSScriptRoot
    return (Join-Path $repoRoot $path)
}

function Ensure-ParentDirectory([string]$path) {
    $parent = Split-Path -Parent $path
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}

function Write-Utf8NoBom([string]$path, [string]$content) {
    Ensure-ParentDirectory $path
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $content, $encoding)
}

function Read-State([string]$path) {
    if (-not (Test-Path $path)) {
        return [pscustomobject]@{
            activeSessionId = $null
            sessions = @()
        }
    }
    $raw = Get-Content -Raw $path
    if (-not $raw.Trim()) {
        return [pscustomobject]@{
            activeSessionId = $null
            sessions = @()
        }
    }
    $parsed = $raw | ConvertFrom-Json
    if (-not $parsed.sessions) {
        $parsed | Add-Member -NotePropertyName sessions -NotePropertyValue @() -Force
    }
    return $parsed
}

function Save-State([string]$path, [object]$state) {
    $json = $state | ConvertTo-Json -Depth 20
    Write-Utf8NoBom $path $json
}

function Get-Session([object]$state, [string]$id) {
    foreach ($session in $state.sessions) {
        if ($session.sessionId -eq $id) {
            return $session
        }
    }
    return $null
}

function Ensure-Session([object]$state, [string]$id, [string]$now) {
    $session = Get-Session $state $id
    if ($null -ne $session) {
        return $session
    }
    $session = [pscustomobject]@{
        sessionId = $id
        startedAt = $now
        lastUpdatedAt = $now
        endedAt = $null
        model = $null
        lastClaudeSessionId = $null
        turns = 0
        totalInputTokens = 0
        totalOutputTokens = 0
        totalCacheReadInputTokens = 0
        totalCacheCreationInputTokens = 0
        totalCostUsd = 0.0
        totalDurationMs = 0
    }
    $state.sessions += $session
    return $session
}

function Get-ModelFromPayload([object]$payload) {
    if (-not $payload.modelUsage) {
        return $null
    }
    foreach ($property in $payload.modelUsage.PSObject.Properties) {
        if ($property.Name) {
            return $property.Name
        }
    }
    return $null
}

function Get-Int64OrZero([object]$value) {
    if ($null -eq $value) {
        return [int64]0
    }
    return [int64]$value
}

function Get-DoubleOrZero([object]$value) {
    if ($null -eq $value) {
        return [double]0
    }
    return [double]$value
}

function Append-LedgerEntry([string]$path, [hashtable]$entry) {
    Ensure-ParentDirectory $path
    Add-Content -Path $path -Value ($entry | ConvertTo-Json -Compress)
}

$stateFile = Resolve-RepoPath $StatePath
$ledgerFile = Resolve-RepoPath $LedgerPath
$state = Read-State $stateFile
$now = (Get-Date).ToString("o")

if ($EndSession) {
    $targetSessionId = $SessionId
    if (-not $targetSessionId) {
        $targetSessionId = $state.activeSessionId
    }
    if (-not $targetSessionId) {
        Write-Host "[claude-usage] no active session to end."
        exit 1
    }
    $targetSession = Get-Session $state $targetSessionId
    if ($null -eq $targetSession) {
        Write-Host "[claude-usage] session not found: $targetSessionId"
        exit 1
    }
    $targetSession.endedAt = $now
    $targetSession.lastUpdatedAt = $now
    if ($state.activeSessionId -eq $targetSessionId) {
        $state.activeSessionId = $null
    }
    Save-State $stateFile $state
    Append-LedgerEntry $ledgerFile ([ordered]@{
            ts = $now
            type = "session_end"
            sessionId = $targetSessionId
            turns = $targetSession.turns
            totalInputTokens = $targetSession.totalInputTokens
            totalOutputTokens = $targetSession.totalOutputTokens
            totalCacheReadInputTokens = $targetSession.totalCacheReadInputTokens
            totalCacheCreationInputTokens = $targetSession.totalCacheCreationInputTokens
            totalCostUsd = [Math]::Round([double]$targetSession.totalCostUsd, 12)
            totalDurationMs = $targetSession.totalDurationMs
            model = $targetSession.model
        })
    Write-Host ("[claude-usage] ended session {0}" -f $targetSessionId)
    Write-Host ("[claude-usage] totals: turns={0}, in={1}, out={2}, cacheRead={3}, cacheCreate={4}, costUsd={5}, durationMs={6}" -f `
            $targetSession.turns, `
            $targetSession.totalInputTokens, `
            $targetSession.totalOutputTokens, `
            $targetSession.totalCacheReadInputTokens, `
            $targetSession.totalCacheCreationInputTokens, `
            [Math]::Round([double]$targetSession.totalCostUsd, 12), `
            $targetSession.totalDurationMs)
    exit 0
}

$activeSessionId = $SessionId
if (-not $activeSessionId) {
    if ($state.activeSessionId) {
        $activeSessionId = $state.activeSessionId
    } else {
        $activeSessionId = [guid]::NewGuid().ToString()
    }
}
$session = Ensure-Session $state $activeSessionId $now

$claudeCommand = Get-Command claude -ErrorAction SilentlyContinue
if (-not $claudeCommand) {
    Write-Host "[claude-usage] claude command not found."
    exit 1
}

$arguments = @("-p", "--output-format", "json")
if ($Model) {
    $arguments += @("--model", $Model)
}
$arguments += $Prompt

$rawOutput = & claude @arguments 2>&1
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    foreach ($line in $rawOutput) {
        Write-Host $line
    }
    exit $exitCode
}

$rawText = ($rawOutput -join "`n").Trim()
try {
    $payload = $rawText | ConvertFrom-Json
} catch {
    Write-Host "[claude-usage] failed to parse claude JSON output."
    Write-Host $rawText
    exit 1
}

$claudeSessionId = if ($payload.session_id) { [string]$payload.session_id } else { $null }
$reportedSessionId = $activeSessionId
$session = Ensure-Session $state $reportedSessionId $now

$usage = $payload.usage
$inputTokens = if ($usage) { Get-Int64OrZero $usage.input_tokens } else { 0 }
$outputTokens = if ($usage) { Get-Int64OrZero $usage.output_tokens } else { 0 }
$cacheReadTokens = if ($usage) { Get-Int64OrZero $usage.cache_read_input_tokens } else { 0 }
$cacheCreationTokens = if ($usage) { Get-Int64OrZero $usage.cache_creation_input_tokens } else { 0 }
$costUsd = Get-DoubleOrZero $payload.total_cost_usd
$durationMs = Get-Int64OrZero $payload.duration_ms
$resolvedModel = if ($Model) { $Model } else { Get-ModelFromPayload $payload }

$session.turns = [int]$session.turns + 1
$session.totalInputTokens = [int64]$session.totalInputTokens + $inputTokens
$session.totalOutputTokens = [int64]$session.totalOutputTokens + $outputTokens
$session.totalCacheReadInputTokens = [int64]$session.totalCacheReadInputTokens + $cacheReadTokens
$session.totalCacheCreationInputTokens = [int64]$session.totalCacheCreationInputTokens + $cacheCreationTokens
$session.totalCostUsd = [Math]::Round(([double]$session.totalCostUsd + $costUsd), 12)
$session.totalDurationMs = [int64]$session.totalDurationMs + $durationMs
$session.lastUpdatedAt = $now
if (-not $session.model -and $resolvedModel) {
    $session.model = $resolvedModel
}
if ($claudeSessionId) {
    if (-not $session.PSObject.Properties["lastClaudeSessionId"]) {
        $session | Add-Member -NotePropertyName lastClaudeSessionId -NotePropertyValue $null -Force
    }
    $session.lastClaudeSessionId = $claudeSessionId
}

$state.activeSessionId = $reportedSessionId
Save-State $stateFile $state

Append-LedgerEntry $ledgerFile ([ordered]@{
        ts = $now
        type = "turn"
        sessionId = $reportedSessionId
        claudeSessionId = $claudeSessionId
        turnIndex = $session.turns
        model = $session.model
        inputTokens = $inputTokens
        outputTokens = $outputTokens
        cacheReadInputTokens = $cacheReadTokens
        cacheCreationInputTokens = $cacheCreationTokens
        totalCostUsd = [Math]::Round($costUsd, 12)
        durationMs = $durationMs
        promptChars = $Prompt.Length
    })

Write-Host ("[claude-usage] session={0} claudeSession={1} turn={2} in={3} out={4} cacheRead={5} cacheCreate={6} costUsd={7}" -f `
        $reportedSessionId, `
        $claudeSessionId, `
        $session.turns, `
        $inputTokens, `
        $outputTokens, `
        $cacheReadTokens, `
        $cacheCreationTokens, `
        [Math]::Round($costUsd, 12))

if ($Json) {
    $payload | ConvertTo-Json -Depth 30
    exit 0
}

if ($payload.result) {
    Write-Output $payload.result
}
