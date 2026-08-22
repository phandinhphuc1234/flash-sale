<#
.SYNOPSIS
  Run or validate the canonical internal authenticated Phase 22 cloud smoke.

.DESCRIPTION
  Validation mode is non-mutating. Run mode uses a loopback API Gateway port-forward, an existing
  ROLE_ADMIN login, a generated shopper, Gateway-owned Product/Campaign HTTP contracts, and an
  Inventory-owned one-shot fixture Job. It never executes SQL, reads another service database, or
  prints passwords, JWTs, cookies, Kubernetes Secret values, or full request bodies.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [string]$AdminLogin,
  [ValidateRange(1024, 65535)]
  [int]$LocalPort = 28082,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600,
  [ValidateRange(1, 100)]
  [int]$InventoryQuantity = 1
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
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 500) { return $Text }
  return $Text.Substring(0, 500) + "... [truncated]"
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
  $script:PortForward = Start-Process -FilePath "kubectl" -ArgumentList @(
    "-n", $Namespace, "port-forward", "--address", "127.0.0.1", "service/api-gateway", ("{0}:8080" -f $LocalPort)
  ) -RedirectStandardOutput $script:PortForwardOut -RedirectStandardError $script:PortForwardErr -WindowStyle Hidden -PassThru
  $baseUri = "http://127.0.0.1:$LocalPort"
  do {
    Start-Sleep -Milliseconds 500
    if ($script:PortForward.HasExited) { throw "Gateway port-forward exited before readiness." }
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
  foreach ($name in @("api-gateway", "authentication-service", "product-service", "campaign-service", "flash-sale-service", "inventory-service", "order-service")) {
    Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "rollout", "status", "deployment/$name", "--timeout=60s") -Description "Deployment/$name readiness" | Out-Null
  }
  $paymentConfig = Get-NativeJson -Command "kubectl" -Arguments @("-n", $Namespace, "get", "configmap", "payment-service-runtime-config", "-o", "json") -Description "Payment runtime flags"
  $data = Get-PropertyValue $paymentConfig "data"
  foreach ($flag in @("PAYMENT_ACCEPTANCE_ENABLED", "PAYMENT_CHECKOUT_ENABLED", "STRIPE_ENABLED", "PAYMENT_CONSUMER_ENABLED", "PAYMENT_OUTBOX_PUBLISHER_ENABLED", "PAYMENT_RECOVERY_ENABLED", "PAYMENT_WEBHOOK_PROCESSING_ENABLED")) {
    if ([string](Get-PropertyValue $data $flag).ToLowerInvariant() -ne "false") { throw "Payment flag '$flag' is not disabled." }
  }
  Write-Output "Phase 22 preflight: PASS (context=$context sync=$sync health=$health payment=7/7-disabled)"
}

function Invoke-AdminLogin {
  param([Parameter(Mandatory)][string]$BaseUri)
  $secure = Read-Host "Existing ROLE_ADMIN password" -AsSecureString
  $plain = Convert-SecurePassword $secure
  try {
    $response = Invoke-Api -Method POST -Uri "$BaseUri/api/v1/auth/login" -Body @{
      login = $AdminLogin; password = $plain; deviceName = "phase22-internal-e2e"
    }
  } finally { $plain = $null }
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
  $variantId = [guid]::NewGuid()
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
    variants = @(@{ id = $variantId; sku = $sku; barcode = $null; name = "Smoke Variant"; basePrice = 199000; currency = "VND"; status = "ACTIVE"; sortOrder = 0 })
    categories = @(); media = @()
  }
  Assert-ApiStatus $composition @(200) "Product composition"
  $version = [long](Get-PropertyValue (Get-ApiData $composition.Body) "version")
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
    $waitSeconds = [Math]::Min(180, (Get-RemainingSeconds))
    Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "wait", "--for=condition=complete", "job/$($script:FixtureJobName)", "--timeout=${waitSeconds}s") -Description "Inventory fixture completion" | Out-Null
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
    variantId = $Product.VariantId; campaignPrice = 199000; requestedQuantity = $InventoryQuantity; purchaseLimitPerUser = 1
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
  Write-Output "Campaign fixture: PASS (campaignId=$campaignId status=ACTIVE)"
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
  Write-Output "Manual cleanup: ProductId=$($product.ProductId) CampaignId=$($campaign.CampaignId) ShopperSubject=$($shopper.UserId)"
} finally {
  Stop-GatewayPortForward
}
