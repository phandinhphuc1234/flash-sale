<#
.SYNOPSIS
  Runs the bounded local verification scenarios for Feature 050.

.DESCRIPTION
  Executes only the service-owned Maven tests for the Order-to-Inventory protection boundary.
  The runner does not start Docker, call Kubernetes, read .env files, provision data, or print
  credentials. All scenarios use deterministic mocks/Testcontainers already owned by the tests.
  A single Maven invocation is used for All so the complete gate has one bounded process budget.
#>
[CmdletBinding()]
param(
    [ValidateSet('Static', 'Fault', 'Recovery', 'Bulkhead', 'Observability', 'All')]
    [string]$Scenario = 'Static',
    [ValidateRange(60, 1800)]
    [int]$TimeoutSeconds = 900
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'

function Get-RemainingMilliseconds {
    $remaining = [int][Math]::Floor(($deadline - (Get-Date)).TotalMilliseconds)
    if ($remaining -lt 1) {
        throw "Feature 050 $Scenario scenario exceeded its $TimeoutSeconds-second execution budget."
    }
    return $remaining
}

function Get-BoundedDiagnostic([AllowEmptyString()][string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return 'no diagnostic output' }
    $singleLine = ($Text -replace '[\r\n]+', ' ').Trim()
    if ($singleLine.Length -le 2000) { return $singleLine }
    return $singleLine.Substring(0, 2000) + '... [truncated]'
}

function Quote-ProcessArgument([string]$Argument) {
    if ($Argument -notmatch '[\s"]') { return $Argument }
    return '"' + $Argument.Replace('"', '\"') + '"'
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

    # PowerShell 7/.NET 6 exposes ArgumentList; Windows PowerShell/.NET Framework does not.
    # Keep a quoted Arguments fallback so this runner is repeatable on both developer shells.
    $argumentListProperty = $startInfo.PSObject.Properties['ArgumentList']
    if ($null -ne $argumentListProperty) {
        foreach ($argument in $Arguments) {
            $null = $startInfo.ArgumentList.Add([string]$argument)
        }
    } else {
        $startInfo.Arguments = (($Arguments | ForEach-Object { Quote-ProcessArgument ([string]$_) }) -join ' ')
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $started = $false
    try {
        if (-not $process.Start()) { throw "Could not start $Stage." }
        $started = $true
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit((Get-RemainingMilliseconds))) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "$Stage exceeded the Feature 050 execution budget."
        }
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "$Stage failed: $(Get-BoundedDiagnostic $stderr)"
        }
        return [pscustomobject]@{ StandardOutput = $stdout; StandardError = $stderr; ExitCode = $process.ExitCode }
    } finally {
        if ($started -and -not $process.HasExited) {
            $process.Kill($true)
            $process.WaitForExit()
        }
        $process.Dispose()
    }
}

function Assert-TrackedFile([string]$RelativePath) {
    $path = Join-Path $repoRoot ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required Feature 050 file is missing: $RelativePath"
    }
}

function Invoke-Tests([string]$Stage, [string[]]$Tests) {
    $selector = '-Dtest=' + ($Tests -join ',')
    $result = Invoke-BoundedNativeProcess -Command $mavenWrapper -Stage $Stage -Arguments @(
        '--batch-mode', '--no-transfer-progress', '-pl', 'services/order-service', '-am',
        'test', $selector, '-Dsurefire.failIfNoSpecifiedTests=false'
    )
    if ($result.StandardOutput -notmatch 'BUILD SUCCESS') {
        throw "$Stage did not report BUILD SUCCESS."
    }
    return $result.StandardOutput
}

function Invoke-Static {
    Assert-TrackedFile 'services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfiguration.java'
    Assert-TrackedFile 'services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceEventLogger.java'
    Assert-TrackedFile 'services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/InventoryRegularHoldBulkheadTests.java'
    Assert-TrackedFile 'services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/integration/OrderInventoryResilienceObservabilityTests.java'

    $adapterPath = Join-Path $repoRoot 'services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapter.java'
    $adapter = Get-Content -LiteralPath $adapterPath -Raw
    if ($adapter -notmatch 'Bulkhead\.decorateSupplier') { throw 'Bulkhead decoration is missing.' }
    if ($adapter -notmatch 'CircuitBreaker\.decorateSupplier') { throw 'Circuit Breaker decoration is missing.' }
    if ($adapter.IndexOf('Bulkhead.decorateSupplier') -lt $adapter.IndexOf('CircuitBreaker.decorateSupplier')) {
        throw 'Bulkhead must remain outside the Circuit Breaker.'
    }
    if ($adapter -notmatch 'BulkheadFullException') { throw 'Bulkhead rejection mapping is missing.' }
    Write-Output 'FEATURE_050_STATIC=PASS'
}

function Invoke-Scenario([string]$Name, [string[]]$Tests) {
    $output = Invoke-Tests "Feature 050 $Name tests" $Tests
    foreach ($marker in @('Tests run:', 'BUILD SUCCESS')) {
        if ($output -notmatch [regex]::Escape($marker)) {
            throw "Feature 050 $Name output did not contain $marker."
        }
    }
    Write-Output "FEATURE_050_$($Name.ToUpperInvariant())=PASS"
}

switch ($Scenario) {
    'Static' {
        Invoke-Static
    }
    'Fault' {
        Invoke-Scenario 'Fault' @('ResilientInventoryRegularHoldClientAdapterTests',
            'RegularPurchaseInventoryResilienceIntegrationTests')
    }
    'Recovery' {
        Invoke-Scenario 'Recovery' @('ResilientInventoryRegularHoldClientAdapterTests',
            'RegularPurchaseRecoveryIntegrationTests')
    }
    'Bulkhead' {
        Invoke-Scenario 'Bulkhead' @('InventoryRegularHoldBulkheadTests')
    }
    'Observability' {
        Invoke-Scenario 'Observability' @('OrderInventoryResilienceObservabilityTests')
    }
    'All' {
        Invoke-Static
        $output = Invoke-Tests 'Feature 050 complete local gate' @(
            'OrderInventoryResilienceConfigurationTests',
            'ResilientInventoryRegularHoldClientAdapterTests',
            'RegularPurchaseInventoryResilienceIntegrationTests',
            'RegularPurchaseRecoveryIntegrationTests',
            'InventoryRegularHoldBulkheadTests',
            'OrderInventoryResilienceObservabilityTests'
        )
        foreach ($marker in @(
            'FEATURE_050_OPEN_REJECTION', 'FEATURE_050_RECOVERY', 'FEATURE_050_IDENTITY_RECOVERY',
            'FEATURE_050_BULKHEAD', 'FEATURE_050_READINESS', 'Tests run:', 'BUILD SUCCESS'
        )) {
            if ($output -notmatch [regex]::Escape($marker)) {
                throw "Feature 050 complete output did not contain $marker."
            }
        }
        Write-Output 'FEATURE_050_FAULT=PASS'
        Write-Output 'FEATURE_050_RECOVERY=PASS'
        Write-Output 'FEATURE_050_BULKHEAD=PASS'
        Write-Output 'FEATURE_050_OBSERVABILITY=PASS'
        Write-Output 'FEATURE_050_ALL=PASS'
    }
}

Write-Output "Feature 050 $Scenario runner completed without reading secrets or changing external state."
