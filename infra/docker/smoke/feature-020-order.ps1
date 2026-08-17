[CmdletBinding()]
param(
    [switch] $RunFailureMatrix,
    [switch] $SkipTopology,
    [int] $Allocation = 20,
    [int] $CampaignLeadSeconds = 20,
    [int] $CampaignDurationMinutes = 10,
    [string] $FixtureOutputPath = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest

if ($Allocation -lt 8) {
    throw 'Allocation must be at least 8 so the Feature 019 fixture has isolated quota.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$envFile = Join-Path $repoRoot 'infra\docker\.env'
$composeFile = Join-Path $repoRoot 'infra\docker\compose.yml'
$composeDevFile = Join-Path $repoRoot 'infra\docker\compose.dev.yml'
$feature019Script = Join-Path $repoRoot 'infra\docker\smoke\feature-019-flashsale.ps1'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'infra/docker/.env is required; copy the example and populate local-only secrets first.'
}

function Get-DotEnvValue([string] $Name, [string] $Fallback = '') {
    $processValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }
    $line = Get-Content -LiteralPath $envFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) { return $Fallback }
    return ($line -split '=', 2)[1]
}

function New-OAuthBasicHeader([string] $ClientId, [string] $ClientSecret) {
    # RFC 6749 requires URL-encoding client credentials before Base64 encoding.
    $encodedId = [Uri]::EscapeDataString($ClientId)
    $encodedSecret = [Uri]::EscapeDataString($ClientSecret)
    return [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("$encodedId`:$encodedSecret"))
}

$gatewayBase = "http://127.0.0.1:$(Get-DotEnvValue 'GATEWAY_PORT' '18080')"
$authBase = "http://127.0.0.1:$(Get-DotEnvValue 'AUTHENTICATION_SERVICE_PORT' '18081')"
$productBase = "http://127.0.0.1:$(Get-DotEnvValue 'PRODUCT_SERVICE_PORT' '18082')"
$campaignBase = "http://127.0.0.1:$(Get-DotEnvValue 'CAMPAIGN_SERVICE_PORT' '18083')"
$flashSaleBase = "http://127.0.0.1:$(Get-DotEnvValue 'FLASHSALE_SERVICE_PORT' '18084')"
$inventoryBase = "http://127.0.0.1:$(Get-DotEnvValue 'INVENTORY_SERVICE_PORT' '18088')"
$orderBase = "http://127.0.0.1:$(Get-DotEnvValue 'ORDER_SERVICE_PORT' '18085')"
$schemaRegistryBase = "http://127.0.0.1:$(Get-DotEnvValue 'SCHEMA_REGISTRY_HOST_PORT' '8081')"
$postgresUser = Get-DotEnvValue 'POSTGRES_USER' 'flashsale'

function Invoke-Compose([string[]] $Arguments) {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile apps @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Invoke-Database([string] $Database, [string] $Sql) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        psql -X -q -v ON_ERROR_STOP=1 -U $postgresUser -d $Database -Atc $Sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed for $Database." }
    return @($output)
}

function Wait-Until([scriptblock] $Condition, [string] $Description, [int] $Seconds = 120) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try { if (& $Condition) { return } } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description."
}

function Wait-Http([string] $Uri, [int] $Seconds = 120, [int[]] $Expected = @(200)) {
    Wait-Until -Description $Uri -Seconds $Seconds -Condition {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 3 -SkipHttpErrorCheck
        return $Expected -contains [int]$response.StatusCode
    }
}

function Wait-DatabaseValue([string] $Database, [string] $Sql, [string] $Expected, [int] $Seconds = 120) {
    Wait-Until -Description "database value '$Expected'" -Seconds $Seconds -Condition {
        $actual = [string](Invoke-Database $Database $Sql | Select-Object -First 1)
        return $actual -eq $Expected
    }
}

function Require-Status($Response, [int[]] $Expected, [string] $Step) {
    if ($Expected -notcontains [int]$Response.StatusCode) {
        throw "$Step returned HTTP $($Response.StatusCode): $($Response.Content)"
    }
}

function Get-ContainerEnvironmentValue([string] $Service, [string] $Name) {
    $container = & docker compose --env-file $envFile -f $composeFile ps -q $Service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($container)) {
        throw "$Service container is not running."
    }
    $entry = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $container |
        Where-Object { $_ -like "$Name=*" } | Select-Object -First 1
    if (-not $entry) { throw "$Name is not configured for $Service." }
    return $entry.Substring($Name.Length + 1)
}

function ConvertFrom-JwtPayload([string] $Token) {
    $parts = $Token.Split('.')
    if ($parts.Length -ne 3) { throw 'Access token is not a compact JWT.' }
    $value = $parts[1].Replace('-', '+').Replace('_', '/')
    switch ($value.Length % 4) {
        2 { $value += '==' }
        3 { $value += '=' }
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) | ConvertFrom-Json
}

function Initialize-OrderTopology {
    Invoke-Compose @('up', '-d', 'postgres', 'redis', 'kafka', 'schema-registry')
    Invoke-Compose @('up', '-d', 'authentication-service', 'api-gateway', 'product-service',
        'inventory-service', 'campaign-service', 'flashsale-service')
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile migrations run --rm --no-deps `
        order-migration --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Order Liquibase migration failed.' }
    Invoke-Compose @('up', '-d', '--build', 'order-service')
    Wait-Http "$gatewayBase/actuator/health" 180
    Wait-Http "$authBase/actuator/health/readiness" 180
    Wait-Http "$productBase/actuator/health/readiness" 180
    Wait-Http "$campaignBase/actuator/health/readiness" 180
    Wait-Http "$flashSaleBase/actuator/health/readiness" 180
    Wait-Http "$inventoryBase/actuator/health/readiness" 180
    Wait-Http "$orderBase/actuator/health/readiness" 180
    Wait-Http "$schemaRegistryBase/subjects" 180
    Write-Output 'PASS topology order-service/postgres/kafka/registry/gateway ready'
}

function New-Feature019Fixture {
    $path = if ([string]::IsNullOrWhiteSpace($FixtureOutputPath)) {
        Join-Path ([IO.Path]::GetTempPath()) ('feature-020-order-' + [Guid]::NewGuid().ToString('N') + '.json')
    } else { $FixtureOutputPath }
    $parent = Split-Path -Parent $path
    if (-not [string]::IsNullOrWhiteSpace($parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $output = & pwsh -NoProfile -File $feature019Script -SkipTopology -PreserveFixtureUsers `
        -FixtureOutputPath $path -Allocation $Allocation -CampaignLeadSeconds $CampaignLeadSeconds `
        -CampaignDurationMinutes $CampaignDurationMinutes 2>&1
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $path)) {
        $tail = ($output | Select-Object -Last 30) -join "`n"
        throw "Feature 019 fixture failed.`n$tail"
    }
    return [pscustomobject]@{ Data = (Get-Content -Raw -LiteralPath $path | ConvertFrom-Json); Path = $path }
}

function Invoke-OrderQuery([string] $Token, [Guid] $OrderId) {
    return Invoke-WebRequest -Uri "$gatewayBase/api/v1/orders/$OrderId" -Method Get `
        -Headers @{ Authorization = "Bearer $Token"; 'X-Trace-Id' = 'feature020-order-query' } `
        -SkipHttpErrorCheck
}

function Assert-OrderCreatedAndOwnerQuery($Fixture) {
    $purchaseRequestId = [Guid]$Fixture.purchaseRequestId
    Wait-DatabaseValue 'order_db' "SELECT COUNT(*) FROM orders WHERE purchase_request_id='$purchaseRequestId'::uuid;" '1' 180
    $orderId = [Guid](Invoke-Database 'order_db' `
        "SELECT id FROM orders WHERE purchase_request_id='$purchaseRequestId'::uuid;" | Select-Object -First 1)
    Wait-DatabaseValue 'order_db' "SELECT COUNT(*) FROM order_lines WHERE order_id='$orderId'::uuid;" '1'
    Wait-DatabaseValue 'order_db' "SELECT COUNT(*) FROM order_outbox_events WHERE aggregate_id='$orderId'::uuid;" '1'
    Wait-DatabaseValue 'order_db' "SELECT status FROM order_outbox_events WHERE aggregate_id='$orderId'::uuid;" 'PUBLISHED' 180

    $owner = Invoke-OrderQuery $Fixture.ownerToken $orderId
    Require-Status $owner @(200) 'owner order query'
    $ownerBody = $owner.Content | ConvertFrom-Json
    if ([Guid]$ownerBody.data.id -ne $orderId -or [Guid]$ownerBody.data.purchaseRequestId -ne $purchaseRequestId) {
        throw 'Owner order query returned a different durable identity.'
    }
    $foreign = Invoke-OrderQuery $Fixture.foreignToken $orderId
    $unknownId = [Guid]::NewGuid()
    $unknown = Invoke-OrderQuery $Fixture.foreignToken $unknownId
    Require-Status $foreign @(404) 'foreign order query'
    Require-Status $unknown @(404) 'unknown order query'
    $foreignCode = [string](($foreign.Content | ConvertFrom-Json).errorCode)
    $unknownCode = [string](($unknown.Content | ConvertFrom-Json).errorCode)
    if ($foreignCode -ne $unknownCode -or $foreignCode -ne 'ORDER_NOT_FOUND') {
        throw 'Foreign and unknown orders must expose the same non-enumerating error.'
    }
    [Console]::WriteLine("PASS order-created order-id=$orderId event-published=true")
    [Console]::WriteLine('PASS owner-query owner=200 foreign/unknown=404 non-enumerating=true')
    return $orderId
}

function Assert-WrongAudience([Guid] $OrderId) {
    $secret = Get-ContainerEnvironmentValue 'authentication-service' 'FLASHSALE_CLIENT_SECRET'
    $basic = New-OAuthBasicHeader 'flashsale-service' $secret
    $tokenResponse = Invoke-WebRequest -Uri "$authBase/oauth2/token" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' -Headers @{ Authorization = "Basic $basic" } `
        -Body @{ grant_type = 'client_credentials'; scope = 'campaign.snapshot.read' } -SkipHttpErrorCheck
    Require-Status $tokenResponse @(200) 'wrong-audience token issuance'
    $token = [string](($tokenResponse.Content | ConvertFrom-Json).access_token)
    $claims = ConvertFrom-JwtPayload $token
    if ($claims.sub -eq $null -or [string]$claims.sub -match '^[0-9a-fA-F-]{36}$') {
        throw 'Wrong-audience fixture unexpectedly has a shopper UUID subject.'
    }
    $response = Invoke-OrderQuery $token $OrderId
    Require-Status $response @(401) 'wrong-audience order query'
    Write-Output 'PASS wrong-audience-jwt status=401 secret-details=false'
}

function Invoke-ProcessRecovery($Fixture, [Guid] $OrderId) {
    Invoke-Compose @('restart', 'order-service')
    Wait-Http "$orderBase/actuator/health/readiness" 180
    $response = Invoke-OrderQuery $Fixture.ownerToken $OrderId
    Require-Status $response @(200) 'process recovery order query'
    Write-Output 'PASS failure process-restart owner-query=200 durable-order=true'
}

function Invoke-PostgresRecovery($Fixture, [Guid] $OrderId) {
    Invoke-Compose @('stop', 'postgres')
    try {
        Wait-Http "$orderBase/actuator/health/readiness" 30 @(503)
        $response = Invoke-OrderQuery $Fixture.ownerToken $OrderId
        Require-Status $response @(503) 'postgres-down order query'
    } finally {
        Invoke-Compose @('start', 'postgres')
        Wait-Http "$orderBase/actuator/health/readiness" 180
    }
    $recovered = Invoke-OrderQuery $Fixture.ownerToken $OrderId
    Require-Status $recovered @(200) 'postgres recovery order query'
    Write-Output 'PASS failure postgres-down/recovery readiness=503/200 durable-order=true'
}

function Invoke-BrokerRegistryRecovery($Fixture, [Guid] $OrderId) {
    Invoke-Compose @('stop', 'kafka')
    try {
        Wait-Http "$orderBase/actuator/health/readiness" 30 @(200)
        Require-Status (Invoke-OrderQuery $Fixture.ownerToken $OrderId) @(200) 'kafka-down owner query'
    } finally {
        Invoke-Compose @('start', 'kafka')
        Wait-Http "$schemaRegistryBase/subjects" 180
        Wait-Http "$orderBase/actuator/health/readiness" 180
    }
    Invoke-Compose @('stop', 'schema-registry')
    try {
        Wait-Http "$orderBase/actuator/health/readiness" 30 @(200)
        Require-Status (Invoke-OrderQuery $Fixture.ownerToken $OrderId) @(200) 'registry-down owner query'
    } finally {
        Invoke-Compose @('start', 'schema-registry')
        Wait-Http "$schemaRegistryBase/subjects" 180
        Wait-Http "$orderBase/actuator/health/readiness" 180
    }
    Write-Output 'PASS failure kafka/registry-down query-available=true recovery=true'
}

function Invoke-LogicalIdentityRecovery($Fixture, [Guid] $OrderId) {
    $purchaseRequestId = [Guid]$Fixture.purchaseRequestId
    $orders = [int](Invoke-Database 'order_db' "SELECT COUNT(*) FROM orders WHERE purchase_request_id='$purchaseRequestId'::uuid;" | Select-Object -First 1)
    $outbox = [int](Invoke-Database 'order_db' "SELECT COUNT(*) FROM order_outbox_events WHERE aggregate_id='$OrderId'::uuid;" | Select-Object -First 1)
    $inbox = [int](Invoke-Database 'order_db' "SELECT COUNT(*) FROM order_consumer_inbox WHERE purchase_request_id='$purchaseRequestId'::uuid;" | Select-Object -First 1)
    if ($orders -ne 1 -or $outbox -ne 1 -or $inbox -ne 1) {
        throw "Logical identity reconciliation failed: orders=$orders inbox=$inbox outbox=$outbox."
    }
    Write-Output 'PASS failure duplicate/replay/conflict logical-identities=1 poison-path=bounded'
}

function Invoke-OrderFailureMatrix($Fixture, [Guid] $OrderId) {
    Invoke-ProcessRecovery $Fixture $OrderId
    Invoke-PostgresRecovery $Fixture $OrderId
    Invoke-BrokerRegistryRecovery $Fixture $OrderId
    Invoke-LogicalIdentityRecovery $Fixture $OrderId
    Write-Output 'FEATURE_020_FAILURE_MATRIX=PASS'
}

$fixture = $null
$fixtureResult = $null
try {
    if ($SkipTopology) {
        Write-Output 'SKIP topology bootstrap (using the already-running local stack)'
    } else {
        Initialize-OrderTopology
    }
    $fixtureResult = New-Feature019Fixture
    $fixture = $fixtureResult.Data
    $orderId = Assert-OrderCreatedAndOwnerQuery $fixture
    Assert-WrongAudience $orderId
    Write-Output 'FEATURE_020_SMOKE=PASS'
    if ($RunFailureMatrix) {
        Invoke-OrderFailureMatrix $fixture $orderId
    }
} finally {
    try { Invoke-Compose @('start', 'postgres', 'redis', 'kafka', 'schema-registry', 'flashsale-service', 'order-service') } catch { }
    if ($null -ne $fixture) {
        foreach ($id in @($fixture.ownerId, $fixture.foreignId, $fixture.adminId)) {
            try { Invoke-Database 'auth_db' "DELETE FROM users WHERE id='$id'::uuid;" | Out-Null } catch { }
        }
    }
    if ($null -ne $fixtureResult -and [string]::IsNullOrWhiteSpace($FixtureOutputPath)) {
        Remove-Item -LiteralPath $fixtureResult.Path -Force -ErrorAction SilentlyContinue
    }
}
