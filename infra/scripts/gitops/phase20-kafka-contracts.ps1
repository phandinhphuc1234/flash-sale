<#
.SYNOPSIS
  Validate or explicitly provision the approved cloud Kafka topics and Avro subjects.

.DESCRIPTION
  Default mode inspects Git, Argo CD, Kafka, and Schema Registry without changing broker or
  registry state. -Apply is merge-gated, reconciles the Argo-owned broker policy, expands only
  empty one-partition topics, creates missing topics, registers exact accepted schemas, and
  verifies the complete inventory. No topic, subject, schema version, message, consumer group, or
  Secret is deleted.
#>
[CmdletBinding()]
param(
  [switch]$Apply,
  [ValidateRange(1024, 65535)]
  [int]$LocalRegistryPort = 28081,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600,
  [string]$Namespace = "flash-sale",
  [string]$ArgoNamespace = "argocd",
  [string]$ExpectedClusterName = "flash-sale-dev"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RunDeadline = (Get-Date).AddSeconds($TimeoutSeconds)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$CloudOverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$KafkaManifestPath = Join-Path $CloudOverlayPath "platform\kafka-statefulset.yaml"
$SchemaRoot = Join-Path $RepoRoot "contracts\kafka-avro-contracts\src\main\avro\topics"
$SchemaScriptRoot = Join-Path $RepoRoot "infra\docker\schema-registry"
$ApplicationName = "flash-sale-cloud"
$ExpectedRepositoryUrl = "https://github.com/phandinhphuc1234/flash-sale.git"
$ExpectedTargetRevision = "develop"
$ExpectedApplicationPath = "infra/k8s/overlays/cloud"
$ExpectedPartitions = 3
$ExpectedReplicationFactor = 1
$ExpectedCompatibility = "BACKWARD_TRANSITIVE"
$DisabledPaymentFlags = @(
  "PAYMENT_ACCEPTANCE_ENABLED",
  "PAYMENT_CHECKOUT_ENABLED",
  "STRIPE_ENABLED",
  "PAYMENT_CONSUMER_ENABLED",
  "PAYMENT_OUTBOX_PUBLISHER_ENABLED",
  "PAYMENT_RECOVERY_ENABLED",
  "PAYMENT_WEBHOOK_PROCESSING_ENABLED"
)

$TopicContracts = @(
  "campaign.lifecycle.v1",
  "flashsale.purchase.events.v1",
  "flashsale.order.purchase-accepted.dlt.v1",
  "flashsale.order.events.v1",
  "flashsale.payment.commands.v1",
  "flashsale.payment.events.v1",
  "flashsale.payment.payment-requested.dlt.v1",
  "flashsale.purchase.commands.v1",
  "flashsale.order.payment-result.dlt.v1",
  "flashsale.flash-sale.purchase-command.dlt.v1",
  "flashsale.order.purchase-reservation-result.dlt.v1"
)
$ApprovedInternalTopics = @("__consumer_offsets", "_schemas")

$SubjectContracts = @(
  [pscustomobject]@{
    Subject = "campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1"
    SchemaPath = Join-Path $SchemaRoot "campaign.lifecycle.v1\CampaignScheduledV1.avsc"
  },
  [pscustomobject]@{
    Subject = "campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1"
    SchemaPath = Join-Path $SchemaRoot "campaign.lifecycle.v1\CampaignActivatedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseAcceptedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.purchase-accepted.dlt.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseAcceptedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.order.events.v1\OrderCreatedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.payment.commands.v1-com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.commands.v1\PaymentRequestedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.payment.payment-requested.dlt.v1-com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.commands.v1\PaymentRequestedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.events.v1\PaymentSucceededV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.events.v1\PaymentFailedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.purchase.commands.v1-com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.commands.v1\ConfirmPurchaseReservationV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.purchase.commands.v1-com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.commands.v1\ReleasePurchaseReservationV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseReservationConfirmedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseReservationReleasedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderConfirmedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.order.events.v1\OrderConfirmedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCancelledV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.order.events.v1\OrderCancelledV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderExpiredV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.order.events.v1\OrderExpiredV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderPaymentReviewRequiredV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.order.events.v1\OrderPaymentReviewRequiredV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.payment-result.dlt.v1-com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.events.v1\PaymentSucceededV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.payment-result.dlt.v1-com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.payment.events.v1\PaymentFailedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.flash-sale.purchase-command.dlt.v1-com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.commands.v1\ConfirmPurchaseReservationV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.flash-sale.purchase-command.dlt.v1-com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.commands.v1\ReleasePurchaseReservationV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.purchase-reservation-result.dlt.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseReservationConfirmedV1.avsc"
  },
  [pscustomobject]@{
    Subject = "flashsale.order.purchase-reservation-result.dlt.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseReservationReleasedV1.avsc"
  }
  # The reservation-results consumer shares flashsale.purchase.events.v1 with the
  # PurchaseAccepted consumer. If it rejects an accepted-purchase record, the
  # DeadLetterPublishingRecoverer serializes that record under this DLT subject.
  [pscustomobject]@{
    Subject = "flashsale.order.purchase-reservation-result.dlt.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1"
    SchemaPath = Join-Path $SchemaRoot "flashsale.purchase.events.v1\PurchaseAcceptedV1.avsc"
  }
)

$SchemaScripts = @(
  "register-campaign-schemas.ps1",
  "register-flashsale-schemas.ps1",
  "register-order-schemas.ps1",
  "register-payment-schemas.ps1",
  "register-purchase-saga-schemas.ps1"
)

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
  foreach ($argument in $Arguments) {
    $null = $startInfo.ArgumentList.Add([string]$argument)
  }
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingTimeoutSeconds
    if (-not $process.Start()) { throw "Could not start native command '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $remainingMilliseconds = (Get-RemainingTimeoutSeconds) * 1000
    if (-not $process.WaitForExit($remainingMilliseconds)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Phase 20 execution budget."
    }
    $process.WaitForExit()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = $stdoutTask.GetAwaiter().GetResult().Trim()
      StandardError = $stderrTask.GetAwaiter().GetResult().Trim()
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    if ($null -ne $process) { $process.Dispose() }
  }
}

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($RunDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) {
    throw "Phase 20 exceeded its $TimeoutSeconds-second execution budget."
  }
  return $remaining
}

function Get-BoundedDiagnostic {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 2000) { return $Text }
  return $Text.Substring(0, 2000) + "... [truncated]"
}

function Invoke-Native {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments
  if (-not [string]::IsNullOrWhiteSpace($result.StandardOutput)) {
    Write-Output $result.StandardOutput
  }
  if ($result.ExitCode -ne 0) {
    $diagnostic = Get-BoundedDiagnostic $result.StandardError
    throw "$Command failed with exit code $($result.ExitCode): $diagnostic"
  }
  if (-not [string]::IsNullOrWhiteSpace($result.StandardError)) {
    Write-Output (Get-BoundedDiagnostic $result.StandardError)
  }
}

function Get-NativeText {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [switch]$AllowFailure
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments
  if ($result.ExitCode -ne 0 -and -not $AllowFailure) {
    $diagnostic = Get-BoundedDiagnostic $result.StandardError
    throw "$Command failed with exit code $($result.ExitCode): $diagnostic"
  }
  if ($result.ExitCode -ne 0) { return "" }
  return $result.StandardOutput
}

function Get-KafkaText {
  param([Parameter(Mandatory)][string[]]$Arguments)
  return Get-NativeText "kubectl" (@(
      "-n", $Namespace, "exec", "kafka-0", "-c", "kafka", "--"
    ) + $Arguments)
}

function Invoke-Kafka {
  param([Parameter(Mandatory)][string[]]$Arguments)
  Invoke-Native "kubectl" (@(
      "-n", $Namespace, "exec", "kafka-0", "-c", "kafka", "--"
    ) + $Arguments)
}

function Add-GeneratedStringProperties {
  param([AllowNull()][object]$Node)
  if ($null -eq $Node) { return }
  if ($Node -is [System.Collections.IList]) {
    for ($index = 0; $index -lt $Node.Count; $index++) {
      if ($Node[$index] -is [string] -and $Node[$index] -eq "string") {
        $Node[$index] = [pscustomobject]@{ type = "string"; "avro.java.string" = "String" }
      } else {
        Add-GeneratedStringProperties $Node[$index]
      }
    }
    return
  }
  if (-not ($Node -is [pscustomobject])) { return }
  foreach ($property in @($Node.PSObject.Properties)) {
    if ($property.Name -eq "type" -and $property.Value -is [string] -and
        $property.Value -eq "string" -and
        -not ($Node.PSObject.Properties.Name -contains "logicalType")) {
      $property.Value = [pscustomobject]@{ type = "string"; "avro.java.string" = "String" }
    } else {
      Add-GeneratedStringProperties $property.Value
    }
  }
}

function Get-CanonicalSchemaDocument {
  param([Parameter(Mandatory)][string]$SchemaPath)
  $schema = Get-Content -LiteralPath $SchemaPath -Raw | ConvertFrom-Json -Depth 100
  Add-GeneratedStringProperties $schema
  return $schema | ConvertTo-Json -Depth 100 -Compress
}

function Invoke-RegistryRequest {
  param(
    [Parameter(Mandatory)][ValidateSet("Get", "Post")][string]$Method,
    [Parameter(Mandatory)][string]$Path,
    [string]$Body
  )
  $mediaType = "application/vnd.schemaregistry.v1+json"
  $parameters = @{
    Method = $Method
    Uri = "$script:RegistryUrl$Path"
    Headers = @{ Accept = $mediaType; "Content-Type" = $mediaType }
    TimeoutSec = 10
    ErrorAction = "Stop"
  }
  if ($null -ne $Body) { $parameters.Body = $Body }
  $response = Invoke-RestMethod @parameters
  # PowerShell 7 preserves a top-level JSON array as one pipeline object. Enumerate it explicitly
  # so an empty Registry /subjects response remains an empty collection instead of one nested
  # System.Object[] value that would look like an unexpected subject.
  if ($response -is [System.Array]) {
    foreach ($item in $response) { Write-Output $item }
    return
  }
  return $response
}

function Assert-ExactSubject {
  param([Parameter(Mandatory)][pscustomobject]$Contract)
  $subjectPath = [Uri]::EscapeDataString($Contract.Subject)
  $schemaDocument = Get-CanonicalSchemaDocument $Contract.SchemaPath
  $payload = @{ schema = $schemaDocument; schemaType = "AVRO" } | ConvertTo-Json -Compress
  try {
    $lookup = Invoke-RegistryRequest -Method Post -Path "/subjects/${subjectPath}?normalize=true" -Body $payload
  } catch {
    throw "Subject '$($Contract.Subject)' does not contain the exact canonical Git schema."
  }
  $latest = Invoke-RegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
  $configuration = Invoke-RegistryRequest -Method Get -Path "/config/$subjectPath"
  if ([int]$lookup.id -ne [int]$latest.id -or [int]$lookup.version -ne [int]$latest.version) {
    throw "Canonical Git schema is not latest for subject '$($Contract.Subject)'."
  }
  if ($configuration.compatibilityLevel -ne $ExpectedCompatibility) {
    throw "Subject '$($Contract.Subject)' must use $ExpectedCompatibility compatibility."
  }
  Write-Output "Verified subject $($Contract.Subject) id=$($latest.id) version=$($latest.version) compatibility=$ExpectedCompatibility."
}

function Get-TopicEndOffsetTotal {
  param(
    [Parameter(Mandatory)][string]$Topic,
    [Parameter(Mandatory)][ValidateRange(1, 1000)][int]$ExpectedPartitionCount
  )
  $offsetText = Get-KafkaText @(
    "/opt/kafka/bin/kafka-get-offsets.sh", "--bootstrap-server", "localhost:9092",
    "--topic", $Topic
  )
  [long]$total = 0
  $seenPartitions = [System.Collections.Generic.HashSet[int]]::new()
  $offsetLines = @($offsetText -split "`r?`n" |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  if ($offsetLines.Count -eq 0) {
    throw "Kafka returned no end-offset evidence for topic '$Topic'."
  }
  foreach ($line in $offsetLines) {
    if ($line -notmatch '^[^:]+:(?<partition>\d+):(?<offset>\d+)$') {
      throw "Kafka returned unparseable end-offset evidence for topic '$Topic'."
    }
    $partition = [int]$Matches.partition
    if (-not $seenPartitions.Add($partition)) {
      throw "Kafka returned duplicate end-offset evidence for topic '$Topic' partition $partition."
    }
    $total += [long]$Matches.offset
  }
  if ($seenPartitions.Count -ne $ExpectedPartitionCount) {
    throw "Kafka returned offsets for $($seenPartitions.Count) partitions of '$Topic'; expected $ExpectedPartitionCount."
  }
  return $total
}

function Get-TopicInventory {
  $topicListText = Get-KafkaText @(
    "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "localhost:9092", "--list"
  )
  $liveTopics = @($topicListText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  $unexpected = @($liveTopics | Where-Object {
      $_ -notin $ApprovedInternalTopics -and $_ -notin $TopicContracts
    })
  if ($unexpected.Count -gt 0) {
    throw "Unexpected application topics require review: $($unexpected -join ', ')."
  }

  $observations = @()
  foreach ($topic in $TopicContracts) {
    if ($topic -notin $liveTopics) {
      $observations += [pscustomobject]@{
        Topic = $topic; State = "Missing"; Partitions = 0; ReplicationFactor = 0; EndOffset = 0
      }
      continue
    }
    $description = Get-KafkaText @(
      "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "localhost:9092",
      "--describe", "--topic", $topic
    )
    if ($description -notmatch "PartitionCount:\s*(?<partitions>\d+)" -or
        $description -notmatch "ReplicationFactor:\s*(?<replication>\d+)") {
      throw "Could not parse topology for topic '$topic'."
    }
    $partitions = [int]([regex]::Match($description, "PartitionCount:\s*(\d+)").Groups[1].Value)
    $replication = [int]([regex]::Match($description, "ReplicationFactor:\s*(\d+)").Groups[1].Value)
    if ($replication -ne $ExpectedReplicationFactor) {
      throw "Topic '$topic' has RF=$replication; expected RF=$ExpectedReplicationFactor."
    }
    if ($partitions -eq $ExpectedPartitions) {
      $observations += [pscustomobject]@{
        Topic = $topic; State = "Verified"; Partitions = $partitions
        ReplicationFactor = $replication
        EndOffset = Get-TopicEndOffsetTotal $topic $partitions
      }
      continue
    }
    if ($partitions -eq 1) {
      $endOffset = Get-TopicEndOffsetTotal $topic $partitions
      if ($endOffset -ne 0) {
        throw "Topic '$topic' has one partition and end offset $endOffset. Refusing an ordering-changing expansion."
      }
      $observations += [pscustomobject]@{
        Topic = $topic; State = "ExpandableEmpty"; Partitions = 1
        ReplicationFactor = $replication; EndOffset = 0
      }
      continue
    }
    throw "Topic '$topic' has $partitions partitions; expected $ExpectedPartitions and no safe correction is authorized."
  }
  return $observations
}

function Wait-ApplicationHealthy {
  param([Parameter(Mandatory)][string]$ExpectedRevision)
  $lastState = ""
  while ((Get-Date) -lt $RunDeadline) {
    $applicationText = Get-NativeText "kubectl" @(
      "-n", $ArgoNamespace, "get", "application", $ApplicationName, "-o", "json"
    ) -AllowFailure
    if ([string]::IsNullOrWhiteSpace($applicationText)) {
      Start-Sleep -Seconds 5
      continue
    }
    $application = $applicationText | ConvertFrom-Json -Depth 100
    $state = "$($application.status.sync.status)|$($application.status.health.status)|$($application.status.sync.revision)"
    if ($state -ne $lastState -and -not [string]::IsNullOrWhiteSpace($state)) {
      Write-Output "Application $ApplicationName state: $state"
      $lastState = $state
    }
    if ($application.status.sync.status -eq "Synced" -and
        $application.status.health.status -eq "Healthy" -and
        $application.status.sync.revision -eq $ExpectedRevision) { return }
    Start-Sleep -Seconds 5
  }
  throw "Application '$ApplicationName' did not become Synced|Healthy at revision $ExpectedRevision within the Phase 20 execution budget."
}

function Assert-LocalPortAvailable {
  param([Parameter(Mandatory)][int]$Port)
  $existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -ne $existing) {
    throw "Local port $Port is already in use. Choose another port with -LocalRegistryPort."
  }
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
  try { $listener.Start() }
  catch { throw "Local port $Port is already in use. Choose another port with -LocalRegistryPort." }
  finally { $listener.Stop() }
}

function Assert-LivePaymentFlagsDisabled {
  $configMap = Get-NativeText "kubectl" @(
    "-n", $Namespace, "get", "configmap", "payment-service-runtime-config", "-o", "json"
  ) | ConvertFrom-Json -Depth 100
  foreach ($disabledFlag in $DisabledPaymentFlags) {
    $property = $configMap.data.PSObject.Properties[$disabledFlag]
    if ($null -eq $property -or [string]$property.Value -ne "false") {
      throw "Live Payment flag '$disabledFlag' must remain false in Phase 20."
    }
  }
  Write-Output "Live Payment runtime flags: 7/7 disabled."
}

function Get-LiveKafkaAutoCreateValue {
  $kafkaJson = Get-NativeText "kubectl" @(
    "-n", $Namespace, "get", "statefulset", "kafka", "-o", "json"
  ) | ConvertFrom-Json -Depth 100
  $kafkaContainer = @($kafkaJson.spec.template.spec.containers |
      Where-Object { $_.name -eq "kafka" })[0]
  return @($kafkaContainer.env | Where-Object {
      $_.name -eq "KAFKA_AUTO_CREATE_TOPICS_ENABLE"
    } | ForEach-Object { $_.value }) | Select-Object -First 1
}

function Invoke-SchemaScript {
  param(
    [Parameter(Mandatory)][string]$ScriptPath,
    [switch]$CheckOnly
  )
  $arguments = @(
    "-NoLogo", "-NoProfile", "-File", $ScriptPath,
    "-SchemaRegistryUrl", $script:RegistryUrl
  )
  if ($CheckOnly) { $arguments += "-CheckOnly" }
  $tempBase = Join-Path ([IO.Path]::GetTempPath()) (
    "flash-sale-schema-script-" + [guid]::NewGuid().ToString("N")
  )
  $childOut = "$tempBase.out.log"
  $childErr = "$tempBase.err.log"
  $child = $null
  try {
    $child = Start-Process -FilePath "pwsh" -ArgumentList $arguments `
      -RedirectStandardOutput $childOut -RedirectStandardError $childErr `
      -WindowStyle Hidden -PassThru
    $remainingMilliseconds = (Get-RemainingTimeoutSeconds) * 1000
    if (-not $child.WaitForExit($remainingMilliseconds)) {
      Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
      throw "Schema script '$([IO.Path]::GetFileName($ScriptPath))' exceeded the Phase 20 execution budget."
    }
    $stdoutContent = if (Test-Path -LiteralPath $childOut) {
      Get-Content -LiteralPath $childOut -Raw
    } else { $null }
    $stderrContent = if (Test-Path -LiteralPath $childErr) {
      Get-Content -LiteralPath $childErr -Raw
    } else { $null }
    $stdout = if ($null -eq $stdoutContent) { "" } else { $stdoutContent.Trim() }
    $stderr = if ($null -eq $stderrContent) { "" } else { $stderrContent.Trim() }
    if (-not [string]::IsNullOrWhiteSpace($stdout)) { Write-Output $stdout }
    if ($child.ExitCode -ne 0) {
      $diagnostic = if ($stderr.Length -gt 2000) { $stderr.Substring(0, 2000) } else { $stderr }
      throw "Schema script '$([IO.Path]::GetFileName($ScriptPath))' failed with exit code $($child.ExitCode): $diagnostic"
    }
  } finally {
    if ($null -ne $child -and -not $child.HasExited) {
      Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $childOut, $childErr -Force -ErrorAction SilentlyContinue
  }
}

foreach ($requiredPath in @($CloudOverlayPath, $KafkaManifestPath) +
    @($SubjectContracts | ForEach-Object { $_.SchemaPath }) +
    @($SchemaScripts | ForEach-Object { Join-Path $SchemaScriptRoot $_ })) {
  if (-not (Test-Path -LiteralPath $requiredPath)) {
    throw "Required Phase 20 path is missing: $requiredPath"
  }
}

$context = Get-NativeText "kubectl" @("config", "current-context")
if ($context -notmatch [regex]::Escape($ExpectedClusterName)) {
  throw "kubectl context '$context' does not target expected cluster '$ExpectedClusterName'."
}
Write-Output "kubectl context: $context"

$renderedCloud = Get-NativeText "kubectl" @("kustomize", $CloudOverlayPath)
$null = Get-NativeText "kubectl" @("apply", "--dry-run=client", "-k", $CloudOverlayPath)
if ($renderedCloud -notmatch '(?ms)- name:\s*KAFKA_AUTO_CREATE_TOPICS_ENABLE\s*\r?\n\s*value:\s*[''"]?false[''"]?\s*$') {
  throw "Cloud desired state must disable Kafka automatic topic creation."
}

$paymentConfig = Get-Content -LiteralPath (Join-Path $CloudOverlayPath "config\payment-service-runtime-config.yaml") -Raw
foreach ($disabledFlag in $DisabledPaymentFlags) {
  if ($paymentConfig -notmatch "(?m)^\s*${disabledFlag}:\s*['`"]?false['`"]?\s*$") {
    throw "Payment flag '$disabledFlag' must remain disabled in Phase 20."
  }
}
Assert-LivePaymentFlagsDisabled

$applicationJson = Get-NativeText "kubectl" @(
  "-n", $ArgoNamespace, "get", "application", $ApplicationName, "-o", "json"
) | ConvertFrom-Json -Depth 100
if ($applicationJson.spec.source.repoURL -ne $ExpectedRepositoryUrl -or
    $applicationJson.spec.source.targetRevision -ne $ExpectedTargetRevision -or
    $applicationJson.spec.source.path -ne $ExpectedApplicationPath -or
    $applicationJson.spec.destination.namespace -ne $Namespace) {
  throw "Argo Application '$ApplicationName' does not own the expected develop cloud overlay and namespace."
}

$expectedRevision = $null
if ($Apply) {
  Invoke-Native "git" @("-C", $RepoRoot, "fetch", "origin", "develop", "--prune")
  $expectedRevision = Get-NativeText "git" @("-C", $RepoRoot, "rev-parse", "origin/develop")
  $headRevision = Get-NativeText "git" @("-C", $RepoRoot, "rev-parse", "HEAD")
  $branchName = Get-NativeText "git" @("-C", $RepoRoot, "branch", "--show-current")
  $phase20AssetPaths = @(
    "infra/scripts/gitops/phase20-kafka-contracts.ps1",
    "infra/k8s/overlays/cloud",
    "infra/k8s/overlays/cloud/platform/kafka-statefulset.yaml",
    "infra/docker/schema-registry/register-campaign-schemas.ps1",
    "infra/docker/schema-registry/register-flashsale-schemas.ps1",
    "infra/docker/schema-registry/register-order-schemas.ps1",
    "infra/docker/schema-registry/register-payment-schemas.ps1",
    "infra/docker/schema-registry/register-purchase-saga-schemas.ps1"
  )
  $phase20AssetPaths += @($SubjectContracts.SchemaPath | ForEach-Object {
      [IO.Path]::GetRelativePath($RepoRoot, $_).Replace('\', '/')
    } | Sort-Object -Unique)
  $dirtyAssets = Get-NativeText "git" (@(
      "-C", $RepoRoot, "status", "--porcelain", "--"
    ) + $phase20AssetPaths)
  if ($branchName -ne "develop" -or $headRevision -ne $expectedRevision -or
      -not [string]::IsNullOrWhiteSpace($dirtyAssets)) {
    throw "Run Phase 20 -Apply only from a clean develop branch exactly matching freshly fetched origin/develop."
  }
  Write-Output "Merge gate: develop@$expectedRevision."
}

$autoCreateValue = Get-LiveKafkaAutoCreateValue
if ($autoCreateValue -eq "false") {
  Write-Output "Kafka automatic topic creation: disabled."
} else {
  Write-Output "Kafka automatic topic creation: pending Phase 20 merge/reconciliation."
}

$topicObservations = @(Get-TopicInventory)
foreach ($observation in $topicObservations) {
  Write-Output ("Topic {0}: state={1} partitions={2} rf={3} endOffset={4}" -f
    $observation.Topic, $observation.State, $observation.Partitions,
    $observation.ReplicationFactor, $observation.EndOffset)
}

Assert-LocalPortAvailable $LocalRegistryPort
$tempBase = Join-Path ([IO.Path]::GetTempPath()) ("flash-sale-registry-" + [guid]::NewGuid().ToString("N"))
$stdoutPath = "$tempBase.out.log"
$stderrPath = "$tempBase.err.log"
$portForward = $null
$script:RegistryUrl = "http://127.0.0.1:$LocalRegistryPort"

try {
  $portForward = Start-Process -FilePath "kubectl" -ArgumentList @(
    "-n", $Namespace, "port-forward", "--address", "127.0.0.1",
    "service/schema-registry", ("{0}:8081" -f $LocalRegistryPort)
  ) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath `
    -WindowStyle Hidden -PassThru

  $liveSubjects = $null
  do {
    Start-Sleep -Milliseconds 500
    if ($portForward.HasExited) {
      throw "Schema Registry port-forward exited before the Registry became available."
    }
    try {
      $liveSubjects = @(Invoke-RegistryRequest -Method Get -Path "/subjects")
      break
    } catch { }
  } while ((Get-Date) -lt $RunDeadline)
  if ($null -eq $liveSubjects) {
    throw "Schema Registry did not become available within the Phase 20 execution budget."
  }

  $portOwner = Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $LocalRegistryPort `
      -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.OwningProcess -eq $portForward.Id } |
    Select-Object -First 1
  if ($null -eq $portOwner) {
    throw "Local port $LocalRegistryPort is not owned by the kubectl process started by this script."
  }

  $expectedSubjectNames = @($SubjectContracts.Subject)
  $unexpectedSubjects = @($liveSubjects | Where-Object { $_ -notin $expectedSubjectNames })
  if ($unexpectedSubjects.Count -gt 0) {
    throw "Unexpected Schema Registry subjects require review: $($unexpectedSubjects -join ', ')."
  }
  $missingSubjects = @($expectedSubjectNames | Where-Object { $_ -notin $liveSubjects })
  foreach ($contract in @($SubjectContracts | Where-Object { $_.Subject -in $liveSubjects })) {
    Assert-ExactSubject $contract
  }

  if ($Apply) {
    # No live mutation occurs before both Kafka and Registry drift preflights above have passed.
    Invoke-Native "kubectl" @(
      "-n", $ArgoNamespace, "annotate", "application", $ApplicationName,
      "argocd.argoproj.io/refresh=hard", "--overwrite"
    )
    Wait-ApplicationHealthy -ExpectedRevision $expectedRevision
    Invoke-Native "kubectl" @(
      "-n", $Namespace, "rollout", "status", "statefulset/kafka",
      ("--timeout=" + (Get-RemainingTimeoutSeconds) + "s")
    )
    Invoke-Native "kubectl" @(
      "-n", $Namespace, "rollout", "status", "deployment/schema-registry",
      ("--timeout=" + (Get-RemainingTimeoutSeconds) + "s")
    )
    Assert-LivePaymentFlagsDisabled
    $autoCreateValue = Get-LiveKafkaAutoCreateValue
    if ($autoCreateValue -ne "false") {
      throw "Live Kafka broker has not reconciled KAFKA_AUTO_CREATE_TOPICS_ENABLE=false."
    }

    $liveSubjects = @(Invoke-RegistryRequest -Method Get -Path "/subjects")
    $unexpectedSubjects = @($liveSubjects | Where-Object { $_ -notin $expectedSubjectNames })
    if ($unexpectedSubjects.Count -gt 0) {
      throw "Schema Registry changed during reconciliation; unexpected subjects require review: $($unexpectedSubjects -join ', ')."
    }
    foreach ($contract in @($SubjectContracts | Where-Object { $_.Subject -in $liveSubjects })) {
      Assert-ExactSubject $contract
    }

    # Re-read topics after Kafka reconciliation and recheck offsets immediately before expansion.
    $topicObservations = @(Get-TopicInventory)
    foreach ($observation in $topicObservations) {
      if ($observation.State -eq "Missing") {
        Invoke-Kafka @(
          "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "localhost:9092",
          "--create", "--if-not-exists", "--topic", $observation.Topic,
          "--partitions", "$ExpectedPartitions", "--replication-factor", "$ExpectedReplicationFactor"
        )
        continue
      }
      if ($observation.State -eq "ExpandableEmpty") {
        $currentEndOffset = Get-TopicEndOffsetTotal $observation.Topic 1
        if ($currentEndOffset -ne 0) {
          throw "Topic '$($observation.Topic)' received records after preflight. Refusing partition expansion."
        }
        Invoke-Kafka @(
          "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "localhost:9092",
          "--alter", "--topic", $observation.Topic, "--partitions", "$ExpectedPartitions"
        )
      }
    }
    $topicObservations = @(Get-TopicInventory)
    $unverifiedTopics = @($topicObservations | Where-Object { $_.State -ne "Verified" })
    if ($unverifiedTopics.Count -gt 0) {
      throw "Topic verification failed after apply: $($unverifiedTopics.Topic -join ', ')."
    }

    foreach ($schemaScript in $SchemaScripts) {
      Invoke-SchemaScript -ScriptPath (Join-Path $SchemaScriptRoot $schemaScript)
    }
    $liveSubjects = @(Invoke-RegistryRequest -Method Get -Path "/subjects")
    $missingSubjects = @($expectedSubjectNames | Where-Object { $_ -notin $liveSubjects })
    if ($missingSubjects.Count -gt 0) {
      throw "Schema registration did not create: $($missingSubjects -join ', ')."
    }
    foreach ($contract in $SubjectContracts) {
      Assert-ExactSubject $contract
    }
    foreach ($schemaScript in $SchemaScripts) {
      Invoke-SchemaScript -ScriptPath (Join-Path $SchemaScriptRoot $schemaScript) -CheckOnly
    }
  }

  if (-not $Apply) {
    $pendingTopics = @($topicObservations | Where-Object { $_.State -ne "Verified" })
    $brokerPolicy = if ([string]::IsNullOrWhiteSpace($autoCreateValue)) {
      "not-configured"
    } else { $autoCreateValue }
    if ($pendingTopics.Count -gt 0 -or $missingSubjects.Count -gt 0 -or
        $autoCreateValue -ne "false") {
      Write-Output "Phase 20 desired state is pending: topics=$($pendingTopics.Count) subjects=$($missingSubjects.Count) brokerPolicy=$brokerPolicy."
      Write-Output "Validation-only mode: no broker, registry, Argo, workload, or Secret state was changed."
      exit 2
    }
  }
} finally {
  if ($null -ne $portForward -and -not $portForward.HasExited) {
    Stop-Process -Id $portForward.Id -Force -ErrorAction SilentlyContinue
  }
  Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
}

if ($Apply) {
  Wait-ApplicationHealthy -ExpectedRevision $expectedRevision
}
Write-Output "Phase 20 complete: topics=$($TopicContracts.Count) subjects=$($SubjectContracts.Count) partitions=$ExpectedPartitions replication-factor=$ExpectedReplicationFactor compatibility=$ExpectedCompatibility."
Write-Output "Secret values were not read or printed. Payment runtime flags remain disabled."
