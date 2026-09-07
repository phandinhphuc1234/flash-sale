[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaDirectory,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Register Feature 049's additive record-name subjects under TopicRecordNameStrategy. Every source
# SpecificRecord is registered before the matching consumer DLT binding, so a failed consumer can
# preserve the original typed payload without silently using a source-topic subject on its DLT.
if ([string]::IsNullOrWhiteSpace($SchemaRegistryUrl)) {
    $SchemaRegistryUrl = if ($env:SCHEMA_REGISTRY_URL) { $env:SCHEMA_REGISTRY_URL } else { 'http://localhost:8081' }
}
if ([string]::IsNullOrWhiteSpace($SchemaDirectory)) {
    $SchemaDirectory = Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics'
}

$registry = $SchemaRegistryUrl.TrimEnd('/')
$compatibility = 'BACKWARD_TRANSITIVE'
$mediaType = 'application/vnd.schemaregistry.v1+json'
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }

function New-SchemaDefinition {
    param(
        [Parameter(Mandatory = $true)] [string]$Topic,
        [Parameter(Mandatory = $true)] [string]$SchemaTopic,
        [Parameter(Mandatory = $true)] [string]$File,
        [Parameter(Mandatory = $true)] [string]$Record
    )

    [pscustomobject]@{
        Topic = $Topic
        SchemaTopic = $SchemaTopic
        File = $File
        Record = $Record
    }
}

$schemas = @(
    New-SchemaDefinition 'flashsale.inventory.regular-hold.commands.v1' 'flashsale.inventory.regular-hold.commands.v1' `
        'ConfirmRegularStockHoldV1.avsc' 'com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold.commands.v1' 'flashsale.inventory.regular-hold.commands.v1' `
        'ReleaseRegularStockHoldV1.avsc' 'com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold.events.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldConfirmedV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold.events.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldReleasedV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold.events.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldExpiredV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold-command.dlt.v1' 'flashsale.inventory.regular-hold.commands.v1' `
        'ConfirmRegularStockHoldV1.avsc' 'com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1'
    New-SchemaDefinition 'flashsale.inventory.regular-hold-command.dlt.v1' 'flashsale.inventory.regular-hold.commands.v1' `
        'ReleaseRegularStockHoldV1.avsc' 'com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1'
    New-SchemaDefinition 'flashsale.order.regular-hold-result.dlt.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldConfirmedV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1'
    New-SchemaDefinition 'flashsale.order.regular-hold-result.dlt.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldReleasedV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1'
    New-SchemaDefinition 'flashsale.order.regular-hold-result.dlt.v1' 'flashsale.inventory.regular-hold.events.v1' `
        'RegularStockHoldExpiredV1.avsc' 'com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredV1'
    New-SchemaDefinition 'flashsale.cart.checkout.commands.v1' 'flashsale.cart.checkout.commands.v1' `
        'ReconcilePurchasedCartSnapshotV1.avsc' 'com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1'
    New-SchemaDefinition 'flashsale.cart.checkout-reconciliation.dlt.v1' 'flashsale.cart.checkout.commands.v1' `
        'ReconcilePurchasedCartSnapshotV1.avsc' 'com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1'
    New-SchemaDefinition 'flashsale.order.events.v1' 'flashsale.order.events.v1' `
        'OrderCreatedV2.avsc' 'com.philia.flashsale.contract.order.event.v2.OrderCreatedV2'
    New-SchemaDefinition 'flashsale.order.events.v1' 'flashsale.order.events.v1' `
        'OrderConfirmedV2.avsc' 'com.philia.flashsale.contract.order.event.v2.OrderConfirmedV2'
    New-SchemaDefinition 'flashsale.order.events.v1' 'flashsale.order.events.v1' `
        'OrderCancelledV2.avsc' 'com.philia.flashsale.contract.order.event.v2.OrderCancelledV2'
    New-SchemaDefinition 'flashsale.order.events.v1' 'flashsale.order.events.v1' `
        'OrderExpiredV2.avsc' 'com.philia.flashsale.contract.order.event.v2.OrderExpiredV2'
)

function Add-GeneratedStringProperties {
    param([AllowNull()] [object]$Node)

    if ($Node -is [System.Collections.IList]) {
        for ($index = 0; $index -lt $Node.Count; $index++) {
            if ($Node[$index] -is [string] -and $Node[$index] -eq 'string') {
                $Node[$index] = [pscustomobject]@{ type = 'string'; 'avro.java.string' = 'String' }
            } else {
                Add-GeneratedStringProperties -Node $Node[$index]
            }
        }
        return
    }
    if (-not ($Node -is [pscustomobject])) { return }

    foreach ($property in @($Node.PSObject.Properties)) {
        if ($property.Name -eq 'type' -and $property.Value -is [string] -and
                $property.Value -eq 'string' -and
                -not ($Node.PSObject.Properties.Name -contains 'logicalType')) {
            $property.Value = [pscustomobject]@{ type = 'string'; 'avro.java.string' = 'String' }
            continue
        }
        Add-GeneratedStringProperties -Node $property.Value
    }
}

function Invoke-SchemaRegistryRequest {
    param(
        [Parameter(Mandatory = $true)] [ValidateSet('Get', 'Post', 'Put')] [string]$Method,
        [Parameter(Mandatory = $true)] [string]$Path,
        [string]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$registry$Path"
        Headers = $headers
        TimeoutSec = 10
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) { $parameters.Body = $Body }

    $lastError = $null
    foreach ($attempt in 1..3) {
        try {
            return Invoke-RestMethod @parameters
        } catch {
            $lastError = $_.Exception.Message
            if ($attempt -lt 3) {
                Start-Sleep -Milliseconds (500 * $attempt)
            }
        }
    }
    throw "Schema Registry request '$Method $Path' failed after 3 attempts: $lastError"
}

foreach ($definition in $schemas) {
    $schemaFile = (Resolve-Path -LiteralPath (Join-Path $SchemaDirectory `
        (Join-Path $definition.SchemaTopic $definition.File))).Path
    $schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100
    $recordName = "$($schema.namespace).$($schema.name)"
    if ($schema.type -ne 'record' -or $recordName -ne $definition.Record) {
        throw "Expected $($definition.Record) in $($definition.File), found $recordName."
    }

    Add-GeneratedStringProperties -Node $schema
    $schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress
    $subject = "$($definition.Topic)-$recordName"
    $subjectPath = [Uri]::EscapeDataString($subject)
    $payload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress

    if (-not $CheckOnly) {
        $compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
        Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" `
            -Body $compatibilityPayload | Out-Null
        Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions?normalize=true" `
            -Body $payload | Out-Null
    }

    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$subjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $subject must use $compatibility compatibility."
    }

    $exact = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/${subjectPath}?normalize=true" -Body $payload
    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
    if ($latest.subject -ne $subject -or [int]$exact.id -ne [int]$latest.id -or
            [int]$exact.version -ne [int]$latest.version) {
        throw "The exact Git schema for $subject is not the latest registered version."
    }

    $registeredSchema = $latest.schema | ConvertFrom-Json -Depth 100
    if ("$($registeredSchema.namespace).$($registeredSchema.name)" -ne $definition.Record) {
        throw "Subject $subject has an unexpected latest record."
    }

    $action = if ($CheckOnly) { 'Verified' } else { 'Registered and verified' }
    Write-Output "$action $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
}

Write-Output "Feature 049 Schema Registry gate passed for $($schemas.Count) topic/record subjects."
