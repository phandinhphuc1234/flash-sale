[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.order.events.v1\OrderCreatedV1.avsc'),
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SchemaRegistryUrl)) {
    $SchemaRegistryUrl = if ($env:SCHEMA_REGISTRY_URL) { $env:SCHEMA_REGISTRY_URL } else { 'http://localhost:8081' }
}

$topic = 'flashsale.order.events.v1'
$compatibility = 'BACKWARD_TRANSITIVE'
$expectedRecordName = 'com.philia.flashsale.contract.order.event.v1.OrderCreatedV1'
$mediaType = 'application/vnd.schemaregistry.v1+json'
$registry = $SchemaRegistryUrl.TrimEnd('/')
$schemaFile = (Resolve-Path -LiteralPath $SchemaPath).Path
$schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100

if (($schema.type -ne 'record') -or [string]::IsNullOrWhiteSpace($schema.name) -or [string]::IsNullOrWhiteSpace($schema.namespace)) {
    throw 'OrderCreatedV1.avsc must define an Avro record with a namespace and name.'
}

$recordName = "$($schema.namespace).$($schema.name)"
if ($recordName -ne $expectedRecordName) {
    throw "Expected record $expectedRecordName but found $recordName."
}

# The Order producer uses TopicRecordNameStrategy, so the subject is topic + fully-qualified record.
$subjectStrategy = 'TopicRecordNameStrategy'

function Add-GeneratedStringProperties([object]$Node) {
    if ($Node -is [System.Collections.IList]) {
        foreach ($item in $Node) { Add-GeneratedStringProperties $item }
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

Add-GeneratedStringProperties $schema
$schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress
$subject = "$topic-$recordName"
$subjectPath = [Uri]::EscapeDataString($subject)
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }

function Invoke-SchemaRegistryRequest {
    param(
        [Parameter(Mandatory = $true)] [ValidateSet('Get', 'Post', 'Put')] [string]$Method,
        [Parameter(Mandatory = $true)] [string]$Path,
        [string]$Body
    )
    $parameters = @{ Method = $Method; Uri = "$registry$Path"; Headers = $headers; ErrorAction = 'Stop' }
    if ($null -ne $Body) { $parameters.Body = $Body }
    try { return Invoke-RestMethod @parameters }
    catch { throw "Schema Registry request '$Method $Path' failed. Check registry health and compatibility." }
}

function Assert-SubjectConfiguration {
    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$subjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $subject must use $compatibility compatibility."
    }
}

function Assert-LatestSchema {
    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
    if ($latest.subject -ne $subject) { throw "Schema Registry returned an unexpected subject for $expectedRecordName." }
    $registeredSchema = $latest.schema | ConvertFrom-Json -Depth 100
    $registeredRecordName = "$($registeredSchema.namespace).$($registeredSchema.name)"
    if ($registeredSchema.type -ne 'record' -or $registeredRecordName -ne $expectedRecordName) {
        throw "The latest schema for $subject is not $expectedRecordName."
    }
    return $latest
}

if ($CheckOnly) {
    Assert-SubjectConfiguration
    $latest = Assert-LatestSchema
    Write-Output "Verified $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
    exit 0
}

$compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" -Body $compatibilityPayload | Out-Null
$registrationPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress
$registration = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" -Body $registrationPayload
Assert-SubjectConfiguration
$latest = Assert-LatestSchema
if ($latest.id -ne $registration.id) { throw "Schema Registry did not retain the OrderCreatedV1 schema identity." }
Write-Output "Registered and verified $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
