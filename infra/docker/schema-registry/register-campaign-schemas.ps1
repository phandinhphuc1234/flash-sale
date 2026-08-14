[CmdletBinding()]
param(
    [string]$SchemaRegistryUrl,
    [string]$SchemaDirectory = (Join-Path $PSScriptRoot '..\..\..\contracts\kafka-avro-contracts\src\main\avro\topics\campaign.lifecycle.v1')
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
$schemaFiles = @('CampaignScheduledV1.avsc', 'CampaignActivatedV1.avsc')
$mediaType = 'application/vnd.schemaregistry.v1+json'
$headers = @{ Accept = $mediaType; 'Content-Type' = $mediaType }

function Add-GeneratedStringProperties([object]$Node) {
    # The contract module generates SpecificRecord schemas with avro.java.string=String for
    # ordinary string fields. Register that generated form so auto.register.schemas=false
    # producers can resolve the exact schema identity.
    if ($Node -is [System.Collections.IList]) {
        foreach ($item in $Node) {
            Add-GeneratedStringProperties $item
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

foreach ($schemaFileName in $schemaFiles) {
    $schemaFile = (Resolve-Path -LiteralPath (Join-Path $SchemaDirectory $schemaFileName)).Path
    $schema = (Get-Content -LiteralPath $schemaFile -Raw) | ConvertFrom-Json -Depth 100
    if (($schema.type -ne 'record') -or [string]::IsNullOrWhiteSpace($schema.name) -or
            [string]::IsNullOrWhiteSpace($schema.namespace)) {
        throw "$schemaFileName must define an Avro record with a namespace and name."
    }

    Add-GeneratedStringProperties $schema
    $schemaDocument = $schema | ConvertTo-Json -Depth 100 -Compress
    $recordName = "$($schema.namespace).$($schema.name)"
    $subject = "$topic-$recordName"
    $subjectPath = [Uri]::EscapeDataString($subject)
    $compatibilityPayload = @{ compatibility = $compatibility } | ConvertTo-Json -Compress
    Invoke-SchemaRegistryRequest -Method Put -Path "/config/$subjectPath" -Body $compatibilityPayload | Out-Null

    $registrationPayload = @{ schema = $schemaDocument; schemaType = 'AVRO' } | ConvertTo-Json -Compress
    $registration = Invoke-SchemaRegistryRequest -Method Post -Path "/subjects/$subjectPath/versions" `
        -Body $registrationPayload
    $configuration = Invoke-SchemaRegistryRequest -Method Get -Path "/config/$subjectPath"
    if ($configuration.compatibilityLevel -ne $compatibility) {
        throw "Subject $subject must use $compatibility compatibility."
    }
    $latest = Invoke-SchemaRegistryRequest -Method Get -Path "/subjects/$subjectPath/versions/latest"
    if ($latest.subject -ne $subject -or [int]$latest.id -ne [int]$registration.id) {
        throw "Schema Registry did not retain the registered schema identity for $subject."
    }

    Write-Output "Registered and verified $subject at schema id $($latest.id), version $($latest.version), compatibility $compatibility."
}
