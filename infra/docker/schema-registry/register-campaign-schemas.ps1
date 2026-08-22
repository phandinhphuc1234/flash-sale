[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaDirectory = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\campaign.lifecycle.v1'),
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

$topic = 'campaign.lifecycle.v1'
$compatibility = 'BACKWARD_TRANSITIVE'
$registry = $SchemaRegistryUrl.TrimEnd('/')
$schemas = @(
    [pscustomobject]@{
        File = 'CampaignScheduledV1.avsc'
        Record = 'com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1'
    },
    [pscustomobject]@{
        File = 'CampaignActivatedV1.avsc'
        Record = 'com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1'
    }
)
$mediaType = 'application/vnd.schemaregistry.v1+json'
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }

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

function Assert-SubjectState {
    param(
        [Parameter(Mandatory = $true)] [string]$Subject,
        [Parameter(Mandatory = $true)] [string]$SubjectPath,
        [Parameter(Mandatory = $true)] [string]$ExpectedRecordName,
        [Parameter(Mandatory = $true)] [string]$LookupPayload
    )

    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$SubjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $Subject must use $compatibility compatibility."
    }

    # POST /subjects/{subject} performs a read-only lookup. Passing the transformed Git schema
    # proves that this exact schema already exists without creating a new version in CheckOnly mode.
    $exact = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$SubjectPath" -Body $LookupPayload
    if ($exact.subject -ne $Subject) {
        throw "Schema Registry returned an unexpected subject for $ExpectedRecordName."
    }

    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$SubjectPath/versions/latest"
    if ($latest.subject -ne $Subject) {
        throw "Schema Registry returned an unexpected latest subject for $ExpectedRecordName."
    }

    $latestSchema = $latest.schema | ConvertFrom-Json -Depth 100
    $latestRecordName = "$($latestSchema.namespace).$($latestSchema.name)"
    if ($latestSchema.type -ne 'record' -or $latestRecordName -ne $ExpectedRecordName) {
        throw "The latest schema for $Subject is not $ExpectedRecordName."
    }
    if ([int]$exact.id -ne [int]$latest.id -or [int]$exact.version -ne [int]$latest.version) {
        throw "The transformed Git schema exists for $Subject but is not its latest version."
    }

    return $latest
}

foreach ($definition in $schemas) {
    $schemaFileName = $definition.File
    $schemaFile = (Resolve-Path -LiteralPath (Join-Path $SchemaDirectory $schemaFileName)).Path
    $schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100
    if (($schema.type -ne 'record') -or [string]::IsNullOrWhiteSpace($schema.name) -or
            [string]::IsNullOrWhiteSpace($schema.namespace)) {
        throw "$schemaFileName must define an Avro record with a namespace and name."
    }

    $recordName = "$($schema.namespace).$($schema.name)"
    if ($recordName -ne $definition.Record) {
        throw "Expected $($definition.Record) in $schemaFileName but found $recordName."
    }

    Add-GeneratedStringProperties $schema
    $schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress
    $subject = "$topic-$recordName"
    $subjectPath = [Uri]::EscapeDataString($subject)
    $schemaPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress
    $registration = $null

    if (-not $CheckOnly) {
        $compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
        Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" -Body $compatibilityPayload | Out-Null

        # Registration is idempotent: Schema Registry returns the existing ID when this exact
        # transformed schema is already registered under the subject.
        $registration = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" `
            -Body $schemaPayload
    }

    $latest = Assert-SubjectState -Subject $subject -SubjectPath $subjectPath `
        -ExpectedRecordName $definition.Record -LookupPayload $schemaPayload
    if ($null -ne $registration -and [int]$registration.id -ne [int]$latest.id) {
        throw "Schema Registry did not retain the registered schema identity for $subject."
    }

    $action = if ($CheckOnly) { 'Verified' } else { 'Registered and verified' }
    Write-Output "$action $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
}
