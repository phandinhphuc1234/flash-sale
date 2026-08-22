<##
.SYNOPSIS
  Run a read-only Terraform safety and drift gate.

.DESCRIPTION
  Phase 23 validates the existing Terraform root module, AWS caller identity, and detailed plan.
  It never runs terraform apply, destroy, import, state mutation, or writes tfvars. The public EKS
  endpoint CIDR may be supplied through TF_VAR_cluster_endpoint_public_access_cidrs or detected
  explicitly with -AutoDetectPublicIp; it is never printed.
##>
[CmdletBinding()]
param(
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedAccountId = "090814040069",
  [switch]$AutoDetectPublicIp,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 900
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Phase 23 requires PowerShell 7 or newer. Run with: pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase23-terraform-gate.ps1"
}

$RunDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$TerraformRoot = Join-Path $RepoRoot "infra\terraform"
$CidrVariableName = "TF_VAR_cluster_endpoint_public_access_cidrs"
$script:TerraformPlanExitCode = $null

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($RunDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Phase 23 exceeded its $TimeoutSeconds-second execution budget." }
  return $remaining
}

function Get-BoundedDiagnostic {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 2000) { return $Text }
  return $Text.Substring(0, 2000) + "... [truncated]"
}

function Invoke-BoundedNativeProcess {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [string]$WorkingDirectory = ""
  )
  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $Command
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) { $startInfo.WorkingDirectory = $WorkingDirectory }
  foreach ($argument in $Arguments) { $null = $startInfo.ArgumentList.Add([string]$argument) }
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingTimeoutSeconds
    if (-not $process.Start()) { throw "Could not start native command '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit((Get-RemainingTimeoutSeconds) * 1000)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Phase 23 execution budget."
    }
    $process.WaitForExit()
    $stdoutRaw = $stdoutTask.GetAwaiter().GetResult()
    $stderrRaw = $stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = if ($null -eq $stdoutRaw) { "" } else { $stdoutRaw.Trim() }
      StandardError = if ($null -eq $stderrRaw) { "" } else { $stderrRaw.Trim() }
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    $process.Dispose()
  }
}

function Invoke-RequiredCommand {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description,
    [string]$WorkingDirectory = ""
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments -WorkingDirectory $WorkingDirectory
  if ($result.ExitCode -ne 0) {
    $diagnostic = if (-not [string]::IsNullOrWhiteSpace($result.StandardError)) { $result.StandardError } else { $result.StandardOutput }
    throw "$Description failed with exit code $($result.ExitCode): $(Get-BoundedDiagnostic $diagnostic)"
  }
  return $result
}

function Get-NativeJson {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description,
    [string]$WorkingDirectory = ""
  )
  $result = Invoke-RequiredCommand -Command $Command -Arguments $Arguments -Description $Description -WorkingDirectory $WorkingDirectory
  if ([string]::IsNullOrWhiteSpace($result.StandardOutput)) { throw "$Description returned empty JSON." }
  try { return $result.StandardOutput | ConvertFrom-Json -Depth 20 }
  catch { throw "$Description returned invalid JSON: $($_.Exception.Message)" }
}

function Get-DetectedPublicIpv4 {
  try {
    $response = Invoke-WebRequest -Uri "https://api.ipify.org" -UseBasicParsing -TimeoutSec 10
    $ip = [string]$response.Content
  } catch {
    throw "Could not auto-detect the public IPv4 address. Set $CidrVariableName manually and retry."
  }
  $ip = $ip.Trim()
  if ($ip -notmatch "^(?:\d{1,3}\.){3}\d{1,3}$") {
    throw "Public IP detection returned a non-IPv4 value. Set $CidrVariableName manually and retry."
  }
  $octets = @($ip -split "\.") | ForEach-Object { [int]$_ }
  if (@($octets | Where-Object { $_ -gt 255 }).Count -gt 0) {
    throw "Public IP detection returned an invalid IPv4 value. Set $CidrVariableName manually and retry."
  }
  return $ip
}

function Assert-CidrInput {
  $raw = [Environment]::GetEnvironmentVariable($CidrVariableName)
  $detected = $false
  if ([string]::IsNullOrWhiteSpace($raw) -and $AutoDetectPublicIp) {
    $detectedIp = Get-DetectedPublicIpv4
    $raw = ('["{0}/32"]' -f $detectedIp)
    $env:TF_VAR_cluster_endpoint_public_access_cidrs = $raw
    $detected = $true
  }
  if ([string]::IsNullOrWhiteSpace($raw)) {
    throw "Missing $CidrVariableName. Set it locally to a Terraform list containing your public IPv4 with /32, or rerun with -AutoDetectPublicIp; the value is never printed."
  }
  try { $parsed = $raw | ConvertFrom-Json -Depth 10 }
  catch { throw "$CidrVariableName must be a Terraform list such as ['IP/32']; the value was not displayed." }
  $cidrs = @($parsed)
  if ($cidrs.Count -lt 1) { throw "$CidrVariableName must contain at least one IPv4 CIDR." }
  foreach ($cidrObject in $cidrs) {
    $cidr = [string]$cidrObject
    if ($cidr -notmatch "^(?<ip>\d{1,3}(?:\.\d{1,3}){3})/(?<prefix>\d{1,2})$") {
      throw "$CidrVariableName contains an invalid IPv4 CIDR; use your public IP with /32. The value was not displayed."
    }
    $octets = @($Matches.ip -split "\.") | ForEach-Object { [int]$_ }
    if (@($octets | Where-Object { $_ -gt 255 }).Count -gt 0 -or [int]$Matches.prefix -gt 32) {
      throw "$CidrVariableName contains an invalid IPv4 range. The value was not displayed."
    }
    if ($cidr -eq "0.0.0.0/0") { throw "$CidrVariableName must not allow 0.0.0.0/0." }
  }
  $mode = if ($detected) { "auto-detected" } else { "provided" }
  Write-Output ("CIDR input: PASS ($($cidrs.Count) IPv4 range(s); mode=$mode; values withheld)")
}

function Assert-TerraformVersion {
  $version = Get-NativeJson -Command "terraform" -Arguments @("version", "-json") -Description "Terraform version"
  $versionText = [string]$version.terraform_version
  $parsedVersion = $null
  if (-not [version]::TryParse($versionText, [ref]$parsedVersion)) { throw "Could not parse Terraform version metadata." }
  if ($parsedVersion.Major -ne 1 -or $parsedVersion.Minor -lt 10) {
    throw "Terraform $versionText is unsupported; this root requires >=1.10 and <2.0."
  }
  Write-Output "Terraform version: PASS ($versionText)"
}

function Assert-AwsIdentity {
  $result = Invoke-RequiredCommand -Command "aws" -Arguments @(
    "sts", "get-caller-identity", "--profile", $AwsProfile, "--query", "Account", "--output", "text"
  ) -Description "AWS caller identity"
  $account = $result.StandardOutput.Trim()
  if ($account -ne $ExpectedAccountId) { throw "AWS profile '$AwsProfile' resolved to an unexpected account; credentials were not displayed." }
  Write-Output "AWS identity: PASS (account=$account region=$AwsRegion profile=$AwsProfile)"
}

function Assert-TerraformFormat {
  $result = Invoke-BoundedNativeProcess -Command "terraform" -Arguments @("fmt", "-check", "-diff", "-recursive") -WorkingDirectory $TerraformRoot
  if ($result.ExitCode -ne 0) {
    throw "terraform fmt -check failed; format the Terraform files before continuing."
  }
  Write-Output "Terraform format: PASS"
}

function Assert-TerraformValidate {
  $null = Invoke-RequiredCommand -Command "terraform" -Arguments @("validate", "-no-color") -Description "Terraform validate" -WorkingDirectory $TerraformRoot
  Write-Output "Terraform validate: PASS"
}

function Get-PlanSummary {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "summary unavailable" }
  $match = [regex]::Match($Text, "(?m)^Plan: (?<summary>[^\r\n]+)$")
  if ($match.Success) { return $match.Groups["summary"].Value.Trim() }
  return "summary unavailable"
}

function Invoke-TerraformPlan {
  $result = Invoke-BoundedNativeProcess -Command "terraform" -Arguments @(
    "plan", "-input=false", "-detailed-exitcode", "-no-color"
  ) -WorkingDirectory $TerraformRoot
  if ($result.ExitCode -eq 0) {
    $script:TerraformPlanExitCode = 0
    Write-Output "Plan: NO_CHANGES"
    return
  }
  if ($result.ExitCode -eq 2) {
    $script:TerraformPlanExitCode = 2
    Write-Output ("Plan: CHANGES_REVIEW_REQUIRED ($((Get-PlanSummary $result.StandardOutput)))")
    Write-Output "No apply was executed. Review the plan before any later infrastructure change."
    return
  }
  $diagnostic = if (-not [string]::IsNullOrWhiteSpace($result.StandardError)) { $result.StandardError } else { $result.StandardOutput }
  throw "terraform plan failed with exit code $($result.ExitCode): $(Get-BoundedDiagnostic $diagnostic)"
}

$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $AwsRegion

Write-Output "Phase 23 Terraform safety gate started (read-only)."
Assert-CidrInput
Assert-TerraformVersion
Assert-AwsIdentity
Assert-TerraformFormat
Assert-TerraformValidate
Invoke-TerraformPlan
if ($script:TerraformPlanExitCode -eq 2) { exit 2 }
Write-Output "Phase 23 Terraform safety gate: PASS"
Write-Output "No terraform apply/destroy/import/state mutation was executed. Credentials, state, .env, and CIDR values were not printed."
