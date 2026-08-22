[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaPath = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\flashsale.purchase.events.v1\PurchaseAcceptedV1.avsc'),
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SchemaRegistryUrl)) {
    $SchemaRegistryUrl = if ($env:SCHEMA_REGISTRY_URL) {
        $env:SCHEMA_REGISTRY_URL
    } else {
        'http://localhost:8081'
    }
}

$topic = 'flashsale.purchase.events.v1'
$compatibility = 'BACKWARD_TRANSITIVE'
$expectedRecordName = 'com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1'
$mediaType = 'application/vnd.schemaregistry.v1+json'
$registry = $SchemaRegistryUrl.TrimEnd('/')
$schemaFile = (Resolve-Path -LiteralPath $SchemaPath).Path
$schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100

if (($schema.type -ne 'record') -or [string]::IsNullOrWhiteSpace($schema.name) -or [string]::IsNullOrWhiteSpace($schema.namespace)) {
    throw 'PurchaseAcceptedV1.avsc must define an Avro record with a namespace and name.'
}

$recordName = "$($schema.namespace).$($schema.name)"
if ($recordName -ne $expectedRecordName) {
    throw "Expected record $expectedRecordName but found $recordName."
}

function Add-GeneratedStringProperties([object]$Node) {
    # The contract module generates SpecificRecord schemas with avro.java.string=String for
    # ordinary string fields. Register that generated form so auto.register.schemas=false
    # producers can resolve the exact schema identity.
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
    if (-not ($Node -is [pscustomobject])) {
        return
    }
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

# TopicRecordNameStrategy fixes the subject to topic + fully-qualified Avro record name.
$subject = "$topic-$recordName"
$subjectPath = [Uri]::EscapeDataString($subject)
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }

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
        throw "Schema Registry request '$Method $Path' failed. Check that the local Schema Registry is healthy and the configured compatibility allows this schema."
    }
}

function Assert-SubjectConfiguration {
    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$subjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $subject must use $compatibility compatibility."
    }
}

function Assert-SubjectState {
    param(
        [Parameter(Mandatory = $true)] [string]$LookupPayload
    )

    Assert-SubjectConfiguration

    # POST /subjects/{subject} is Schema Registry's read-only exact-schema lookup. Supplying the
    # transformed Git schema proves exact presence without registering a version in CheckOnly mode.
    $exact = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath" -Body $LookupPayload
    if ($exact.subject -ne $subject) {
        throw "Schema Registry returned an unexpected subject for $expectedRecordName."
    }

    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
    if ($latest.subject -ne $subject) {
        throw "Schema Registry returned an unexpected latest subject for $expectedRecordName."
    }

    $registeredSchema = $latest.schema | ConvertFrom-Json -Depth 100
    $registeredRecordName = "$($registeredSchema.namespace).$($registeredSchema.name)"
    if ($registeredSchema.type -ne 'record' -or $registeredRecordName -ne $expectedRecordName) {
        throw "The latest schema for $subject is not $expectedRecordName."
    }
    if ([int]$exact.id -ne [int]$latest.id -or [int]$exact.version -ne [int]$latest.version) {
        throw "The transformed Git schema exists for $subject but is not its latest version."
    }

    return $latest
}

$schemaPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress

if ($CheckOnly) {
    $latest = Assert-SubjectState -LookupPayload $schemaPayload
    Write-Output "Verified $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
    exit 0
}

$compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" -Body $compatibilityPayload | Out-Null

# Registration is idempotent: Schema Registry returns the existing ID when this exact schema is
# already present and rejects an incompatible evolution under the subject-level policy above.
$registration = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" -Body $schemaPayload

$latest = Assert-SubjectState -LookupPayload $schemaPayload
if ([int]$latest.id -ne [int]$registration.id) {
    throw "Schema Registry did not retain the registered PurchaseAcceptedV1 schema identity."
}

Write-Output "Registered and verified $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
