<#
.SYNOPSIS
  Runs bounded local validation scenarios for Feature 044.

.DESCRIPTION
  Contracts verifies the contract Maven module and local Kafka/Schema Registry provisioning.
  Start runs the Order-owned Purchase Saga start gate through the service module's real PostgreSQL
  integration tests, proving one durable Saga and PaymentRequested outbox intent under replay and
  concurrency. Paid verifies the affected Order and Flash Sale modules together, including the
  PaymentSucceeded -> reservation confirmation -> OrderConfirmed path. Later groups extend this
  same runner with Failed, Replay, LateSuccess, and All.
#>
[CmdletBinding()]
param(
    [ValidateSet('Contracts', 'Start', 'Paid', 'Failed', 'Replay', 'LateSuccess', 'All')]
    [string]$Scenario = 'Contracts',
    [switch]$SkipTopology,
    # The aggregate scenario includes the complete monorepo reactor. On a
    # resource-constrained developer workstation that gate can legitimately
    # need longer than the affected-module scenarios, while still remaining
    # bounded and fail-fast.
    [ValidateRange(60, 3600)]
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
        throw "Feature 044 $Scenario scenario exceeded its $TimeoutSeconds-second execution budget."
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

function Invoke-AffectedModuleVerify([string]$Stage) {
    Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage $Stage -Arguments @(
        '--batch-mode', '--no-transfer-progress', '-pl', 'services/order-service,services/flashsale-service',
        '-am', 'verify'
    )
}

function Invoke-FocusedTests([string]$Stage, [string[]]$Tests) {
    $testSelector = '-Dtest=' + ($Tests -join ',')
    Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage $Stage -Arguments @(
        '--batch-mode', '--no-transfer-progress', '-pl', 'services/order-service,services/flashsale-service',
        '-am', $testSelector, '-Dsurefire.failIfNoSpecifiedTests=false', 'test'
    )
}

function Invoke-ContractValidation {
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
}

switch ($Scenario) {
    'Contracts' {
        Invoke-ContractValidation
        Write-Output 'FEATURE_044_CONTRACTS=PASS'
    }
    'Start' {
        # The service-owned Testcontainers integration gate exercises the application boundary and
        # its PostgreSQL transaction. The runner does not write another service's database or emit
        # an artificial Kafka record; it only reports the bounded test result.
        Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage 'Feature 044 Order Saga start Maven verify' -Arguments @(
            '--batch-mode', '--no-transfer-progress', '-pl', 'services/order-service', '-am', 'verify'
        )
        Write-Output 'FEATURE_044_START=PASS'
    }
    'Paid' {
        # This is an affected-module gate rather than a cross-service fixture. The Order tests
        # exercise PaymentSucceeded, confirm-command persistence, and Order terminalization; the
        # Flash Sale tests exercise the reservation confirmation transaction and outcome outbox.
        # Both boundaries use their own Testcontainers/database setup and no SQL is issued here.
        Invoke-AffectedModuleVerify 'Feature 044 Paid affected-module verify'
        Write-Output 'FEATURE_044_PAID=PASS'
    }
    'Failed' {
        # The failure gate runs both participants in one reactor. Order verifies the three
        # PaymentFailed reason branches, durable release command, and terminal Order/Saga facts;
        # Flash Sale verifies release/expiry idempotency, Redis Lua reconciliation, and its result
        # outbox. No SQL or synthetic Kafka record is created by this runner.
        Invoke-AffectedModuleVerify 'Feature 044 PaymentFailed release affected-module verify'
        Write-Output 'FEATURE_044_FAILED=PASS'
    }
    'Replay' {
        # Replay is test-backed: each test drives a service-owned transaction, inbox, outbox, or
        # Redis boundary. The operator script does not create cross-service rows or Kafka records.
        Invoke-FocusedTests 'Feature 044 replay/idempotency tests' @(
            'AcceptedPurchasePersistenceIntegrationTests',
            'AcceptedPurchaseConcurrencyIntegrationTests',
            'PaymentFailureTransitionIntegrationTests',
            'PurchaseReservationConfirmationPersistenceIntegrationTests',
            'ReservationIdempotencyConcurrencyIntegrationTests',
            'ReservationReconciliationIntegrationTests',
            'ReservationReleaseRedisIntegrationTests',
            'ReservationRedisFailureIntegrationTests',
            'ReservationReconciliationServiceTests'
        )
        Write-Output 'FEATURE_044_REPLAY=PASS'
    }
    'LateSuccess' {
        # This gate proves success dominance, terminal-to-manual-review correction, stable
        # correction identity, and replay using service-owned integration fixtures only.
        Invoke-FocusedTests 'Feature 044 late-success tests' @(
            'PurchaseSagaLateSuccessTests',
            'OrderPaymentReviewRequiredMapperTests',
            'LatePaymentCorrectionIntegrationTests'
        )
        Write-Output 'FEATURE_044_LATE_SUCCESS=PASS'
    }
    'All' {
        # Aggregate gate: contracts, affected modules, replay/late-success focused scenarios, and
        # the complete reactor. Cloud image promotion remains a separate reviewed step.
        Invoke-ContractValidation
        Invoke-AffectedModuleVerify 'Feature 044 affected-module verify'
        Invoke-FocusedTests 'Feature 044 replay/idempotency tests' @(
            'AcceptedPurchasePersistenceIntegrationTests',
            'AcceptedPurchaseConcurrencyIntegrationTests',
            'PaymentFailureTransitionIntegrationTests',
            'PurchaseReservationConfirmationPersistenceIntegrationTests',
            'ReservationIdempotencyConcurrencyIntegrationTests',
            'ReservationReconciliationIntegrationTests',
            'ReservationReleaseRedisIntegrationTests',
            'ReservationRedisFailureIntegrationTests',
            'ReservationReconciliationServiceTests'
        )
        Write-Output 'FEATURE_044_CONTRACTS=PASS'
        Write-Output 'FEATURE_044_START=PASS'
        Write-Output 'FEATURE_044_PAID=PASS'
        Write-Output 'FEATURE_044_FAILED=PASS'
        Write-Output 'FEATURE_044_REPLAY=PASS'
        Invoke-FocusedTests 'Feature 044 late-success tests' @(
            'PurchaseSagaLateSuccessTests',
            'OrderPaymentReviewRequiredMapperTests',
            'LatePaymentCorrectionIntegrationTests'
        )
        Write-Output 'FEATURE_044_LATE_SUCCESS=PASS'
        Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage 'Feature 044 full reactor verify' -Arguments @(
            '--batch-mode', '--no-transfer-progress', 'clean', 'verify'
        )
        Write-Output 'FEATURE_044_MODULES=PASS'
        Write-Output 'FEATURE_044_MONOREPO=PASS'
        Write-Output 'FEATURE_044_LOCAL_GATE=PASS'
    }
}
