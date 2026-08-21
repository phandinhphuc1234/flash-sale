<#
.SYNOPSIS
  Validate and, only when explicitly requested, provision Phase 15 cloud Secrets.

.DESCRIPTION
  The default mode is validation-only. It reads key names and local file metadata from the
  ignored infra/docker/.env and never prints secret values. -Apply creates short-lived filtered
  Secret input files and pipes kubectl's generated manifest directly to kubectl apply. -EnableStripe
  is intentionally opt-in because Stripe is disabled in the Phase 15 cloud ConfigMap.
#>
[CmdletBinding()]
param(
  [string]$Namespace = "flash-sale",
  [string]$SourceEnvFile = "",
  [string]$JwtKeyDirectory = "",
  [switch]$EnableStripe,
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
if ([string]::IsNullOrWhiteSpace($SourceEnvFile)) {
  $SourceEnvFile = Join-Path $repoRoot "infra\docker\.env"
}

function Fail([string]$Message) {
  throw "Phase 15 preflight failed: $Message"
}

function Test-UsableValue([object]$Value) {
  if ($null -eq $Value) { return $false }
  $text = [string]$Value
  if ([string]::IsNullOrWhiteSpace($text)) { return $false }
  if ($text -match "^(REPLACE_WITH|<[^>]+>)") { return $false }
  return $true
}

function Read-DotEnv([string]$Path) {
  $values = @{}
  foreach ($line in Get-Content -LiteralPath $Path) {
    if ($line -match '^\s*(?:export\s+)?(?<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?<value>.*)\s*$') {
      $key = $Matches.key
      $value = $Matches.value.Trim()
      if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
      }
      $values[$key] = $value
    }
  }
  return $values
}

function Assert-IgnoredFile([string]$Path) {
  Push-Location $repoRoot
  try {
    & git check-ignore --quiet -- $Path
    if ($LASTEXITCODE -ne 0) {
      Fail "source file is not ignored by Git; refusing to read it"
    }
  } finally {
    Pop-Location
  }
}

$sourcePath = (Resolve-Path -LiteralPath $SourceEnvFile -ErrorAction SilentlyContinue)
if ($null -eq $sourcePath) { Fail "source .env file was not found" }
$sourcePath = $sourcePath.Path
Assert-IgnoredFile $sourcePath
$envValues = Read-DotEnv $sourcePath

$requiredKeys = @(
  "POSTGRES_USER",
  "POSTGRES_PASSWORD",
  "REDIS_PASSWORD",
  "RATE_LIMIT_KEY_HMAC_SECRET",
  "AUTH_THROTTLE_HMAC_SECRET",
  "CAMPAIGN_CLIENT_SECRET",
  "FLASHSALE_CLIENT_SECRET"
)
if ($EnableStripe) {
  $requiredKeys += @("STRIPE_SECRET_KEY", "STRIPE_PUBLISHABLE_KEY", "STRIPE_WEBHOOK_SECRET")
}

$missingKeys = @($requiredKeys | Where-Object { -not $envValues.ContainsKey($_) -or -not (Test-UsableValue $envValues[$_]) })
if ($missingKeys.Count -gt 0) {
  Fail ("missing manual key(s): " + ($missingKeys -join ", "))
}

if ([string]::IsNullOrWhiteSpace($JwtKeyDirectory)) {
  if ($envValues.ContainsKey("AUTH_JWT_KEY_DIR")) { $JwtKeyDirectory = [string]$envValues["AUTH_JWT_KEY_DIR"] }
}
if ([string]::IsNullOrWhiteSpace($JwtKeyDirectory)) { Fail "AUTH_JWT_KEY_DIR is missing" }
$jwtDirectoryPath = (Resolve-Path -LiteralPath $JwtKeyDirectory -ErrorAction SilentlyContinue)
if ($null -eq $jwtDirectoryPath) { Fail "JWT key directory was not found" }
$jwtDirectoryPath = $jwtDirectoryPath.Path
$publicKeyPath = Join-Path $jwtDirectoryPath "jwt-public.pem"
$privateKeyPath = Join-Path $jwtDirectoryPath "jwt-private.pem"
foreach ($keyPath in @($publicKeyPath, $privateKeyPath)) {
  if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf)) { Fail "JWT key file was not found: $keyPath" }
  if ((Get-Item -LiteralPath $keyPath).Length -le 0) { Fail "JWT key file is empty: $keyPath" }
}

& kubectl get namespace $Namespace --ignore-not-found -o name *> $null
if ($LASTEXITCODE -ne 0) { Fail "kubectl could not query namespace $Namespace" }
$namespaceResource = (& kubectl get namespace $Namespace --ignore-not-found -o name 2>$null)
if ([string]::IsNullOrWhiteSpace(($namespaceResource -join ""))) { Fail "namespace $Namespace does not exist" }

$secretMappings = @(
  @{ Name = "platform-secrets"; Entries = @(@("POSTGRES_USER", "POSTGRES_USER"), @("POSTGRES_PASSWORD", "POSTGRES_PASSWORD"), @("REDIS_PASSWORD", "REDIS_PASSWORD")) },
  @{ Name = "gateway-secrets"; Entries = @(@("SPRING_DATA_REDIS_PASSWORD", "REDIS_PASSWORD"), @("RATE_LIMIT_KEY_HMAC_SECRET", "RATE_LIMIT_KEY_HMAC_SECRET")) },
  @{ Name = "authentication-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD"), @("SPRING_DATA_REDIS_PASSWORD", "REDIS_PASSWORD"), @("AUTH_THROTTLE_HMAC_SECRET", "AUTH_THROTTLE_HMAC_SECRET"), @("CAMPAIGN_CLIENT_SECRET", "CAMPAIGN_CLIENT_SECRET"), @("FLASHSALE_CLIENT_SECRET", "FLASHSALE_CLIENT_SECRET")) },
  @{ Name = "product-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD")) },
  @{ Name = "campaign-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD"), @("CAMPAIGN_CLIENT_SECRET", "CAMPAIGN_CLIENT_SECRET")) },
  @{ Name = "flashsale-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD"), @("SPRING_DATA_REDIS_PASSWORD", "REDIS_PASSWORD"), @("FLASHSALE_CLIENT_SECRET", "FLASHSALE_CLIENT_SECRET")) },
  @{ Name = "inventory-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD")) },
  @{ Name = "order-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD")) },
  @{ Name = "payment-secrets"; Entries = @(@("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER"), @("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD")) }
)
if ($EnableStripe) {
  $secretMappings[-1].Entries += @(@("STRIPE_SECRET_KEY", "STRIPE_SECRET_KEY"), @("STRIPE_PUBLISHABLE_KEY", "STRIPE_PUBLISHABLE_KEY"), @("STRIPE_WEBHOOK_SECRET", "STRIPE_WEBHOOK_SECRET"))
}

Write-Output "Phase 15 input validation passed."
Write-Output ("Required manual keys present: " + ($requiredKeys.Count))
Write-Output ("JWT files present: jwt-public.pem, jwt-private.pem")
Write-Output ("Stripe provisioning: " + ($(if ($EnableStripe) { "enabled by explicit opt-in" } else { "deferred (disabled)" })))
Write-Output ("Secret boundaries: " + (($secretMappings | ForEach-Object { $_.Name }) -join ", ") + ", auth-jwt")

if (-not $Apply) {
  Write-Output "Validation-only mode: no Kubernetes Secret was changed."
  exit 0
}

function Apply-EnvSecret([hashtable]$Mapping) {
  $tempPath = [IO.Path]::GetTempFileName()
  try {
    $lines = @()
    foreach ($entry in $Mapping.Entries) {
      $outputKey = [string]$entry[0]
      $sourceKey = [string]$entry[1]
      $lines += ($outputKey + "=" + [string]$envValues[$sourceKey])
    }
    [IO.File]::WriteAllLines($tempPath, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
    & kubectl -n $Namespace create secret generic $Mapping.Name --from-env-file=$tempPath --dry-run=client -o yaml | kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { Fail "kubectl could not apply $($Mapping.Name)" }
  } finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
  }
}

foreach ($mapping in $secretMappings) { Apply-EnvSecret $mapping }

$publicFileArgument = "--from-file=jwt-public.pem=$publicKeyPath"
$privateFileArgument = "--from-file=jwt-private.pem=$privateKeyPath"
& kubectl -n $Namespace create secret generic auth-jwt $publicFileArgument $privateFileArgument --dry-run=client -o yaml | kubectl apply -f -
if ($LASTEXITCODE -ne 0) { Fail "kubectl could not apply auth-jwt" }
Write-Output "Phase 15 Secret provisioning completed without displaying Secret values."
