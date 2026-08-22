[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaDirectory,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
$schemas = @(
    [pscustomobject]@{
        Topic = 'flashsale.payment.commands.v1'
        SchemaTopic = 'flashsale.payment.commands.v1'
        File = 'PaymentRequestedV1.avsc'
        Record = 'com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.payment.payment-requested.dlt.v1'
        SchemaTopic = 'flashsale.payment.commands.v1'
        File = 'PaymentRequestedV1.avsc'
        Record = 'com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.payment.events.v1'
        SchemaTopic = 'flashsale.payment.events.v1'
        File = 'PaymentSucceededV1.avsc'
        Record = 'com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1'
    },
    [pscustomobject]@{
        Topic = 'flashsale.payment.events.v1'
        SchemaTopic = 'flashsale.payment.events.v1'
        File = 'PaymentFailedV1.avsc'
        Record = 'com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1'
    }
)

function Add-GeneratedStringProperties([object]$Node) {
    if ($Node -is [System.Collections.IList]) {
        for ($index = 0; $index -lt $Node.Count; $index++) {
            if ($Node[$index] -is [string] -and $Node[$index] -eq 'string') {
                $Node[$index] = [pscustomobject]@{ type = 'string'; 'avro.java.string' = 'String' }
            } else {
                Add-GeneratedStringProperties $Node[$index]
            }
        }
        return
    }
    if (-not ($Node -is [pscustomobject])) { return }
    foreach ($property in @($Node.PSObject.Properties)) {
        if ($property.Name -eq 'type' -and $property.Value -is [string] -and $property.Value -eq 'string' -and
                -not ($Node.PSObject.Properties.Name -contains 'logicalType')) {
            $property.Value = [pscustomobject]@{ type = 'string'; 'avro.java.string' = 'String' }
            continue
        }
        Add-GeneratedStringProperties $property.Value
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
    try {
        return Invoke-RestMethod @parameters
    } catch {
        throw "Schema Registry request '$Method $Path' failed. Check that Schema Registry is healthy and Payment compatibility is configured."
    }
}

foreach ($definition in $schemas) {
    $topicDirectory = Join-Path $SchemaDirectory ($definition.SchemaTopic -replace '/', '\')
    $schemaFile = (Resolve-Path -LiteralPath (Join-Path $topicDirectory $definition.File)).Path
    $schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100
    $recordName = "$($schema.namespace).$($schema.name)"
    if ($schema.type -ne 'record' -or $recordName -ne $definition.Record) {
        throw "Expected $($definition.Record) in $($definition.File), found $recordName."
    }

    Add-GeneratedStringProperties $schema
    $schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress
    $subject = "$($definition.Topic)-$recordName"
    $subjectPath = [Uri]::EscapeDataString($subject)
    # This payload is the canonical producer-facing schema derived from the accepted Git AVSC.
    # POST /subjects/{subject} is a read-only exact-schema lookup; it does not register a version.
    $schemaIdentityPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress

    function Assert-Subject {
        $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$subjectPath"
        if ($configuration.compatibilityLevel -ne $compatibility) {
            throw "Subject $subject must use $compatibility compatibility."
        }

        $exact = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath" `
            -Body $schemaIdentityPayload
        if ($exact.subject -ne $subject) {
            throw "Schema Registry returned an unexpected exact-schema subject for $subject."
        }

        $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
        if ($latest.subject -ne $subject) { throw "Schema Registry returned an unexpected subject for $subject." }
        if ([int]$exact.id -ne [int]$latest.id -or [int]$exact.version -ne [int]$latest.version) {
            throw "The exact Git schema for $subject exists at id $($exact.id), version $($exact.version), but is not latest."
        }

        $registeredSchema = $latest.schema | ConvertFrom-Json -Depth 100
        $registeredRecord = "$($registeredSchema.namespace).$($registeredSchema.name)"
        if ($registeredSchema.type -ne 'record' -or $registeredRecord -ne $definition.Record) {
            throw "Subject $subject has an unexpected latest record."
        }
        return $latest
    }

    $registration = $null
    if (-not $CheckOnly) {
        $compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
        Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" -Body $compatibilityPayload | Out-Null
        # Registration is idempotent: an already registered exact schema resolves to its existing
        # identity rather than creating another version.
        $registration = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" `
            -Body $schemaIdentityPayload
    }

    $latest = Assert-Subject
    if (-not $CheckOnly -and [int]$registration.id -ne [int]$latest.id) {
        throw "Schema Registry did not retain the registered exact Git schema identity for $subject."
    }
    $action = if ($CheckOnly) { 'Verified' } else { 'Registered and verified' }
    Write-Output "$action $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
}
