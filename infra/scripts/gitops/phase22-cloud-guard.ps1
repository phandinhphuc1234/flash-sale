<##
.SYNOPSIS
  Verify cloud ownership and configuration boundaries without changing the cluster.

.DESCRIPTION
  Phase 22 is a read-only guard for the single EKS cloud environment. It verifies the expected
  kube context, cloud overlay, Argo source/policy, application ConfigMap/Secret references, platform
  Secret references, private Service shape, Kafka safety settings, and disabled Payment flags.
  Secret values, .env values, and JWT contents are never requested or printed.
##>
[CmdletBinding()]
param(
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [string]$ArgoNamespace = "argocd",
  [string]$ArgoApplication = "flash-sale-cloud",
  [string]$ExpectedRepositoryUrl = "https://github.com/phandinhphuc1234/flash-sale.git",
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Phase 22 requires PowerShell 7 or newer. Run with: pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-cloud-guard.ps1"
}

$RunDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$CloudOverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$ExpectedServices = @(
  [pscustomobject]@{ Name = "api-gateway"; ConfigMap = "api-gateway-runtime-config"; Secret = "gateway-secrets" },
  [pscustomobject]@{ Name = "authentication-service"; ConfigMap = "authentication-service-runtime-config"; Secret = "authentication-secrets" },
  [pscustomobject]@{ Name = "product-service"; ConfigMap = "product-service-runtime-config"; Secret = "product-secrets" },
  [pscustomobject]@{ Name = "campaign-service"; ConfigMap = "campaign-service-runtime-config"; Secret = "campaign-secrets" },
  [pscustomobject]@{ Name = "flash-sale-service"; ConfigMap = "flash-sale-service-runtime-config"; Secret = "flashsale-secrets" },
  [pscustomobject]@{ Name = "inventory-service"; ConfigMap = "inventory-service-runtime-config"; Secret = "inventory-secrets" },
  [pscustomobject]@{ Name = "order-service"; ConfigMap = "order-service-runtime-config"; Secret = "order-secrets" },
  [pscustomobject]@{ Name = "payment-service"; ConfigMap = "payment-service-runtime-config"; Secret = "payment-secrets" }
  [pscustomobject]@{ Name = "cart-service"; ConfigMap = "cart-service-runtime-config"; Secret = "cart-secrets" }
)
$ExpectedSecretBoundaries = @(
  "platform-secrets", "gateway-secrets", "authentication-secrets", "product-secrets",
  "campaign-secrets", "flashsale-secrets", "inventory-secrets", "order-secrets",
  "payment-secrets", "cart-secrets", "auth-jwt"
)
$ExpectedPaymentFlags = @(
  "PAYMENT_ACCEPTANCE_ENABLED", "PAYMENT_CHECKOUT_ENABLED", "STRIPE_ENABLED",
  "PAYMENT_CONSUMER_ENABLED", "PAYMENT_OUTBOX_PUBLISHER_ENABLED", "PAYMENT_RECOVERY_ENABLED",
  "PAYMENT_WEBHOOK_PROCESSING_ENABLED"
)
$ForbiddenConfigMapKeys = @(
  "POSTGRES_PASSWORD", "REDIS_PASSWORD", "RATE_LIMIT_KEY_HMAC_SECRET", "AUTH_THROTTLE_HMAC_SECRET",
  "CAMPAIGN_CLIENT_SECRET", "FLASHSALE_CLIENT_SECRET", "STRIPE_SECRET_KEY", "STRIPE_PUBLISHABLE_KEY",
  "STRIPE_WEBHOOK_SECRET", "JWT_PRIVATE_KEY_PEM", "JWT_PUBLIC_KEY_PEM"
)
$script:LiveDeployments = @{}
$script:LiveConfigMaps = @{}
$script:LiveStatefulSets = @{}
$script:LiveSecretNames = @()
$script:LiveServices = @()

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($RunDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Phase 22 exceeded its $TimeoutSeconds-second execution budget." }
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
    $null = Get-RemainingTimeoutSeconds
    if (-not $process.Start()) { throw "Could not start native command '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit((Get-RemainingTimeoutSeconds) * 1000)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Phase 22 execution budget."
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

function Get-NativeText {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [string]$Description = $Command
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments
  if ($result.ExitCode -ne 0) {
    throw "$Description failed with exit code $($result.ExitCode): $(Get-BoundedDiagnostic $result.StandardError)"
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
  catch { throw "$Description returned invalid JSON: $($_.Exception.Message)" }
}

function Get-JsonPropertyValue {
  param([AllowNull()][object]$Object, [Parameter(Mandatory)][string]$PropertyName)
  if ($null -eq $Object) { return $null }
  $property = $Object.PSObject.Properties[$PropertyName]
  if ($null -eq $property) { return $null }
  return $property.Value
}

function Assert-ResourceExists {
  param([Parameter(Mandatory)][ValidateSet("configmap", "secret")][string]$Kind, [Parameter(Mandatory)][string]$Name)
  $resource = Get-NativeText -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", $Kind, $Name, "--ignore-not-found", "-o", "name"
  ) -Description "$Kind/$Name metadata"
  if ([string]::IsNullOrWhiteSpace($resource)) { throw "Required $Kind '$Namespace/$Name' is missing." }
}

function Initialize-LiveInventory {
  $deploymentInventory = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "deployments", "-o", "json"
  ) -Description "cloud Deployment inventory"
  foreach ($item in @($deploymentInventory.items)) {
    $name = [string](Get-JsonPropertyValue (Get-JsonPropertyValue $item "metadata") "name")
    if (-not [string]::IsNullOrWhiteSpace($name)) { $script:LiveDeployments[$name] = $item }
  }

  $configInventory = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "configmaps", "-o", "json"
  ) -Description "cloud ConfigMap inventory"
  foreach ($item in @($configInventory.items)) {
    $name = [string](Get-JsonPropertyValue (Get-JsonPropertyValue $item "metadata") "name")
    if (-not [string]::IsNullOrWhiteSpace($name)) { $script:LiveConfigMaps[$name] = $item }
  }

  $secretInventory = Get-NativeText -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "secrets", "-o", "name"
  ) -Description "Secret name inventory"
  $script:LiveSecretNames = @($secretInventory -split "`r?`n" |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { ($_ -split "/", 2)[-1] })

  $statefulInventory = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "statefulsets", "-o", "json"
  ) -Description "cloud StatefulSet inventory"
  foreach ($item in @($statefulInventory.items)) {
    $name = [string](Get-JsonPropertyValue (Get-JsonPropertyValue $item "metadata") "name")
    if (-not [string]::IsNullOrWhiteSpace($name)) { $script:LiveStatefulSets[$name] = $item }
  }

  $serviceInventory = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "services", "-o", "json"
  ) -Description "cloud Service inventory"
  $script:LiveServices = @($serviceInventory.items)
  Write-Output ("Live metadata inventory: deployments={0} configmaps={1} secret-names={2} statefulsets={3} services={4}" -f
    $script:LiveDeployments.Count, $script:LiveConfigMaps.Count, $script:LiveSecretNames.Count,
    $script:LiveStatefulSets.Count, $script:LiveServices.Count)
}

function Assert-ContextAndOverlay {
  $context = Get-NativeText -Command "kubectl" -Arguments @("config", "current-context") -Description "kubectl context"
  if ($context -notmatch "(^|[:/])cluster/$([regex]::Escape($ExpectedClusterName))$") {
    throw "kubectl context '$context' does not identify expected cluster '$ExpectedClusterName'."
  }
  $rendered = Get-NativeText -Command "kubectl" -Arguments @("kustomize", $CloudOverlayPath) -Description "cloud Kustomize render"
  if ([string]::IsNullOrWhiteSpace($rendered)) { throw "Cloud Kustomize render was empty." }
  $null = Get-NativeText -Command "kubectl" -Arguments @("apply", "--dry-run=client", "-k", $CloudOverlayPath) -Description "cloud Kustomize dry-run"
  Write-Output "Cloud context/overlay: PASS ($context)"
}

function Assert-ArgoOwnership {
  $application = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $ArgoNamespace, "get", "application", $ArgoApplication, "-o", "json"
  ) -Description "Argo Application $ArgoApplication"
  $source = Get-JsonPropertyValue $application.spec "source"
  $destination = Get-JsonPropertyValue $application.spec "destination"
  $policy = Get-JsonPropertyValue $application.spec "syncPolicy"
  $automated = Get-JsonPropertyValue $policy "automated"
  $sync = Get-JsonPropertyValue $application.status.sync "status"
  $health = Get-JsonPropertyValue $application.status.health "status"
  $repo = [string](Get-JsonPropertyValue $source "repoURL")
  $path = [string](Get-JsonPropertyValue $source "path")
  $target = [string](Get-JsonPropertyValue $source "targetRevision")
  $destinationNamespace = [string](Get-JsonPropertyValue $destination "namespace")
  $destinationServer = [string](Get-JsonPropertyValue $destination "server")
  $selfHeal = [bool](Get-JsonPropertyValue $automated "selfHeal")
  $prune = [bool](Get-JsonPropertyValue $automated "prune")
  $revision = [string](Get-JsonPropertyValue $application.status.sync "revision")
  if ($sync -ne "Synced" -or $health -ne "Healthy") { throw "Argo '$ArgoApplication' is not Synced and Healthy." }
  if ($repo -ne $ExpectedRepositoryUrl -or $path -ne "infra/k8s/overlays/cloud" -or $target -ne "develop") {
    throw "Argo '$ArgoApplication' source drift detected (repo/path/target)."
  }
  if ($destinationNamespace -ne $Namespace -or $destinationServer -ne "https://kubernetes.default.svc") {
    throw "Argo '$ArgoApplication' destination drift detected."
  }
  if (-not $selfHeal -or $prune) { throw "Argo '$ArgoApplication' policy must self-heal and keep pruning disabled." }
  if ([string]::IsNullOrWhiteSpace($revision)) { throw "Argo '$ArgoApplication' has no live revision." }
  Write-Output "Argo ownership: PASS (sync=$sync health=$health target=$target revision=$revision selfHeal=$selfHeal prune=$prune)"
}

function Assert-ApplicationBoundaries {
  foreach ($service in $ExpectedServices) {
    Write-Output "Checking application boundary: $($service.Name)"
    if (-not $script:LiveConfigMaps.ContainsKey($service.ConfigMap)) { throw "Required ConfigMap '$Namespace/$($service.ConfigMap)' is missing." }
    if ($service.Secret -notin $script:LiveSecretNames) { throw "Required Secret boundary '$Namespace/$($service.Secret)' is missing." }
    if (-not $script:LiveDeployments.ContainsKey($service.Name)) { throw "Required Deployment '$Namespace/$($service.Name)' is missing." }
    $deployment = $script:LiveDeployments[$service.Name]
    $containers = @($deployment.spec.template.spec.containers)
    if ($containers.Count -lt 1) { throw "Deployment '$($service.Name)' has no container." }
    $container = $containers[0]
    $configNames = @($container.envFrom | ForEach-Object {
      $ref = Get-JsonPropertyValue $_ "configMapRef"
      if ($null -ne $ref) { [string](Get-JsonPropertyValue $ref "name") }
    })
    $secretNames = @($container.envFrom | ForEach-Object {
      $ref = Get-JsonPropertyValue $_ "secretRef"
      if ($null -ne $ref) { [string](Get-JsonPropertyValue $ref "name") }
    })
    foreach ($requiredConfig in @("flash-sale-runtime-config", $service.ConfigMap)) {
      if ($requiredConfig -notin $configNames) { throw "Deployment '$($service.Name)' misses ConfigMap '$requiredConfig'." }
    }
    if ($service.Secret -notin $secretNames) { throw "Deployment '$($service.Name)' misses Secret boundary '$($service.Secret)'." }
    if ($service.Name -eq "authentication-service") {
      $jwtVolume = @($deployment.spec.template.spec.volumes | Where-Object {
        $secret = Get-JsonPropertyValue $_ "secret"
        $null -ne $secret -and (Get-JsonPropertyValue $secret "secretName") -eq "auth-jwt"
      })
      $jwtMount = @($container.volumeMounts | Where-Object { (Get-JsonPropertyValue $_ "mountPath") -eq "/run/secrets/auth-jwt" })
      if ($jwtVolume.Count -ne 1 -or $jwtMount.Count -ne 1) { throw "Authentication Service JWT Secret mount is missing or duplicated." }
      if ("auth-jwt" -notin $script:LiveSecretNames) { throw "Required Secret boundary '$Namespace/auth-jwt' is missing." }
    }
    Write-Output "Application boundary $($service.Name): PASS (ConfigMaps=$($configNames -join ',') Secret=$($service.Secret))"
  }
}

function Assert-ConfigMapSafety {
  $configMapNames = @("flash-sale-runtime-config") + @($ExpectedServices | ForEach-Object { $_.ConfigMap })
  foreach ($name in $configMapNames | Select-Object -Unique) {
    if (-not $script:LiveConfigMaps.ContainsKey($name)) { throw "Required ConfigMap '$Namespace/$name' is missing." }
    $config = $script:LiveConfigMaps[$name]
    $data = Get-JsonPropertyValue $config "data"
    $properties = if ($null -eq $data) { @() } else { @($data.PSObject.Properties) }
    $keys = @($properties | ForEach-Object { [string]$_.Name })
    $forbidden = @($keys | Where-Object { $_ -in $ForbiddenConfigMapKeys })
    if ($forbidden.Count -gt 0) { throw "ConfigMap '$name' contains Secret-owned key(s): $($forbidden -join ', ')." }
    foreach ($property in $properties) {
      if ([string]$property.Value -match "sk_(?:test|live)_|whsec_|-----BEGIN .*PRIVATE KEY-----") {
        throw "ConfigMap '$name' contains a provider credential or private key pattern."
      }
    }
  }
  Write-Output "ConfigMap safety: PASS (no Secret-owned keys or credential patterns)"
}

function Assert-PlatformSafety {
  foreach ($name in @("platform-secrets", "product-postgres-credentials")) {
    if ($name -notin $script:LiveSecretNames) { throw "Required Secret '$Namespace/$name' is missing." }
  }
  foreach ($statefulSetName in @("postgres", "redis")) {
    if (-not $script:LiveStatefulSets.ContainsKey($statefulSetName)) { throw "Required StatefulSet '$Namespace/$statefulSetName' is missing." }
    $statefulSet = $script:LiveStatefulSets[$statefulSetName]
    $container = @($statefulSet.spec.template.spec.containers)[0]
    $requiredEnv = if ($statefulSetName -eq "postgres") { @("POSTGRES_USER", "POSTGRES_PASSWORD") } else { @("REDIS_PASSWORD") }
    foreach ($envName in $requiredEnv) {
      $envEntry = @($container.env | Where-Object { (Get-JsonPropertyValue $_ "name") -eq $envName })
      if ($envEntry.Count -ne 1) { throw "$statefulSetName must define exactly one $envName entry." }
      $ref = Get-JsonPropertyValue $envEntry[0] "valueFrom"
      $secretRef = Get-JsonPropertyValue $ref "secretKeyRef"
      if ($null -eq $secretRef -or (Get-JsonPropertyValue $secretRef "name") -ne "platform-secrets") {
        throw "$statefulSetName $envName must come from platform-secrets."
      }
    }
  }
  $serviceTypes = @($script:LiveServices | ForEach-Object { [string]$_.spec.type })
  if (@($serviceTypes | Where-Object { $_ -in @("LoadBalancer", "NodePort") }).Count -gt 0) {
    throw "Cloud namespace contains a public LoadBalancer or NodePort Service."
  }
  $ingresses = Get-NativeText -Command "kubectl" -Arguments @("-n", $Namespace, "get", "ingress", "--ignore-not-found", "-o", "name") -Description "cloud Ingress inventory"
  if (-not [string]::IsNullOrWhiteSpace($ingresses)) { throw "Cloud namespace contains an Ingress; public exposure is out of scope." }
  Write-Output "Platform safety: PASS (Secret references, private Services, no Ingress)"
}

function Assert-KafkaSafety {
  if (-not $script:LiveStatefulSets.ContainsKey("kafka")) { throw "Required StatefulSet '$Namespace/kafka' is missing." }
  $kafka = $script:LiveStatefulSets["kafka"]
  $container = @($kafka.spec.template.spec.containers | Where-Object { (Get-JsonPropertyValue $_ "name") -eq "kafka" })[0]
  $envEntries = @($container.env)
  $autoCreate = @($envEntries | Where-Object { (Get-JsonPropertyValue $_ "name") -eq "KAFKA_AUTO_CREATE_TOPICS_ENABLE" })
  $logDirs = @($envEntries | Where-Object { (Get-JsonPropertyValue $_ "name") -eq "KAFKA_LOG_DIRS" })
  if ($autoCreate.Count -ne 1 -or [string](Get-JsonPropertyValue $autoCreate[0] "value") -ne "false") {
    throw "Kafka automatic topic creation must be disabled."
  }
  if ($logDirs.Count -ne 1 -or [string](Get-JsonPropertyValue $logDirs[0] "value") -ne "/var/lib/kafka/data/kafka-logs") {
    throw "Kafka log directory must be the isolated kafka-logs subdirectory."
  }
  Write-Output "Kafka safety: PASS (auto-create=false logDir=/var/lib/kafka/data/kafka-logs)"
}

function Assert-PaymentFlags {
  if (-not $script:LiveConfigMaps.ContainsKey("payment-service-runtime-config")) { throw "Required ConfigMap '$Namespace/payment-service-runtime-config' is missing." }
  $config = $script:LiveConfigMaps["payment-service-runtime-config"]
  $data = Get-JsonPropertyValue $config "data"
  foreach ($flag in $ExpectedPaymentFlags) {
    $value = [string](Get-JsonPropertyValue $data $flag)
    if ($value.ToLowerInvariant() -ne "false") { throw "Payment runtime flag $flag is '$value'; Phase 22 requires false." }
  }
  Write-Output "Payment flags: $($ExpectedPaymentFlags.Count)/$($ExpectedPaymentFlags.Count) disabled"
}

Write-Output "Phase 22 cloud environment guard started (read-only)."
Assert-ContextAndOverlay
Assert-ArgoOwnership
Initialize-LiveInventory
Assert-ApplicationBoundaries
Assert-ConfigMapSafety
Assert-PlatformSafety
Assert-KafkaSafety
Assert-PaymentFlags
Write-Output "Phase 22 cloud environment guard: PASS"
Write-Output "Secret values, .env values, and JWT contents were not read or printed. No Kubernetes state was changed."
