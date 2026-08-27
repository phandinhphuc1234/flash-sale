[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.order.events.v1\OrderCreatedV1.avsc'),
    [string]$PurchaseAcceptedSchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.purchase.events.v1\PurchaseAcceptedV1.avsc'),
    [string]$PurchaseReservationConfirmedSchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.purchase.events.v1\PurchaseReservationConfirmedV1.avsc'),
    [string]$PurchaseReservationReleasedSchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.purchase.events.v1\PurchaseReservationReleasedV1.avsc'),
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SchemaRegistryUrl)) {
    $SchemaRegistryUrl = if ($env:SCHEMA_REGISTRY_URL) { $env:SCHEMA_REGISTRY_URL } else { 'http://localhost:8081' }
}

$compatibility = 'BACKWARD_TRANSITIVE'
$subjectStrategy = 'TopicRecordNameStrategy'
$mediaType = 'application/vnd.schemaregistry.v1+json'
$registry = $SchemaRegistryUrl.TrimEnd('/')
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }
$schemas = @(
    [pscustomobject]@{
        Topic = 'flashsale.order.events.v1'
        SchemaPath = $SchemaPath
        ExpectedRecord = 'com.philia.flashsale.contract.order.event.v1.OrderCreatedV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.order.purchase-accepted.dlt.v1'
        SchemaPath = $PurchaseAcceptedSchemaPath
        ExpectedRecord = 'com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.order.purchase-accepted.dlt.v1'
        SchemaPath = $PurchaseReservationConfirmedSchemaPath
        ExpectedRecord = 'com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.order.purchase-accepted.dlt.v1'
        SchemaPath = $PurchaseReservationReleasedSchemaPath
        ExpectedRecord = 'com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1'
    }
)

function Add-GeneratedStringProperties {
    param(
        [AllowNull()] [object]$Node
    )

    # Avro generates SpecificRecord schemas with avro.java.string=String for ordinary strings.
    # Logical strings such as UUID retain their logicalType and must not receive this property.
    if ($Node -is [System.Collections.IList]) {
        for ($index = 0; $index -lt $Node.Count; $index++) {
            if ($Node[$index] -is [string] -and $Node[$index] -eq 'string') {
                $Node[$index] = [pscustomobject]@{
                    type = 'string'
                    'avro.java.string' = 'String'
                }
            } else {
                Add-GeneratedStringProperties -Node $Node[$index]
            }
        }
        return
    }
    if (-not ($Node -is [pscustomobject])) {
        return
    }

    foreach ($property in @($Node.PSObject.Properties)) {
        if ($property.Name -eq 'type' -and $property.Value -is [string] -and
                $property.Value -eq 'string' -and
                -not ($Node.PSObject.Properties.Name -contains 'logicalType')) {
            $property.Value = [pscustomobject]@{
                type = 'string'
                'avro.java.string' = 'String'
            }
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
    if ($null -ne $Body) {
        $parameters.Body = $Body
    }

    try {
        return Invoke-RestMethod @parameters
    } catch {
        throw "Schema Registry request '$Method $Path' failed. Check registry health, subject existence, and compatibility."
    }
}

function Assert-SubjectConfiguration {
    param(
        [Parameter(Mandatory = $true)] [string]$Subject,
        [Parameter(Mandatory = $true)] [string]$SubjectPath
    )

    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$SubjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $Subject must use $compatibility compatibility."
    }
}

function Get-ExactSchemaIdentity {
    param(
        [Parameter(Mandatory = $true)] [string]$Subject,
        [Parameter(Mandatory = $true)] [string]$SubjectPath,
        [Parameter(Mandatory = $true)] [string]$SchemaDocument
    )

    # POST /subjects/{subject} is Schema Registry's non-mutating exact-schema lookup endpoint.
    # Normalization makes insignificant JSON formatting differences irrelevant.
    $lookupPayload = @{ schema = $SchemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress
    $identity = Invoke-SchemaRegistryRequest -Method Post `
        -Path "/subjects/${SubjectPath}?normalize=true" `
        -Body $lookupPayload
    if ($identity.subject -ne $Subject -or $null -eq $identity.id -or $null -eq $identity.version) {
        throw "Schema Registry did not resolve the exact Git schema identity for $Subject."
    }
    return $identity
}

function Assert-ExpectedSchemaIsLatest {
    param(
        [Parameter(Mandatory = $true)] [string]$Subject,
        [Parameter(Mandatory = $true)] [string]$SubjectPath,
        [Parameter(Mandatory = $true)] [string]$ExpectedRecord,
        [Parameter(Mandatory = $true)] [object]$ExactIdentity
    )

    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$SubjectPath/versions/latest"
    if ($latest.subject -ne $Subject) {
        throw "Schema Registry returned an unexpected latest subject for $ExpectedRecord."
    }

    $registeredSchema = $latest.schema | ConvertFrom-Json -Depth 100
    $registeredRecord = "$($registeredSchema.namespace).$($registeredSchema.name)"
    if ($registeredSchema.type -ne 'record' -or $registeredRecord -ne $ExpectedRecord) {
        throw "The latest schema for $Subject is not $ExpectedRecord."
    }
    if ([int]$latest.id -ne [int]$ExactIdentity.id -or
            [int]$latest.version -ne [int]$ExactIdentity.version) {
        throw "The exact Git schema for $Subject exists but is not the latest subject version."
    }

    return $latest
}

foreach ($definition in $schemas) {
    $schemaFile = (Resolve-Path -LiteralPath $definition.SchemaPath).Path
    $schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100
    if (($schema.type -ne 'record') -or [string]::IsNullOrWhiteSpace($schema.name) -or
            [string]::IsNullOrWhiteSpace($schema.namespace)) {
        throw "$schemaFile must define an Avro record with a namespace and name."
    }

    $recordName = "$($schema.namespace).$($schema.name)"
    if ($recordName -ne $definition.ExpectedRecord) {
        throw "Expected $($definition.ExpectedRecord) in $schemaFile but found $recordName."
    }

    Add-GeneratedStringProperties -Node $schema
    $schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress

    # Order producers use TopicRecordNameStrategy: topic + fully-qualified record name.
    # The purchase-events topic is shared by three record types. A poison record rejected by
    # the PurchaseAccepted consumer is still published to its DLT with its original SpecificRecord,
    # so the Order DLT must register every record type that can arrive from the shared source topic.
    $subject = "$($definition.Topic)-$recordName"
    $subjectPath = [Uri]::EscapeDataString($subject)
    $registrationPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress

    if (-not $CheckOnly) {
        $compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
        Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" `
            -Body $compatibilityPayload | Out-Null
        Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" `
            -Body $registrationPayload | Out-Null
    }

    Assert-SubjectConfiguration -Subject $subject -SubjectPath $subjectPath
    $exactIdentity = Get-ExactSchemaIdentity -Subject $subject -SubjectPath $subjectPath `
        -SchemaDocument $schemaDocument
    $latest = Assert-ExpectedSchemaIsLatest -Subject $subject -SubjectPath $subjectPath `
        -ExpectedRecord $definition.ExpectedRecord -ExactIdentity $exactIdentity

    $action = if ($CheckOnly) { 'Verified' } else { 'Registered and verified' }
    Write-Output "$action $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility, strategy $subjectStrategy."
}
