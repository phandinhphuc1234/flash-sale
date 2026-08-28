<#
.SYNOPSIS
  Run or validate the canonical internal authenticated Phase 22 cloud smoke.

.DESCRIPTION
  Validation mode is non-mutating. Run mode uses a loopback API Gateway port-forward, an existing
  ROLE_ADMIN login, a generated shopper, Gateway-owned Product/Campaign HTTP contracts, and an
  Inventory-owned one-shot fixture Job. With -AllowPaymentEnabled it accepts the Phase 24 runtime
  flags; with -RunStripeCloudSmoke it continues through the hosted Stripe Checkout and webhook
  evidence path. It never executes SQL, reads another service database, or prints passwords, JWTs,
  cookies, Kubernetes Secret values, Checkout URLs, or full request bodies.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [string]$AdminLogin,
  [Security.SecureString]$AdminPassword,
  [ValidateRange(1024, 65535)]
  [int]$LocalPort = 28082,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600,
  [ValidateRange(1, 100)]
  [int]$InventoryQuantity = 1,
  [switch]$AllowPaymentEnabled,
  [switch]$RunStripeCloudSmoke,
  [string]$StripeGatewayBaseUri = "https://api.flashsale123.tech"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Phase 22 requires PowerShell 7 or newer."
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$OverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$Namespace = "flash-sale"
$ArgoNamespace = "argocd"
$ArgoApplication = "flash-sale-cloud"
$ExpectedClusterName = "flash-sale-dev"
$ExpectedRepositoryUrl = "https://github.com/phandinhphuc1234/flash-sale.git"
$StripeApiVersion = "2026-07-29.dahlia"
$StripeGatewayBaseUri = $StripeGatewayBaseUri.TrimEnd('/')
$ProductBasePrice = 199000
$CampaignPrice = 179000
if ($CampaignPrice -ge $ProductBasePrice) {
  throw "Phase 22 fixture campaign price must be lower than the Product base price."
}
$RunDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
$script:PortForward = $null
$script:PortForwardOut = $null
$script:PortForwardErr = $null
$script:FixtureJobName = $null
$script:FixtureManifest = $null

function Get-RemainingSeconds {
  $remaining = [int][Math]::Ceiling(($RunDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) {
    throw "Phase 22 exceeded its $TimeoutSeconds-second execution budget."
  }
  return $remaining
}

function Get-BoundedText {
  param(
    [AllowEmptyString()][string]$Text,
    [ValidateRange(200, 5000)][int]$MaxLength = 500
  )
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le $MaxLength) { return $Text }
  return $Text.Substring(0, $MaxLength) + "... [truncated]"
}

function Invoke-BoundedNativeProcess {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments
  )
  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $Command
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  foreach ($argument in $Arguments) { $null = $startInfo.ArgumentList.Add([string]$argument) }
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingSeconds
    if (-not $process.Start()) { throw "Could not start '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit((Get-RemainingSeconds) * 1000)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Phase 22 deadline."
    }
    $process.WaitForExit()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = ([string]$stdoutTask.GetAwaiter().GetResult()).Trim()
      StandardError = ([string]$stderrTask.GetAwaiter().GetResult()).Trim()
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    $process.Dispose()
  }
}

function Get-NativeText {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [string]$Description = $Command
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments
  if ($result.ExitCode -ne 0) {
    throw "$Description failed: $(Get-BoundedText $result.StandardError)"
  }
  return $result.StandardOutput
}

function Get-NativeJson {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description
  )
  $text = Get-NativeText -Command $Command -Arguments $Arguments -Description $Description
  if ([string]::IsNullOrWhiteSpace($text)) { throw "$Description returned empty JSON." }
  try { return $text | ConvertFrom-Json -Depth 100 }
  catch { throw "$Description returned invalid JSON." }
}

function Get-PropertyValue {
  param([AllowNull()][object]$Object, [Parameter(Mandatory)][string]$Name)
  if ($null -eq $Object) { return $null }
  $property = $Object.PSObject.Properties[$Name]
  if ($null -eq $property) { return $null }
  return $property.Value
}

function Get-ApiData {
  param([AllowNull()][object]$Body)
  $data = Get-PropertyValue $Body "data"
  if ($null -ne $data) { return $data }
  return $Body
}

function Read-IgnoredDotEnv {
  $path = Join-Path $RepoRoot "infra\docker\.env"
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Stripe cloud smoke requires infra/docker/.env; add local-only values and never commit it."
  }
  & git check-ignore --quiet -- $path
  if ($LASTEXITCODE -ne 0) {
    throw "Refusing to read Stripe values because infra/docker/.env is not ignored by Git."
  }
  $values = @{}
  foreach ($line in Get-Content -LiteralPath $path) {
    if ($line -match '^\s*(?:export\s+)?(?<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?<value>.*)\s*$') {
      $value = $Matches.value.Trim()
      if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
          ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
      }
      $values[$Matches.key] = $value
    }
  }
  return $values
}

function Get-StripeRuntimeSecrets {
  $values = Read-IgnoredDotEnv
  foreach ($name in @("STRIPE_SECRET_KEY", "STRIPE_WEBHOOK_SECRET")) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace([string]$values[$name])) {
      throw "Stripe cloud smoke requires $name in the ignored infra/docker/.env."
    }
  }
  if ($values.ContainsKey("STRIPE_API_VERSION") -and
      -not [string]::IsNullOrWhiteSpace([string]$values.STRIPE_API_VERSION) -and
      [string]$values.STRIPE_API_VERSION -ne $StripeApiVersion) {
    throw "Stripe API version mismatch; expected $StripeApiVersion."
  }
  return [pscustomobject]@{
    SecretKey = [string]$values.STRIPE_SECRET_KEY
    WebhookSecret = [string]$values.STRIPE_WEBHOOK_SECRET
  }
}

function Invoke-StripeApiGet {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$SecretKey)
  try {
    $response = Invoke-WebRequest -Uri ("https://api.stripe.com" + $Path) -Method GET `
      -Headers @{ Authorization = "Bearer $SecretKey" } -UseBasicParsing `
      -TimeoutSec ([Math]::Min(20, (Get-RemainingSeconds))) -SkipHttpErrorCheck
  } catch {
    throw "Stripe API request failed before a response was received."
  }
  if ([int]$response.StatusCode -notin @(200)) {
    throw "Stripe API request failed with HTTP $($response.StatusCode)."
  }
  try { return $response.Content | ConvertFrom-Json -Depth 100 }
  catch { throw "Stripe API returned invalid JSON." }
}

function Get-StripeSessionIdFromUrl {
  param([Parameter(Mandatory)][string]$CheckoutUrl)
  $match = [regex]::Match($CheckoutUrl, '/c/pay/(?<id>cs_[^#?]+)')
  if (-not $match.Success) { throw "Checkout response did not contain a Stripe session identity." }
  return $match.Groups['id'].Value
}

function Wait-Condition {
  param(
    [Parameter(Mandatory)][scriptblock]$Condition,
    [Parameter(Mandatory)][string]$Description,
    [int]$DelaySeconds = 3
  )
  do {
    if (& $Condition) { return }
    Start-Sleep -Seconds ([Math]::Min($DelaySeconds, (Get-RemainingSeconds)))
  } while ($true)
}

function Get-HmacSha256Hex {
  param([Parameter(Mandatory)][string]$Secret, [Parameter(Mandatory)][string]$Value)
  $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
  try { return ([BitConverter]::ToString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-', '').ToLowerInvariant() }
  finally { $hmac.Dispose() }
}

function Post-SignedStripeWebhook {
  param(
    [Parameter(Mandatory)][string]$Payload,
    [Parameter(Mandatory)][string]$WebhookSecret,
    [Parameter(Mandatory)][string]$EventId
  )
  $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $digest = Get-HmacSha256Hex -Secret $WebhookSecret -Value ("$timestamp.$Payload")
  try {
    $response = Invoke-WebRequest -Uri "$StripeGatewayBaseUri/webhooks/v1/payments/stripe" -Method POST `
      -Headers @{ 'Stripe-Signature' = "t=$timestamp,v1=$digest"; 'Content-Type' = 'application/json' } `
      -Body ([Text.Encoding]::UTF8.GetBytes($Payload)) -UseBasicParsing `
      -TimeoutSec ([Math]::Min(20, (Get-RemainingSeconds))) -SkipHttpErrorCheck
  } catch {
    throw "Stripe webhook $EventId failed before a response was received."
  }
  if ([int]$response.StatusCode -ne 204) {
    throw "Stripe webhook $EventId returned HTTP $($response.StatusCode)."
  }
}

function Get-KafkaTopicTotalEndOffset {
  $result = Invoke-BoundedNativeProcess -Command "kubectl" -Arguments @(
    "-n", $Namespace, "exec", "kafka-0", "--", "/opt/kafka/bin/kafka-get-offsets.sh",
    "--bootstrap-server", "localhost:9092", "--topic", "flashsale.payment.events.v1"
  )
  if ($result.ExitCode -ne 0) { throw "Kafka offset query failed." }
  $offsets = @($result.StandardOutput -split "`r?`n" |
    ForEach-Object { if ($_ -match ':([0-9]+)$') { [long]$Matches[1] } })
  if ($offsets.Count -eq 0) { throw "Kafka offset query returned no partitions." }
  # A record can land on any partition. Comparing only the maximum misses progress when another
  # partition already has the same or a greater end offset.
  return [long](($offsets | Measure-Object -Sum).Sum)
}

function Get-ApiErrorCode {
  param([AllowNull()][object]$Body)
  $value = Get-PropertyValue $Body "errorCode"
  if ([string]::IsNullOrWhiteSpace([string]$value)) {
    $value = Get-PropertyValue $Body "code"
  }
  return [string]$value
}

function Invoke-Api {
  param(
    [Parameter(Mandatory)][ValidateSet("GET", "POST", "PUT", "PATCH")][string]$Method,
    [Parameter(Mandatory)][string]$Uri,
    [hashtable]$Headers = @{},
    [AllowNull()][object]$Body
  )
  $requestHeaders = @{}
  foreach ($key in $Headers.Keys) { $requestHeaders[$key] = [string]$Headers[$key] }
  $parameters = @{
    Uri = $Uri
    Method = $Method
    Headers = $requestHeaders
    TimeoutSec = [Math]::Min(20, (Get-RemainingSeconds))
    SkipHttpErrorCheck = $true
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json"
    $parameters.Body = ($Body | ConvertTo-Json -Depth 30 -Compress)
  }
  if ($requestHeaders.ContainsKey("If-Match")) {
    # Product's approved admin contract carries the expected version as a bare number
    # (for example, If-Match: 1). PowerShell's HTTP client enforces RFC-style quoted
    # entity tags before sending the request, so bypass only this local header check.
    $parameters.SkipHeaderValidation = $true
  }
  try {
    $response = Invoke-WebRequest @parameters
  } catch {
    throw "HTTP $Method $Uri failed before a response was received."
  }
  $parsed = $null
  if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
    try { $parsed = $response.Content | ConvertFrom-Json -Depth 100 } catch { $parsed = $null }
  }
  return [pscustomobject]@{
    StatusCode = [int]$response.StatusCode
    Headers = $response.Headers
    Body = $parsed
  }
}

function Assert-ApiStatus {
  param(
    [Parameter(Mandatory)][object]$Response,
    [Parameter(Mandatory)][int[]]$Expected,
    [Parameter(Mandatory)][string]$Stage
  )
  if ($Response.StatusCode -notin $Expected) {
    $code = Get-ApiErrorCode $Response.Body
    if ([string]::IsNullOrWhiteSpace($code)) { $code = "UNSPECIFIED" }
    throw "$Stage failed: HTTP $($Response.StatusCode), code=$code."
  }
}

function Get-HeaderValue {
  param([Parameter(Mandatory)][object]$Headers, [Parameter(Mandatory)][string]$Name)
  $value = $Headers[$Name]
  if ($value -is [array]) { return [string]$value[0] }
  return [string]$value
}

function Get-Version {
  param([Parameter(Mandatory)][object]$Response, [AllowNull()][object]$Body)
  $etag = Get-HeaderValue -Headers $Response.Headers -Name "ETag"
  if (-not [string]::IsNullOrWhiteSpace($etag)) {
    $trimmed = $etag.Trim().Trim('"')
    $parsed = 0L
    if ([long]::TryParse($trimmed, [ref]$parsed)) { return $parsed }
  }
  $data = Get-ApiData $Body
  $version = 0L
  if ([long]::TryParse([string](Get-PropertyValue $data "version"), [ref]$version)) { return $version }
  throw "The response did not contain a numeric version/ETag."
}

function New-TraceId {
  return ("phase22-{0}" -f ([guid]::NewGuid().ToString("N")))
}

function New-OperationHeaders {
  param([Parameter(Mandatory)][string]$Token, [Parameter(Mandatory)][string]$TraceId)
  return @{ Authorization = "Bearer $Token"; "X-Trace-Id" = $TraceId }
}

function ConvertFrom-Base64Url {
  param([Parameter(Mandatory)][string]$Value)
  $normalized = $Value.Replace('-', '+').Replace('_', '/')
  switch ($normalized.Length % 4) { 2 { $normalized += "==" } 3 { $normalized += "=" } }
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($normalized))
}

function Get-JwtAuthorities {
  param([Parameter(Mandatory)][string]$Token)
  $parts = $Token.Split('.')
  if ($parts.Count -ne 3) { throw "Authentication returned an invalid access token shape." }
  try { $claims = ConvertFrom-Base64Url $parts[1] | ConvertFrom-Json -Depth 20 }
  catch { throw "Authentication returned an unreadable access token." }
  $values = @()
  foreach ($claimName in @("authorities", "roles")) {
    $claim = Get-PropertyValue $claims $claimName
    if ($claim -is [System.Collections.IEnumerable] -and $claim -isnot [string]) {
      $values += @($claim | ForEach-Object { [string]$_ })
    } elseif ($null -ne $claim) {
      $values += [string]$claim
    }
  }
  return @($values | Sort-Object -Unique)
}

function Convert-SecurePassword {
  param([Parameter(Mandatory)][Security.SecureString]$SecurePassword)
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
  try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Assert-LocalPortAvailable {
  param([Parameter(Mandatory)][int]$Port)
  $existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -ne $existing) { throw "Local port $Port is already in use." }
  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
  try { $listener.Start() } catch { throw "Local port $Port is already in use." } finally { $listener.Stop() }
}

function Start-GatewayPortForward {
  Assert-LocalPortAvailable $LocalPort
  $base = Join-Path ([IO.Path]::GetTempPath()) ("phase22-gateway-{0}" -f ([guid]::NewGuid().ToString("N")))
  $script:PortForwardOut = "$base.out.log"
  $script:PortForwardErr = "$base.err.log"
  # Phase 24 intentionally exposes only Service port 443. The Service still maps that
  # port to the Gateway's plain HTTP container port, so the loopback probe remains HTTP.
  $script:PortForward = Start-Process -FilePath "kubectl" -ArgumentList @(
    "-n", $Namespace, "port-forward", "--address", "127.0.0.1", "service/api-gateway", ("{0}:443" -f $LocalPort)
  ) -RedirectStandardOutput $script:PortForwardOut -RedirectStandardError $script:PortForwardErr -WindowStyle Hidden -PassThru
  $baseUri = "http://127.0.0.1:$LocalPort"
  do {
    Start-Sleep -Milliseconds 500
    if ($script:PortForward.HasExited) {
      $details = if (Test-Path -LiteralPath $script:PortForwardErr) {
        (Get-Content -LiteralPath $script:PortForwardErr -Raw).Trim()
      } else { "no kubectl stderr captured" }
      if ([string]::IsNullOrWhiteSpace($details)) { $details = "no kubectl stderr captured" }
      throw "Gateway port-forward exited before readiness: $details"
    }
    try {
      $probe = Invoke-WebRequest -Uri "$baseUri/actuator/health/readiness" -TimeoutSec 5 -SkipHttpErrorCheck
      if ($probe.StatusCode -eq 200) { return $baseUri }
    } catch { }
  } while ((Get-Date) -lt $RunDeadline)
  throw "Gateway readiness did not become HTTP 200 before timeout."
}

function Assert-AnonymousAdminRejected {
  param([Parameter(Mandatory)][string]$BaseUri)
  $response = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/admin/catalog/products?page=0&size=1"
  Assert-ApiStatus $response @(401, 403) "anonymous admin boundary"
  Write-Output "Anonymous admin boundary: PASS (HTTP $($response.StatusCode))"
}

function Stop-GatewayPortForward {
  if ($null -ne $script:PortForward -and -not $script:PortForward.HasExited) {
    Stop-Process -Id $script:PortForward.Id -Force -ErrorAction SilentlyContinue
  }
  if ($null -ne $script:PortForwardOut) { Remove-Item -LiteralPath $script:PortForwardOut -Force -ErrorAction SilentlyContinue }
  if ($null -ne $script:PortForwardErr) { Remove-Item -LiteralPath $script:PortForwardErr -Force -ErrorAction SilentlyContinue }
}

function Assert-Preflight {
  $context = Get-NativeText -Command "kubectl" -Arguments @("config", "current-context") -Description "kubectl context"
  if ($context -notmatch "(^|[:/])cluster/$([regex]::Escape($ExpectedClusterName))$") {
    throw "kubectl context does not identify '$ExpectedClusterName'."
  }
  Get-NativeText -Command "kubectl" -Arguments @("kustomize", $OverlayPath) -Description "cloud Kustomize render" | Out-Null
  Get-NativeText -Command "kubectl" -Arguments @("apply", "--dry-run=client", "-k", $OverlayPath) -Description "cloud Kustomize dry-run" | Out-Null
  $app = Get-NativeJson -Command "kubectl" -Arguments @("-n", $ArgoNamespace, "get", "application", $ArgoApplication, "-o", "json") -Description "Argo Application"
  $sync = [string](Get-PropertyValue (Get-PropertyValue $app "status") "sync" | ForEach-Object { Get-PropertyValue $_ "status" })
  $health = [string](Get-PropertyValue (Get-PropertyValue $app "status") "health" | ForEach-Object { Get-PropertyValue $_ "status" })
  $source = Get-PropertyValue $app "spec" | ForEach-Object { Get-PropertyValue $_ "source" }
  if ($sync -ne "Synced" -or $health -ne "Healthy") { throw "Argo Application is not Synced/Healthy." }
  if ([string](Get-PropertyValue $source "repoURL") -ne $ExpectedRepositoryUrl -or
      [string](Get-PropertyValue $source "path") -ne "infra/k8s/overlays/cloud" -or
      [string](Get-PropertyValue $source "targetRevision") -ne "develop") {
    throw "Argo source drift detected."
  }
  $expectedPaymentFlag = if ($AllowPaymentEnabled -or $RunStripeCloudSmoke) { "true" } else { "false" }
  $requiredDeployments = @("api-gateway", "authentication-service", "product-service", "campaign-service", "flash-sale-service", "inventory-service", "order-service")
  if ($expectedPaymentFlag -eq "true") { $requiredDeployments += "payment-service" }
  foreach ($name in $requiredDeployments) {
    Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "rollout", "status", "deployment/$name", "--timeout=60s") -Description "Deployment/$name readiness" | Out-Null
  }
  if ($expectedPaymentFlag -eq "true") {
    Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "get", "secret", "payment-secrets", "-o", "name") -Description "Payment Secret boundary" | Out-Null
  }
  $paymentConfig = Get-NativeJson -Command "kubectl" -Arguments @("-n", $Namespace, "get", "configmap", "payment-service-runtime-config", "-o", "json") -Description "Payment runtime flags"
  $data = Get-PropertyValue $paymentConfig "data"
  foreach ($flag in @("PAYMENT_ACCEPTANCE_ENABLED", "PAYMENT_CHECKOUT_ENABLED", "STRIPE_ENABLED", "PAYMENT_CONSUMER_ENABLED", "PAYMENT_OUTBOX_PUBLISHER_ENABLED", "PAYMENT_RECOVERY_ENABLED", "PAYMENT_WEBHOOK_PROCESSING_ENABLED")) {
    if ([string](Get-PropertyValue $data $flag).ToLowerInvariant() -ne $expectedPaymentFlag) {
      throw "Payment flag '$flag' is not '$expectedPaymentFlag'."
    }
  }
  $paymentState = if ($expectedPaymentFlag -eq "true") { "7/7-enabled" } else { "7/7-disabled" }
  Write-Output "Phase 22 preflight: PASS (context=$context sync=$sync health=$health payment=$paymentState)"
}

function Invoke-AdminLogin {
  param([Parameter(Mandatory)][string]$BaseUri)
  $ownsSecurePassword = $null -eq $AdminPassword
  $secure = if ($ownsSecurePassword) {
    Read-Host "Existing ROLE_ADMIN password" -AsSecureString
  } else {
    $AdminPassword
  }
  $plain = Convert-SecurePassword $secure
  try {
    $response = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/auth/login" -Body @{
      login = $AdminLogin; password = $plain; deviceName = "phase22-internal-e2e"
    }
  } finally {
    $plain = $null
    if ($ownsSecurePassword) { $secure.Dispose() }
    $secure = $null
  }
  Assert-ApiStatus $response @(200) "admin login"
  $data = Get-ApiData $response.Body
  $token = [string](Get-PropertyValue $data "accessToken")
  if ([string]::IsNullOrWhiteSpace($token)) { throw "Admin login response did not contain an access token." }
  $authorities = @(Get-JwtAuthorities $token)
  foreach ($required in @("CATALOG_ADMIN", "INVENTORY_ADMIN", "CAMPAIGN_ADMIN")) {
    if ($required -notin $authorities) { throw "Admin token lacks required authority '$required'." }
  }
  return $token
}

function Invoke-ShopperRegistration {
  param([Parameter(Mandatory)][string]$BaseUri)
  $suffix = [guid]::NewGuid().ToString("N").Substring(0, 12)
  $login = "phase22-$suffix"
  $email = "$login@example.test"
  $password = "Phase22-$([guid]::NewGuid().ToString('N'))-Aa1!"
  $register = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/auth/register" -Body @{
    email = $email; username = $login; password = $password
  }
  Assert-ApiStatus $register @(201) "shopper registration"
  $loginResponse = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/auth/login" -Body @{
    login = $login; password = $password; deviceName = "phase22-internal-e2e"
  }
  Assert-ApiStatus $loginResponse @(200) "shopper login"
  $token = [string](Get-PropertyValue (Get-ApiData $loginResponse.Body) "accessToken")
  if ([string]::IsNullOrWhiteSpace($token)) { throw "Shopper login response did not contain an access token." }
  $claims = $token.Split('.')[1] | ForEach-Object { ConvertFrom-Base64Url $_ | ConvertFrom-Json -Depth 20 }
  $userId = [guid](Get-PropertyValue $claims "sub")
  return [pscustomobject]@{ Token = $token; UserId = $userId; Label = $login }
}

function Invoke-ProductFixture {
  param([Parameter(Mandatory)][string]$BaseUri, [Parameter(Mandatory)][string]$AdminToken)
  $suffix = [guid]::NewGuid().ToString("N").Substring(0, 12)
  $trace = New-TraceId
  $sku = "PHASE22-SKU-$suffix"
  $headers = New-OperationHeaders $AdminToken $trace
  $headers["Idempotency-Key"] = "phase22-product-create-$suffix"
  $create = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/admin/catalog/products" -Headers $headers -Body @{
    code = "PHASE22-$suffix"; slug = "phase22-$suffix"; name = "Phase 22 Smoke $suffix"
    shortDescription = "Disposable internal smoke fixture"; description = "Phase 22 internal E2E fixture"
  }
  Assert-ApiStatus $create @(201) "Product draft creation"
  $created = Get-ApiData $create.Body
  $productId = [guid](Get-PropertyValue $created "id")
  $version = [long](Get-PropertyValue $created "version")
  $compositionHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $compositionHeaders["If-Match"] = [string]$version
  $composition = Invoke-Api -Method PUT -Uri "$BaseUri/api/v1/admin/catalog/products/$productId/composition" -Headers $compositionHeaders -Body @{
    name = "Phase 22 Smoke $suffix"; shortDescription = "Disposable internal smoke fixture"; description = "Phase 22 internal E2E fixture"
    # Product owns Variant identity. A new composition must omit id; supplying a
    # runner-generated id is treated as an update to a non-owned Variant (409).
    variants = @(@{ id = $null; sku = $sku; barcode = $null; name = "Smoke Variant"; basePrice = $ProductBasePrice; currency = "VND"; status = "ACTIVE"; sortOrder = 0 })
    categories = @(); media = @()
  }
  Assert-ApiStatus $composition @(200) "Product composition"
  $version = [long](Get-PropertyValue (Get-ApiData $composition.Body) "version")
  $detailHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $detail = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/admin/catalog/products/$productId" -Headers $detailHeaders
  Assert-ApiStatus $detail @(200) "Product composition detail"
  $variant = @(Get-PropertyValue (Get-ApiData $detail.Body) "variants") |
    Where-Object { [string](Get-PropertyValue $_ "sku") -eq $sku } |
    Select-Object -First 1
  if ($null -eq $variant) { throw "Product composition detail did not contain the created SKU." }
  $variantId = [guid](Get-PropertyValue $variant "id")
  if ($variantId -eq [guid]::Empty) { throw "Product composition detail returned an empty Variant id." }
  $publishHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $publishHeaders["If-Match"] = [string]$version
  $publishHeaders["Idempotency-Key"] = "phase22-product-publish-$suffix"
  $publish = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/admin/catalog/products/$productId/publish" -Headers $publishHeaders
  Assert-ApiStatus $publish @(200) "Product publication"
  return [pscustomobject]@{ ProductId = $productId; VariantId = $variantId; Sku = $sku; Label = $suffix }
}

function Invoke-InventoryFixture {
  param([Parameter(Mandatory)][object]$Product)
  $image = Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "get", "deployment/inventory-service", "-o", "jsonpath={.spec.template.spec.containers[0].image}") -Description "Inventory image"
  $script:FixtureJobName = "phase22-inventory-$([guid]::NewGuid().ToString('N').Substring(0, 12))"
  $script:FixtureManifest = Join-Path ([IO.Path]::GetTempPath()) "$($script:FixtureJobName).yaml"
  $manifest = @"
apiVersion: batch/v1
kind: Job
metadata:
  name: $($script:FixtureJobName)
  namespace: $Namespace
  labels:
    app.kubernetes.io/part-of: flash-sale
    app.kubernetes.io/component: phase22-fixture
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 300
  template:
    metadata:
      labels:
        app.kubernetes.io/part-of: flash-sale
        app.kubernetes.io/component: phase22-fixture
    spec:
      restartPolicy: Never
      containers:
      - name: inventory-fixture
        image: $image
        imagePullPolicy: IfNotPresent
        envFrom:
        - configMapRef:
            name: flash-sale-runtime-config
        - configMapRef:
            name: inventory-service-runtime-config
        - secretRef:
            name: inventory-secrets
        env:
        - name: SPRING_MAIN_WEB_APPLICATION_TYPE
          value: "none"
        - name: SPRING_TASK_SCHEDULING_ENABLED
          value: "false"
        - name: INVENTORY_FIXTURE_ENABLED
          value: "true"
        - name: INVENTORY_FIXTURE_VARIANT_ID
          value: "$($Product.VariantId)"
        - name: INVENTORY_FIXTURE_SKU_SNAPSHOT
          value: "$($Product.Sku)"
        - name: INVENTORY_FIXTURE_QUANTITY
          value: "$InventoryQuantity"
        - name: INVENTORY_FIXTURE_REASON
          value: "PHASE22_INTERNAL_E2E"
"@
  $manifest | Set-Content -LiteralPath $script:FixtureManifest -Encoding utf8NoBOM
  try {
    Get-NativeText -Command "kubectl" -Arguments @("apply", "--dry-run=client", "-f", $script:FixtureManifest) -Description "Inventory fixture dry-run" | Out-Null
    Get-NativeText -Command "kubectl" -Arguments @("apply", "-f", $script:FixtureManifest) -Description "Inventory fixture Job" | Out-Null
    $waitDeadline = (Get-Date).AddSeconds([Math]::Min(180, (Get-RemainingSeconds)))
    $completed = $false
    do {
      $job = Get-NativeJson -Command "kubectl" -Arguments @(
        "-n", $Namespace, "get", "job/$($script:FixtureJobName)", "-o", "json") -Description "Inventory fixture status"
      $succeeded = [int](Get-PropertyValue (Get-PropertyValue $job "status") "succeeded")
      $failed = [int](Get-PropertyValue (Get-PropertyValue $job "status") "failed")
      if ($succeeded -ge 1) {
        $completed = $true
        break
      }
      if ($failed -ge 1) {
        $logs = (& kubectl -n $Namespace logs "job/$($script:FixtureJobName)" --all-containers=true --tail=100 2>&1 | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($logs)) { $logs = "no logs available" }
        throw "Inventory fixture Job failed (failedAttempts=$failed): $(Get-BoundedText $logs 5000)"
      }
      Start-Sleep -Seconds ([Math]::Min(3, (Get-RemainingSeconds)))
    } while ((Get-Date) -lt $waitDeadline)
    if (-not $completed) {
      $logs = (& kubectl -n $Namespace logs "job/$($script:FixtureJobName)" --all-containers=true --tail=100 2>&1 | Out-String).Trim()
      if ([string]::IsNullOrWhiteSpace($logs)) { $logs = "no logs available" }
      throw "Inventory fixture completion timed out: $(Get-BoundedText $logs 5000)"
    }
    Write-Output "Inventory fixture Job: PASS (variantId=$($Product.VariantId) quantity=$InventoryQuantity)"
  } finally {
    if ($null -ne $script:FixtureJobName) {
      & kubectl -n $Namespace delete job $script:FixtureJobName --ignore-not-found *> $null
    }
    if ($null -ne $script:FixtureManifest) { Remove-Item -LiteralPath $script:FixtureManifest -Force -ErrorAction SilentlyContinue }
  }
}

function Invoke-CampaignFixture {
  param([Parameter(Mandatory)][string]$BaseUri, [Parameter(Mandatory)][string]$AdminToken, [Parameter(Mandatory)][object]$Product)
  $suffix = $Product.Label
  $startAt = [DateTimeOffset]::UtcNow.AddSeconds(25)
  $endAt = $startAt.AddMinutes(5)
  $format = "yyyy-MM-dd'T'HH:mm:ss.fff'Z'"
  $common = New-OperationHeaders $AdminToken (New-TraceId)
  $create = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/admin/campaigns" -Headers $common -Body @{
    code = "PHASE22-$suffix"; name = "Phase 22 Campaign $suffix"; startAt = $startAt.ToString($format); endAt = $endAt.ToString($format)
  }
  Assert-ApiStatus $create @(201) "Campaign creation"
  $campaign = Get-ApiData $create.Body
  $campaignId = [guid](Get-PropertyValue $campaign "id")
  $version = Get-Version $create $campaign
  $itemHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $itemHeaders["If-Match"] = ('"{0}"' -f $version)
  $item = Invoke-Api -Method PUT -Uri "$BaseUri/api/v1/admin/campaigns/$campaignId/item" -Headers $itemHeaders -Body @{
    variantId = $Product.VariantId; campaignPrice = $CampaignPrice; requestedQuantity = $InventoryQuantity; purchaseLimitPerUser = 1
  }
  Assert-ApiStatus $item @(200) "Campaign item"
  $version = Get-Version $item (Get-ApiData $item.Body)
  $scheduleHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $scheduleHeaders["If-Match"] = ('"{0}"' -f $version)
  $scheduleHeaders["Idempotency-Key"] = "phase22-campaign-schedule-$suffix"
  $schedule = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/admin/campaigns/$campaignId/schedule" -Headers $scheduleHeaders -Body @{}
  Assert-ApiStatus $schedule @(200) "Campaign schedule"
  while ([DateTimeOffset]::UtcNow -lt $startAt) { Start-Sleep -Seconds ([Math]::Min(2, (Get-RemainingSeconds))) }
  # The lifecycle scheduler may activate the due Campaign between the scheduled write and this
  # operator check. Refresh the aggregate/version before attempting the explicit activation so a
  # scheduler race is observed as a valid ACTIVE result rather than a stale If-Match failure.
  $detailHeaders = New-OperationHeaders $AdminToken (New-TraceId)
  $detailResponse = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/admin/campaigns/$campaignId" -Headers $detailHeaders
  Assert-ApiStatus $detailResponse @(200) "Campaign activation preflight"
  $active = Get-ApiData $detailResponse.Body
  if ([string](Get-PropertyValue $active "status") -ne "ACTIVE") {
    $version = Get-Version $detailResponse (Get-ApiData $detailResponse.Body)
    $activateHeaders = New-OperationHeaders $AdminToken (New-TraceId)
    $activateHeaders["If-Match"] = ('"{0}"' -f $version)
    $activate = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/admin/campaigns/$campaignId/activate" -Headers $activateHeaders -Body @{}
    if ($activate.StatusCode -eq 409) {
      $refresh = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/admin/campaigns/$campaignId" -Headers (New-OperationHeaders $AdminToken (New-TraceId))
      Assert-ApiStatus $refresh @(200) "Campaign activation race refresh"
      $active = Get-ApiData $refresh.Body
    } else {
      Assert-ApiStatus $activate @(200) "Campaign activation"
      $active = Get-ApiData $activate.Body
    }
  }
  if ([string](Get-PropertyValue $active "status") -ne "ACTIVE") { throw "Campaign activation did not return ACTIVE." }
  # Keep operator progress out of the pipeline so callers receive only the fixture object.
  Write-Host "Campaign fixture: PASS (campaignId=$campaignId status=ACTIVE)"
  return [pscustomobject]@{ CampaignId = $campaignId; StartAt = $startAt; EndAt = $endAt }
}

function Invoke-ReservationWithRetry {
  param([Parameter(Mandatory)][string]$BaseUri, [Parameter(Mandatory)][string]$ShopperToken, [Parameter(Mandatory)][object]$Campaign, [Parameter(Mandatory)][object]$Product)
  $key = "phase22-reservation-$([guid]::NewGuid().ToString('N'))"
  $trace = New-TraceId
  $headers = New-OperationHeaders $ShopperToken $trace
  $headers["Idempotency-Key"] = $key
  $body = @{ variantId = $Product.VariantId; quantity = $InventoryQuantity }
  do {
    $response = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/flash-sales/$($Campaign.CampaignId)/reservations" -Headers $headers -Body $body
    if ($response.StatusCode -eq 202) { break }
    $code = Get-ApiErrorCode $response.Body
    if ($code -notin @("FLASH_SALE_PROJECTION_UNAVAILABLE", "FLASH_SALE_CAMPAIGN_NOT_ACTIVE", "FLASH_SALE_ACCEPTANCE_PENDING", "FLASH_SALE_REDIS_UNAVAILABLE")) {
      throw "Reservation submission failed: HTTP $($response.StatusCode), code=$code."
    }
    Start-Sleep -Seconds ([Math]::Min(2, (Get-RemainingSeconds)))
  } while ((Get-Date) -lt $RunDeadline)
  Assert-ApiStatus $response @(202) "Reservation submission"
  $accepted = Get-ApiData $response.Body
  foreach ($name in @("purchaseRequestId", "reservationId", "campaignId", "variantId")) {
    if ($null -eq (Get-PropertyValue $accepted $name)) { throw "Reservation response omitted $name." }
  }
  return [pscustomobject]@{ Key = $key; TraceId = $trace; Headers = $headers; Body = $body; Accepted = $accepted }
}

function Assert-ReservationReplayAndOwner {
  param([Parameter(Mandatory)][string]$BaseUri, [Parameter(Mandatory)][string]$ShopperToken, [Parameter(Mandatory)][object]$Reservation)
  $replay = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/flash-sales/$($Reservation.Accepted.campaignId)/reservations" -Headers $Reservation.Headers -Body $Reservation.Body
  Assert-ApiStatus $replay @(202) "Reservation replay"
  $replayed = Get-ApiData $replay.Body
  foreach ($name in @("purchaseRequestId", "reservationId", "campaignId", "variantId")) {
    if ([string](Get-PropertyValue $replayed $name) -ne [string](Get-PropertyValue $Reservation.Accepted $name)) { throw "Reservation replay changed $name." }
  }
  $ownerHeaders = New-OperationHeaders $ShopperToken (New-TraceId)
  do {
    $owner = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/flash-sales/reservations/$($Reservation.Accepted.reservationId)" -Headers $ownerHeaders
    if ($owner.StatusCode -eq 200) {
      $detail = Get-ApiData $owner.Body
      if ([string](Get-PropertyValue $detail "purchaseRequestId") -eq [string]$Reservation.Accepted.purchaseRequestId) {
        return $detail
      }
    } elseif ($owner.StatusCode -ne 404) {
      Assert-ApiStatus $owner @(200) "Reservation owner query"
    }
    Start-Sleep -Seconds ([Math]::Min(2, (Get-RemainingSeconds)))
  } while ((Get-Date) -lt $RunDeadline)
  throw "Reservation owner query did not converge before timeout."
}

function Assert-OrderConvergence {
  param([Parameter(Mandatory)][string]$BaseUri, [Parameter(Mandatory)][string]$ShopperToken, [Parameter(Mandatory)][object]$Reservation, [Parameter(Mandatory)][object]$Product, [Parameter(Mandatory)][object]$Campaign)
  $headers = New-OperationHeaders $ShopperToken (New-TraceId)
  do {
    $list = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/orders?page=0&size=100" -Headers $headers
    Assert-ApiStatus $list @(200) "Order owner list"
    $page = Get-ApiData $list.Body
    $items = @(Get-PropertyValue $page "data")
    foreach ($item in $items) {
      $orderId = Get-PropertyValue $item "id"
      if ($null -eq $orderId) { continue }
      $detailResponse = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/orders/$orderId" -Headers $headers
      if ($detailResponse.StatusCode -ne 200) { continue }
      $detail = Get-ApiData $detailResponse.Body
      $line = @($detail.items) | Select-Object -First 1
      if ([string](Get-PropertyValue $detail "purchaseRequestId") -eq [string]$Reservation.Accepted.purchaseRequestId -and
          [string](Get-PropertyValue $detail "reservationId") -eq [string]$Reservation.Accepted.reservationId -and
          [string](Get-PropertyValue $detail "campaignId") -eq [string]$Campaign.CampaignId -and
          [string](Get-PropertyValue $line "variantId") -eq [string]$Product.VariantId -and
          [string](Get-PropertyValue $detail "status") -eq "PENDING_PAYMENT") {
        return $detail
      }
    }
    Start-Sleep -Seconds ([Math]::Min(2, (Get-RemainingSeconds)))
  } while ((Get-Date) -lt $RunDeadline)
  throw "Order consumer did not produce a matching owned Order before timeout."
}

function Invoke-StripeCloudSmoke {
  param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$ShopperToken,
    [Parameter(Mandatory)][object]$Order,
    [Parameter(Mandatory)][object]$Reservation
  )
  $secrets = Get-StripeRuntimeSecrets
  $script:StripePaymentResponse = $null
  Wait-Condition -Description "Payment aggregate for Order" -Condition {
    $script:StripePaymentResponse = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/payments/by-order/$($Order.id)" `
      -Headers (New-OperationHeaders $ShopperToken (New-TraceId))
    return $script:StripePaymentResponse.StatusCode -eq 200
  }
  $payment = Get-ApiData $script:StripePaymentResponse.Body
  $paymentId = [guid](Get-PropertyValue $payment "id")
  if ($paymentId -eq [guid]::Empty) { throw "Payment query returned an empty Payment id." }
  Write-Output "Payment aggregate: PASS (paymentId=$paymentId status=$([string](Get-PropertyValue $payment 'status')))"

  $offsetBefore = Get-KafkaTopicTotalEndOffset
  $checkoutKey = "phase24-stripe-$([guid]::NewGuid().ToString('N'))"
  $checkoutResponse = $null
  $checkoutData = $null
  do {
    $checkoutHeaders = New-OperationHeaders $ShopperToken (New-TraceId)
    $checkoutHeaders["Idempotency-Key"] = $checkoutKey
    $checkoutResponse = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/payments/$paymentId/checkout-sessions" `
      -Headers $checkoutHeaders
    Assert-ApiStatus $checkoutResponse @(200, 201, 202) "Checkout creation"
    $checkoutData = Get-ApiData $checkoutResponse.Body
    $checkoutUrl = [string](Get-PropertyValue $checkoutData "checkoutUrl")
    if (-not [string]::IsNullOrWhiteSpace($checkoutUrl)) { break }
    Start-Sleep -Seconds ([Math]::Min(3, (Get-RemainingSeconds)))
  } while ($true)

  $replayHeaders = New-OperationHeaders $ShopperToken (New-TraceId)
  $replayHeaders["Idempotency-Key"] = $checkoutKey
  $replay = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/payments/$paymentId/checkout-sessions" -Headers $replayHeaders
  Assert-ApiStatus $replay @(200, 201, 202) "Checkout idempotency replay"
  Write-Output "Checkout: PASS (status=$($checkoutResponse.StatusCode), replay=$($replay.StatusCode), URL withheld)"

  $sessionId = Get-StripeSessionIdFromUrl $checkoutUrl
  try { Start-Process -FilePath $checkoutUrl | Out-Null }
  catch { throw "Could not open the hosted Checkout page in the default browser." }
  Write-Host "Hosted Stripe Checkout opened. Complete it with a Stripe test card, then press Enter."
  [void](Read-Host "Press Enter after the test payment completes")

  $script:StripeSession = $null
  Wait-Condition -Description "Stripe Checkout completion" -Condition {
    $script:StripeSession = Invoke-StripeApiGet -Path "/v1/checkout/sessions/$sessionId" -SecretKey $secrets.SecretKey
    return [string](Get-PropertyValue $script:StripeSession "status") -eq "complete" -and
      [string](Get-PropertyValue $script:StripeSession "payment_status") -eq "paid"
  }
  $stripeSession = $script:StripeSession
  $metadata = Get-PropertyValue $stripeSession "metadata"
  $sessionPaymentId = [string](Get-PropertyValue $metadata "paymentId")
  $sessionOrderId = [string](Get-PropertyValue $metadata "orderId")
  if ($sessionPaymentId -ne [string]$paymentId -or $sessionOrderId -ne [string]$Order.id) {
    throw "Stripe Checkout metadata did not match the Order-owned Payment identity."
  }
  Write-Output "Stripe Checkout: PASS (test payment completed; provider identifiers withheld)"

  Wait-Condition -Description "Payment webhook convergence" -Condition {
    $current = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/payments/$paymentId" `
      -Headers (New-OperationHeaders $ShopperToken (New-TraceId))
    if ($current.StatusCode -ne 200) { return $false }
    return [string](Get-PropertyValue (Get-ApiData $current.Body) "status") -eq "SUCCEEDED"
  }
  Write-Output "Webhook delivery: PASS (Payment status=SUCCEEDED)"

  $attemptId = [string](Get-PropertyValue $metadata "attemptId")
  $eventMetadata = [ordered]@{
    paymentId = [string]$paymentId
    orderId = [string]$Order.id
  }
  if (-not [string]::IsNullOrWhiteSpace($attemptId)) { $eventMetadata.attemptId = $attemptId }
  $event = [ordered]@{
    id = "evt_phase24_$([guid]::NewGuid().ToString('N'))"
    object = "event"
    api_version = $StripeApiVersion
    created = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    livemode = $false
    type = "checkout.session.completed"
    data = [ordered]@{ object = [ordered]@{
      id = $sessionId
      object = "checkout.session"
      status = [string](Get-PropertyValue $stripeSession "status")
      payment_status = [string](Get-PropertyValue $stripeSession "payment_status")
      metadata = $eventMetadata
    } }
  }
  $payload = $event | ConvertTo-Json -Depth 20 -Compress
  Post-SignedStripeWebhook -Payload $payload -WebhookSecret $secrets.WebhookSecret -EventId $event.id
  Post-SignedStripeWebhook -Payload $payload -WebhookSecret $secrets.WebhookSecret -EventId $event.id
  Write-Output "Webhook replay: PASS (first and duplicate delivery acknowledged with HTTP 204)"

  Wait-Condition -Description "PaymentSucceeded Kafka outbox publication" -Condition {
    return (Get-KafkaTopicTotalEndOffset) -gt $offsetBefore
  }
  Write-Output "Kafka Payment event: PASS (flashsale.payment.events.v1 advanced)"

  $script:FinalOrderResponse = $null
  Wait-Condition -Description "confirmed Order convergence" -Condition {
    $script:FinalOrderResponse = Invoke-Api -Method GET -Uri "$BaseUri/api/v1/orders/$($Order.id)" `
      -Headers (New-OperationHeaders $ShopperToken (New-TraceId))
    if ($script:FinalOrderResponse.StatusCode -ne 200) { return $false }
    return [string](Get-PropertyValue (Get-ApiData $script:FinalOrderResponse.Body) "status") -eq "CONFIRMED"
  }
  $finalOrder = Get-ApiData $script:FinalOrderResponse.Body
  if ([string](Get-PropertyValue $finalOrder "purchaseRequestId") -ne [string]$Order.purchaseRequestId) {
    throw "Order final query changed purchaseRequestId."
  }

  $script:FinalReservationResponse = $null
  Wait-Condition -Description "confirmed reservation convergence" -Condition {
    $script:FinalReservationResponse = Invoke-Api -Method GET `
      -Uri "$BaseUri/api/v1/flash-sales/reservations/$($Reservation.Accepted.reservationId)" `
      -Headers (New-OperationHeaders $ShopperToken (New-TraceId))
    if ($script:FinalReservationResponse.StatusCode -ne 200) { return $false }
    return [string](Get-PropertyValue (Get-ApiData $script:FinalReservationResponse.Body) "status") -eq "CONFIRMED"
  }
  $finalReservation = Get-ApiData $script:FinalReservationResponse.Body
  if ([string](Get-PropertyValue $finalReservation "purchaseRequestId") -ne
      [string]$Reservation.Accepted.purchaseRequestId) {
    throw "Reservation final query changed purchaseRequestId."
  }
  Write-Output "Order Saga boundary: PASS (orderId=$($Order.id) status=CONFIRMED)"
  Write-Output "Reservation finalization: PASS (reservationId=$($Reservation.Accepted.reservationId) status=CONFIRMED)"
}

if (-not (Test-Path $OverlayPath)) { throw "Cloud overlay not found: $OverlayPath" }
Assert-Preflight
if (-not $Run) {
  Write-Output "Validation-only mode: no port-forward, Job, or business resource was changed."
  exit 0
}
if ([string]::IsNullOrWhiteSpace($AdminLogin)) { throw "-AdminLogin is required with -Run." }

$baseUri = $null
$shopper = $null
$product = $null
$campaign = $null
$reservation = $null
$ownerReservation = $null
$order = $null
$startedAt = Get-Date
try {
  $baseUri = Start-GatewayPortForward
  Assert-AnonymousAdminRejected $baseUri
  $adminToken = Invoke-AdminLogin $baseUri
  Write-Output "Authentication: PASS (admin authorities verified)"
  $shopper = Invoke-ShopperRegistration $baseUri
  Write-Output "Shopper registration/login: PASS (subject=$($shopper.UserId))"
  $product = Invoke-ProductFixture $baseUri $adminToken
  Write-Output "Product fixture: PASS (productId=$($product.ProductId) variantId=$($product.VariantId))"
  Invoke-InventoryFixture $product
  $campaign = Invoke-CampaignFixture $baseUri $adminToken $product
  $reservation = Invoke-ReservationWithRetry $baseUri $shopper.Token $campaign $product
  Write-Output "Reservation: PASS (purchaseRequestId=$($reservation.Accepted.purchaseRequestId) reservationId=$($reservation.Accepted.reservationId))"
  $ownerReservation = Assert-ReservationReplayAndOwner $baseUri $shopper.Token $reservation
  Write-Output "Reservation replay/owner: PASS (status=$($ownerReservation.status))"
  $order = Assert-OrderConvergence $baseUri $shopper.Token $reservation $product $campaign
  $elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
  Write-Output "Phase 22 internal E2E: PASS (orderId=$($order.id) status=$($order.status) elapsedSeconds=$elapsed)"
  if ($RunStripeCloudSmoke) {
    Invoke-StripeCloudSmoke -BaseUri $baseUri -ShopperToken $shopper.Token -Order $order -Reservation $reservation
    Write-Output "Phase 24 Stripe runtime smoke: PASS"
  }
  Write-Output "Manual cleanup: ProductId=$($product.ProductId) CampaignId=$($campaign.CampaignId) ShopperSubject=$($shopper.UserId)"
} finally {
  Stop-GatewayPortForward
}
