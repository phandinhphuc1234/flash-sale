<#
.SYNOPSIS
  Runs bounded local validation scenarios for Feature 044.

.DESCRIPTION
  G1 implements the Contracts scenario only. It verifies the contract Maven module, starts the
  local Kafka/Schema Registry pair unless -SkipTopology is supplied, provisions approved topics,
  and idempotently registers all main and DLT subjects. Later groups extend this same runner with
  Start, Paid, Failed, Replay, LateSuccess, and All without duplicating topology logic.
#>
[CmdletBinding()]
param(
    [ValidateSet('Contracts')]
    [string]$Scenario = 'Contracts',
    [switch]$SkipTopology,
    [ValidateRange(60, 1800)]
    [int]$TimeoutSeconds = 600,
    [string]$SchemaRegistryUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$dockerRoot = Join-Path $repoRoot 'infra\docker'
$composeFile = Join-Path $dockerRoot 'compose.yml'
$envFile = Join-Path $dockerRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    $envFile = Join-Path $dockerRoot '.env.example'
}
$topicScript = Join-Path $dockerRoot 'kafka\init-purchase-saga-topics.sh'
$schemaScript = Join-Path $dockerRoot 'schema-registry\register-purchase-saga-schemas.ps1'
$mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
if ([string]::IsNullOrWhiteSpace($SchemaRegistryUrl)) {
    $SchemaRegistryUrl = if ($env:FEATURE_044_SCHEMA_REGISTRY_URL) {
        $env:FEATURE_044_SCHEMA_REGISTRY_URL
    } else {
        'http://127.0.0.1:8081'
    }
}

function Get-RemainingMilliseconds {
    $remaining = [int][Math]::Floor(($deadline - (Get-Date)).TotalMilliseconds)
    if ($remaining -lt 1) {
        throw "Feature 044 Contracts scenario exceeded its $TimeoutSeconds-second execution budget."
    }
    return $remaining
}

function Get-BoundedDiagnostic([AllowEmptyString()][string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return 'no diagnostic output' }
    if ($Text.Length -le 2000) { return $Text }
    return $Text.Substring(0, 2000) + '... [truncated]'
}

function Invoke-BoundedNativeProcess {
    param(
        [Parameter(Mandatory = $true)] [string]$Command,
        [Parameter(Mandatory = $true)] [string[]]$Arguments,
        [Parameter(Mandatory = $true)] [string]$Stage
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Command
    $startInfo.WorkingDirectory = $repoRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        $null = $startInfo.ArgumentList.Add([string]$argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw "Could not start $Stage." }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit((Get-RemainingMilliseconds))) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "$Stage exceeded the Feature 044 execution budget."
        }
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
        $stderr = $stderrTask.GetAwaiter().GetResult().Trim()
        if ($process.ExitCode -ne 0) {
            throw "$Stage failed: $(Get-BoundedDiagnostic $stderr)"
        }
        if (-not [string]::IsNullOrWhiteSpace($stdout)) { Write-Output $stdout }
        if (-not [string]::IsNullOrWhiteSpace($stderr)) { Write-Output (Get-BoundedDiagnostic $stderr) }
    } finally {
        if (-not $process.HasExited) {
            $process.Kill($true)
            $process.WaitForExit()
        }
        $process.Dispose()
    }
}

function Invoke-Compose([string[]]$Arguments, [string]$Stage) {
    Invoke-BoundedNativeProcess -Command 'docker' -Stage $Stage -Arguments (@(
        'compose', '--env-file', $envFile, '-f', $composeFile
    ) + $Arguments)
}

function Wait-SchemaRegistry {
    do {
        try {
            $response = Invoke-WebRequest -Uri "$($SchemaRegistryUrl.TrimEnd('/'))/subjects" -UseBasicParsing -TimeoutSec 4
            if ([int]$response.StatusCode -eq 200) { return }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw 'Timed out waiting for local Schema Registry readiness.'
}

function Invoke-TopicProvisioning {
    $containerId = (& docker compose --env-file $envFile -f $composeFile ps -q kafka).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw 'The local Kafka container is not running.'
    }

    $temporaryScript = Join-Path ([IO.Path]::GetTempPath()) ('feature-044-topics-' +
        [Guid]::NewGuid().ToString('N') + '.sh')
    $containerScript = '/tmp/feature-044-init-purchase-saga-topics.sh'
    try {
        $lineFeed = [string][char]10
        $carriageReturn = [string][char]13
        $content = [IO.File]::ReadAllText($topicScript)
        $content = $content.Replace($carriageReturn + $lineFeed, $lineFeed)
        $content = $content.Replace($carriageReturn, $lineFeed)
        [IO.File]::WriteAllText($temporaryScript, $content, [Text.UTF8Encoding]::new($false))
        Invoke-BoundedNativeProcess -Command 'docker' -Stage 'copy Feature 044 topic script' -Arguments @(
            'cp', $temporaryScript, ($containerId + ':' + $containerScript)
        )
        Invoke-BoundedNativeProcess -Command 'docker' -Stage 'provision Feature 044 Kafka topics' -Arguments @(
            'exec', $containerId, 'bash', $containerScript
        )
    } finally {
        if (Test-Path -LiteralPath $temporaryScript) {
            Remove-Item -LiteralPath $temporaryScript -Force
        }
    }
}

if ($Scenario -ne 'Contracts') {
    throw "Scenario '$Scenario' is not implemented until its approved task group."
}

Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage 'Feature 044 contract Maven verify' -Arguments @(
    '--batch-mode', '--no-transfer-progress', '-pl', 'contracts/kafka-avro-contracts', '-am', 'verify'
)

if (-not $SkipTopology) {
    Invoke-Compose @('up', '-d', 'kafka', 'schema-registry') 'start local Kafka and Schema Registry'
    Wait-SchemaRegistry
    Invoke-TopicProvisioning
    Invoke-BoundedNativeProcess -Command 'pwsh' -Stage 'register Feature 044 schemas' -Arguments @(
        '-NoLogo', '-NoProfile', '-File', $schemaScript, '-SchemaRegistryUrl', $SchemaRegistryUrl
    )
}

Write-Output 'FEATURE_044_CONTRACTS=PASS'
