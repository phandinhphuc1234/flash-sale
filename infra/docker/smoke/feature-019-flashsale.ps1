[CmdletBinding()]
param(
    [switch] $RunFailureMatrix,
    [switch] $SkipTopology,
    [switch] $PreserveFixtureUsers,
    [string] $FixtureOutputPath = '',
    [int] $Allocation = 20,
    [int] $CampaignLeadSeconds = 20,
    [int] $CampaignDurationMinutes = 10
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest

if ($Allocation -lt 8) {
    throw 'Allocation must be at least 8 so the smoke and failure scenarios have isolated quota.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$envFile = Join-Path $repoRoot 'infra\docker\.env'
$composeFile = Join-Path $repoRoot 'infra\docker\compose.yml'
$composeDevFile = Join-Path $repoRoot 'infra\docker\compose.dev.yml'
$topicBootstrapScript = Join-Path $repoRoot 'infra\docker\kafka\init-flashsale-topics.sh'
$schemaBootstrapScript = Join-Path $repoRoot 'infra\docker\schema-registry\register-flashsale-schemas.ps1'
$campaignSchemaBootstrapScript = Join-Path $repoRoot 'infra\docker\schema-registry\register-campaign-schemas.ps1'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'infra/docker/.env is required; copy the example and populate local-only secrets first.'
}

function Get-DotEnvValue([string] $Name, [string] $Fallback = '') {
    # Compose gives an explicitly supplied process environment value precedence over --env-file.
    # Supporting the same order lets CI/local secret injection avoid writing credentials to disk.
    $processValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }
    $line = Get-Content -LiteralPath $envFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $Fallback
    }
    return ($line -split '=', 2)[1]
}

function New-OAuthBasicHeader([string] $ClientId, [string] $ClientSecret) {
    # RFC 6749 requires URL-encoding client credentials before Base64 encoding.
    # This preserves '+' and '/' in generated local secrets.
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
$schemaRegistryBase = "http://127.0.0.1:$(Get-DotEnvValue 'SCHEMA_REGISTRY_HOST_PORT' '8081')"
$postgresUser = Get-DotEnvValue 'POSTGRES_USER' 'flashsale'
$redisPassword = Get-DotEnvValue 'REDIS_PASSWORD'
$requiredLocalEnvironment = @(
    'POSTGRES_PASSWORD',
    'REDIS_PASSWORD',
    'AUTH_THROTTLE_HMAC_SECRET',
    'AUTH_JWT_KEY_DIR',
    'CAMPAIGN_CLIENT_SECRET',
    'FLASHSALE_CLIENT_SECRET'
)
$missingLocalEnvironment = @($requiredLocalEnvironment | Where-Object {
        [string]::IsNullOrWhiteSpace((Get-DotEnvValue $_))
    })
if ($missingLocalEnvironment.Count -gt 0) {
    throw "infra/docker/.env is missing required local-only values: $($missingLocalEnvironment -join ', '). Add them locally; never commit this file."
}

function Invoke-Compose([string[]] $CommandArguments) {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile apps @CommandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Database([string] $Database, [string] $Sql) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        psql -X -q -v ON_ERROR_STOP=1 -U $postgresUser -d $Database -Atc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL command failed for $Database."
    }
    return @($output)
}

function Invoke-Redis([string[]] $Arguments) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T redis `
        redis-cli --no-auth-warning -a $redisPassword @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Redis command failed.'
    }
    return @($output)
}

function Wait-Until([scriptblock] $Condition, [string] $Description, [int] $Seconds = 90) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            if (& $Condition) {
                return
            }
        } catch {
            # Dependencies may be intentionally unavailable while a failure case is running.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description."
}

function Wait-Http([string] $Uri, [int] $Seconds = 90) {
    Wait-Until -Description $Uri -Seconds $Seconds -Condition {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 3 -SkipHttpErrorCheck
        # Cast explicitly: PowerShell can surface the response status as a deserialized
        # value when this helper is invoked from a nested script process.
        return ([int]$response.StatusCode -eq 200)
    }
}

function Wait-DatabaseValue(
        [string] $Database,
        [string] $Sql,
        [string] $Expected,
        [int] $Seconds = 120) {
    Wait-Until -Description "database value '$Expected'" -Seconds $Seconds -Condition {
        $value = [string](Invoke-Database $Database $Sql | Select-Object -First 1)
        return $value -eq $Expected
    }
}

function Wait-PostgresReady([int] $Seconds = 150) {
    Wait-Until -Description 'PostgreSQL accepting connections' -Seconds $Seconds -Condition {
        $value = [string](Invoke-Database 'flashsale_db' 'SELECT 1;' | Select-Object -First 1)
        return $value -eq '1'
    }
}

function Require-Status($Response, [int[]] $Expected, [string] $Step) {
    if ($Expected -notcontains [int] $Response.StatusCode) {
        throw "$Step returned HTTP $($Response.StatusCode): $($Response.Content)"
    }
}

function Require-Header($Response, [string] $Name, [string] $Step) {
    $value = [string] $Response.Headers[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Step did not include required $Name header."
    }
    return $value
}

function Get-ContainerEnvironmentValue([string] $Service, [string] $Name) {
    $container = & docker compose --env-file $envFile -f $composeFile ps -q $Service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($container)) {
        throw "$Service container is not running."
    }
    $entry = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $container |
        Where-Object { $_ -like "$Name=*" } | Select-Object -First 1
    if (-not $entry) {
        throw "$Name is not configured for $Service."
    }
    return $entry.Substring($Name.Length + 1)
}

function ConvertFrom-JwtPayload([string] $Token) {
    $parts = $Token.Split('.')
    if ($parts.Length -ne 3) {
        throw 'Access token is not a compact JWT.'
    }
    $value = $parts[1].Replace('-', '+').Replace('_', '/')
    switch ($value.Length % 4) {
        2 { $value += '==' }
        3 { $value += '=' }
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) | ConvertFrom-Json
}

function Invoke-TopicBootstrap {
    $kafkaContainer = & docker compose --env-file $envFile -f $composeFile ps -q kafka
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($kafkaContainer)) {
        throw 'Kafka container is not running for the controlled topic bootstrap.'
    }
    $containerScript = '/tmp/feature-019-init-flashsale-topics.sh'
    # Git on Windows may materialize tracked shell scripts as CRLF. Normalize a
    # temporary copy before handing it to Linux so bash does not see `\r` in
    # options such as `set -euo pipefail`.
    $normalizedScript = Join-Path ([IO.Path]::GetTempPath()) (
        'feature-019-init-flashsale-topics-' + [Guid]::NewGuid().ToString('N') + '.sh')
    try {
        $scriptText = [IO.File]::ReadAllText($topicBootstrapScript).Replace("`r`n", "`n").Replace("`r", "`n")
        [IO.File]::WriteAllText($normalizedScript, $scriptText, [Text.UTF8Encoding]::new($false))
        & docker cp $normalizedScript "${kafkaContainer}:$containerScript"
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not copy the approved Kafka topic bootstrap into the Kafka container.'
        }
    } finally {
        if (Test-Path -LiteralPath $normalizedScript) {
            Remove-Item -LiteralPath $normalizedScript -Force
        }
    }
    & docker exec $kafkaContainer bash $containerScript
    if ($LASTEXITCODE -ne 0) {
        throw 'Controlled Kafka topic bootstrap failed.'
    }
}

function Ensure-SchemaRegistryTopic {
    $kafkaContainer = & docker compose --env-file $envFile -f $composeFile ps -q kafka
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($kafkaContainer)) {
        throw 'Kafka container is not running for the Schema Registry topic bootstrap.'
    }
    & docker exec $kafkaContainer /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 `
        --create --if-not-exists --topic _schemas --partitions 1 --replication-factor 1 `
        --config cleanup.policy=compact
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not create or inspect the Schema Registry topic.'
    }
    & docker exec $kafkaContainer /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9092 `
        --entity-type topics --entity-name _schemas --alter --add-config cleanup.policy=compact
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not enforce compact retention on the Schema Registry topic.'
    }
}

function Initialize-Topology {
    Invoke-Compose @('up', '-d', '--build', 'postgres', 'redis', 'kafka')
    Wait-Until -Description 'PostgreSQL health' -Condition {
        return ((& docker compose --env-file $envFile -f $composeFile ps -q postgres) -ne $null)
    }
    Invoke-TopicBootstrap
    Ensure-SchemaRegistryTopic
    Invoke-Compose @('up', '-d', '--build', 'schema-registry')
    Wait-Http "$schemaRegistryBase/subjects"
    & $schemaBootstrapScript -SchemaRegistryUrl $schemaRegistryBase
    if ($LASTEXITCODE -ne 0) {
        throw 'Controlled Schema Registry bootstrap failed.'
    }
    & $campaignSchemaBootstrapScript -SchemaRegistryUrl $schemaRegistryBase
    if ($LASTEXITCODE -ne 0) {
        throw 'Controlled Campaign Schema Registry bootstrap failed.'
    }

    # Liquibase is intentionally a one-off Compose process. Normal replicas keep it disabled.
    Invoke-Compose @('up', '--build', '--abort-on-container-exit', '--exit-code-from',
        'flashsale-migration', 'flashsale-migration')
    Invoke-Compose @('up', '-d', '--build', 'authentication-service', 'api-gateway',
        'product-service', 'inventory-service', 'campaign-service', 'flashsale-service')

    Wait-Http "$gatewayBase/actuator/health"
    Wait-Http "$authBase/actuator/health/readiness"
    Wait-Http "$productBase/actuator/health/readiness"
    Wait-Http "$campaignBase/actuator/health/readiness"
    Wait-Http "$flashSaleBase/actuator/health/readiness"
    Wait-Http "$inventoryBase/actuator/health/readiness"
    Wait-Http "$schemaRegistryBase/subjects"
    Write-Output 'PASS topology gateway/auth/campaign/flashsale/postgres/redis/kafka/registry ready'
}

function Invoke-InventoryFixture([string] $Action, [Guid] $VariantId, [string] $Sku, [int] $Quantity) {
    $postgresPort = Get-DotEnvValue 'POSTGRES_HOST_PORT' '15432'
    $previous = @{
        Url = $env:SPRING_DATASOURCE_URL
        Username = $env:SPRING_DATASOURCE_USERNAME
        Password = $env:SPRING_DATASOURCE_PASSWORD
        Profiles = $env:SPRING_PROFILES_ACTIVE
    }
    try {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/inventory_db"
        $env:SPRING_DATASOURCE_USERNAME = $postgresUser
        $env:SPRING_DATASOURCE_PASSWORD = Get-DotEnvValue 'POSTGRES_PASSWORD'
        $env:SPRING_PROFILES_ACTIVE = 'test'
        $output = & (Join-Path $repoRoot 'mvnw.cmd') -q -pl services/inventory-service -am `
            '-Dtest=InventoryLocalSmokeFixture' '-Dsurefire.failIfNoSpecifiedTests=false' `
            '-Dinventory.local.fixture.enabled=true' "-Dinventory.local.fixture.action=$Action" `
            "-Dinventory.local.fixture.variant-id=$VariantId" "-Dinventory.local.fixture.sku=$Sku" `
            "-Dinventory.local.fixture.quantity=$Quantity" test 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Inventory local fixture failed: $($output | Select-Object -Last 20 | Out-String)"
        }
    } finally {
        $env:SPRING_DATASOURCE_URL = $previous.Url
        $env:SPRING_DATASOURCE_USERNAME = $previous.Username
        $env:SPRING_DATASOURCE_PASSWORD = $previous.Password
        $env:SPRING_PROFILES_ACTIVE = $previous.Profiles
    }
}

function New-Shopper([string] $Label) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $password = 'Sm0ke!Aa' + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
    $email = "feature019-$Label-$suffix@example.test"
    $trace = "feature019-$Label-$suffix"
    $register = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/register" -Method Post `
        -ContentType 'application/json' -Headers @{ 'X-Trace-Id' = $trace } `
        -Body (@{ email = $email; username = "feature019-$Label-$suffix"; password = $password } |
            ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $register @(201) "$Label registration"
    $userId = [Guid](($register.Content | ConvertFrom-Json).data.userId)
    $login = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/login" -Method Post `
        -ContentType 'application/json' -Headers @{ 'X-Trace-Id' = $trace } `
        -Body (@{ login = $email; password = $password; deviceName = 'feature-019-smoke' } |
            ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $login @(200) "$Label login"
    return [pscustomobject]@{
        Id = $userId
        Token = [string](($login.Content | ConvertFrom-Json).data.accessToken)
        Trace = $trace
    }
}

function New-AdminToken {
    # New-Shopper intentionally returns only the access data needed by shoppers. The admin flow keeps
    # registration credentials locally long enough to obtain a second token after role promotion.
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $email = "feature019-admin-$suffix@example.test"
    $password = 'Sm0ke!Aa' + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
    $trace = "feature019-admin-$suffix"
    $register = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/register" -Method Post -ContentType 'application/json' `
        -Headers @{ 'X-Trace-Id' = $trace } -Body (@{ email = $email; username = "feature019-admin-$suffix";
            password = $password } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $register @(201) 'admin registration'
    $adminId = [Guid](($register.Content | ConvertFrom-Json).data.userId)
    Invoke-Database 'auth_db' "UPDATE users SET role='ROLE_ADMIN', updated_at=NOW() WHERE id='$adminId'::uuid;" | Out-Null
    $login = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/login" -Method Post -ContentType 'application/json' `
        -Headers @{ 'X-Trace-Id' = $trace } -Body (@{ login = $email; password = $password;
            deviceName = 'feature-019-smoke-admin' } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $login @(200) 'admin login'
    $token = [string](($login.Content | ConvertFrom-Json).data.accessToken)
    $claims = ConvertFrom-JwtPayload $token
    if ($claims.aud -notcontains 'flash-sale-api' -or $claims.authorities -notcontains 'CAMPAIGN_ADMIN') {
        throw 'Admin token does not satisfy the approved Campaign administration trust contract.'
    }
    return [pscustomobject]@{ Id = $adminId; Token = $token; Trace = $trace }
}

function New-CampaignFixture($Admin) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $sku = ("F019-SMOKE-$suffix").ToUpperInvariant()
    $productHeaders = @{
        Authorization = "Bearer $($Admin.Token)"
        'X-Trace-Id' = "$($Admin.Trace)-product"
        'Idempotency-Key' = "feature019-product-$suffix"
    }
    $product = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/catalog/products" -Method Post `
        -ContentType 'application/json' -Headers $productHeaders -Body (@{
            code = "F019-$suffix"; slug = "feature019-$suffix"; name = 'Feature 019 smoke product'
            shortDescription = 'Local Flash Sale smoke fixture'; description = 'Disposable local validation fixture'
        } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $product @(201) 'product create'
    $productData = ($product.Content | ConvertFrom-Json).data
    $productId = [Guid]$productData.id
    $productVersion = [long]$productData.version

    $composition = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/catalog/products/$productId/composition" `
        -Method Put -ContentType 'application/json' -Headers @{
            Authorization = "Bearer $($Admin.Token)"; 'X-Trace-Id' = "$($Admin.Trace)-composition"
            'If-Match' = [string]$productVersion
        } -Body (@{
            name = 'Feature 019 smoke product'; shortDescription = 'Local Flash Sale smoke fixture'
            description = 'Disposable local validation fixture'; variants = @(@{
                id = $null; sku = $sku; barcode = $null; name = 'Smoke variant'; basePrice = 100000
                currency = 'VND'; status = 'ACTIVE'; sortOrder = 0
            }); categories = @(); media = @()
        } | ConvertTo-Json -Depth 6 -Compress) -SkipHeaderValidation -SkipHttpErrorCheck
    Require-Status $composition @(200) 'product composition'
    $productVersion = [long](($composition.Content | ConvertFrom-Json).data.version)
    $detail = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/catalog/products/$productId" -Method Get `
        -Headers @{ Authorization = "Bearer $($Admin.Token)"; 'X-Trace-Id' = "$($Admin.Trace)-detail" } -SkipHttpErrorCheck
    Require-Status $detail @(200) 'product detail'
    $variants = @(($detail.Content | ConvertFrom-Json).data.variants)
    if ($variants.Count -ne 1) { throw 'Smoke product must expose exactly one generated Variant.' }
    $variantId = [Guid]$variants[0].id
    $publish = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/catalog/products/$productId/publish" -Method Post `
        -Headers @{ Authorization = "Bearer $($Admin.Token)"; 'X-Trace-Id' = "$($Admin.Trace)-publish"
            'If-Match' = [string]$productVersion; 'Idempotency-Key' = "feature019-publish-$suffix" } -Body '{}' `
        -SkipHeaderValidation -SkipHttpErrorCheck
    Require-Status $publish @(200) 'product publish'
    Invoke-InventoryFixture 'prepare' $variantId $sku $Allocation

    $now = [DateTimeOffset]::UtcNow
    $create = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns" -Method Post -ContentType 'application/json' `
        -Headers @{ Authorization = "Bearer $($Admin.Token)"; 'X-Trace-Id' = "$($Admin.Trace)-campaign" } -Body (@{
            code = "F019-CAMPAIGN-$suffix"; name = 'Feature 019 Flash Sale smoke campaign'
            startAt = $now.AddSeconds($CampaignLeadSeconds).UtcDateTime.ToString('o')
            endAt = $now.AddMinutes($CampaignDurationMinutes).UtcDateTime.ToString('o')
        } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $create @(201) 'campaign create'
    $campaign = ($create.Content | ConvertFrom-Json).data
    $campaignId = [Guid]$campaign.id
    $version = [long]$campaign.version
    $item = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$campaignId/item" -Method Put `
        -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($Admin.Token)"
            'X-Trace-Id' = "$($Admin.Trace)-campaign-item"; 'If-Match' = '"' + $version + '"' } -Body (@{
                variantId = $variantId; campaignPrice = 90000; requestedQuantity = $Allocation
                purchaseLimitPerUser = 1
            } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
    Require-Status $item @(200) 'campaign item'
    $version = [long](($item.Content | ConvertFrom-Json).data.version)
    $schedule = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$campaignId/schedule" -Method Post `
        -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($Admin.Token)"
            'X-Trace-Id' = "$($Admin.Trace)-campaign-schedule"; 'If-Match' = '"' + $version + '"'
            'Idempotency-Key' = "feature019-schedule-$suffix" } -Body '{}' -SkipHttpErrorCheck
    Require-Status $schedule @(200) 'campaign schedule'

    Wait-DatabaseValue 'campaign_db' "SELECT status FROM campaigns WHERE id='$campaignId'::uuid;" 'ACTIVE' 120
    $metaKey = "fs:{hot}:campaign:$campaignId`:meta"
    Wait-Until -Description 'Flash Sale ACTIVE Campaign projection' -Seconds 120 -Condition {
        return ([string](Invoke-Redis @('HGET', $metaKey, 'state') | Select-Object -First 1)) -eq 'ACTIVE'
    }
    return [pscustomobject]@{ CampaignId = $campaignId; VariantId = $variantId; Sku = $sku; ProductId = $productId }
}

function Submit-Reservation($Shopper, $Fixture, [string] $IdempotencyKey) {
    return Invoke-WebRequest -Uri "$gatewayBase/api/v1/flash-sales/$($Fixture.CampaignId)/reservations" -Method Post `
        -ContentType 'application/json' -Headers @{ Authorization = "Bearer $($Shopper.Token)"
            'Idempotency-Key' = $IdempotencyKey; 'X-Trace-Id' = "$($Shopper.Trace)-reservation"
            traceparent = '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' } `
        -Body (@{ variantId = $Fixture.VariantId; quantity = 1 } | ConvertTo-Json -Compress) -SkipHttpErrorCheck
}

function Assert-DurableAcceptance($Response, $Shopper, $Fixture, [string] $ExpectedIdempotencyKey) {
    Require-Status $Response @(202) 'reservation submit'
    $location = Require-Header $Response 'Location' 'reservation submit'
    $traceId = Require-Header $Response 'X-Trace-Id' 'reservation submit'
    if ([string]$Response.Headers['Cache-Control'] -notmatch 'no-store') {
        throw 'Reservation submit must be no-store.'
    }
    $body = $Response.Content | ConvertFrom-Json
    $reservationId = [Guid]$body.data.reservationId
    $purchaseRequestId = [Guid]$body.data.purchaseRequestId
    if ($location -ne "/api/v1/flash-sales/reservations/$reservationId" -or [string]::IsNullOrWhiteSpace($traceId)) {
        throw 'Accepted reservation Location or trace identity is invalid.'
    }
    Wait-DatabaseValue 'flashsale_db' "SELECT COUNT(*) FROM purchase_requests WHERE id='$purchaseRequestId'::uuid;" '1'
    Wait-DatabaseValue 'flashsale_db' "SELECT COUNT(*) FROM flash_sale_reservations WHERE id='$reservationId'::uuid;" '1'
    Wait-DatabaseValue 'flashsale_db' "SELECT COUNT(*) FROM flash_sale_outbox_events WHERE aggregate_id='$purchaseRequestId'::uuid;" '1'

    $replay = Submit-Reservation $Shopper $Fixture $ExpectedIdempotencyKey
    Require-Status $replay @(202) 'identical reservation replay'
    $replayBody = $replay.Content | ConvertFrom-Json
    if ([Guid]$replayBody.data.reservationId -ne $reservationId -or
            [Guid]$replayBody.data.purchaseRequestId -ne $purchaseRequestId) {
        throw 'Identical replay did not return the original durable identities.'
    }
    return [pscustomobject]@{ ReservationId = $reservationId; PurchaseRequestId = $purchaseRequestId }
}

function Assert-OwnedQuery($Shopper, $ForeignShopper, [Guid] $ReservationId) {
    $owner = Invoke-WebRequest -Uri "$gatewayBase/api/v1/flash-sales/reservations/$ReservationId" -Method Get `
        -Headers @{ Authorization = "Bearer $($Shopper.Token)"; 'X-Trace-Id' = "$($Shopper.Trace)-owner-query" } `
        -SkipHttpErrorCheck
    Require-Status $owner @(200) 'owner reservation query'
    if ([Guid](($owner.Content | ConvertFrom-Json).data.reservationId) -ne $ReservationId) {
        throw 'Owner query returned a different reservation identity.'
    }
    $foreign = Invoke-WebRequest -Uri "$gatewayBase/api/v1/flash-sales/reservations/$ReservationId" -Method Get `
        -Headers @{ Authorization = "Bearer $($ForeignShopper.Token)"; 'X-Trace-Id' = "$($ForeignShopper.Trace)-foreign-query" } `
        -SkipHttpErrorCheck
    $unknown = Invoke-WebRequest -Uri "$gatewayBase/api/v1/flash-sales/reservations/$([Guid]::NewGuid())" -Method Get `
        -Headers @{ Authorization = "Bearer $($ForeignShopper.Token)"; 'X-Trace-Id' = "$($ForeignShopper.Trace)-unknown-query" } `
        -SkipHttpErrorCheck
    Require-Status $foreign @(404) 'foreign reservation query'
    Require-Status $unknown @(404) 'unknown reservation query'
    $foreignCode = [string](($foreign.Content | ConvertFrom-Json).errorCode)
    $unknownCode = [string](($unknown.Content | ConvertFrom-Json).errorCode)
    if ($foreignCode -ne $unknownCode -or $foreignCode -ne 'FLASH_SALE_RESERVATION_NOT_FOUND') {
        throw 'Foreign and unknown reservation queries must expose the same safe 404 code.'
    }
    Write-Output 'PASS owner-query owner=200 foreign/unknown=404 non-enumerating=true'
}

function Assert-KafkaPublication([Guid] $PurchaseRequestId) {
    $event = [string](Invoke-Database 'flashsale_db' "SELECT event_id FROM flash_sale_outbox_events WHERE aggregate_id='$PurchaseRequestId'::uuid;" |
        Select-Object -First 1)
    Wait-DatabaseValue 'flashsale_db' "SELECT status FROM flash_sale_outbox_events WHERE event_id='$event'::uuid;" 'PUBLISHED' 120
    $consumerOutput = & docker compose --env-file $envFile -f $composeFile exec -T kafka `
        /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 `
        --topic flashsale.purchase.events.v1 --from-beginning --timeout-ms 5000 `
        --property print.key=true --property print.value=false --property print.headers=true 2>&1
    $consumerText = $consumerOutput -join "`n"
    if ($consumerText -notmatch [regex]::Escape([string]$PurchaseRequestId) -or
            $consumerText -notmatch 'traceparent') {
        throw 'Kafka smoke did not observe the stable purchase-request key and W3C trace header.'
    }
    $subject = 'flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1'
    if ((Invoke-RestMethod -Uri "$schemaRegistryBase/subjects") -notcontains $subject) {
        throw 'The approved PurchaseAcceptedV1 Schema Registry subject is missing.'
    }
    Write-Output "PASS durable-outbox-kafka event-id=$event key=$PurchaseRequestId schema=PurchaseAcceptedV1"
}

function Invoke-RedisDownCase($Fixture) {
    $shopper = New-Shopper 'redis-down'
    Invoke-Compose @('stop', 'redis')
    try {
        $response = Submit-Reservation $shopper $Fixture ("feature019-redis-down-" + [Guid]::NewGuid().ToString('N'))
        Require-Status $response @(503) 'Redis-down reservation'
        $errorCode = [string](($response.Content | ConvertFrom-Json).errorCode)
        if (@('FLASH_SALE_REDIS_UNAVAILABLE', 'FLASH_SALE_PROJECTION_UNAVAILABLE') -notcontains $errorCode) {
            throw "Redis-down admission returned unexpected public error code: $errorCode"
        }
        Write-Output "PASS failure redis-down status=503 error=$errorCode durable-write=false"
    } finally {
        Invoke-Compose @('start', 'redis')
        Wait-Http "$flashSaleBase/actuator/health/readiness"
    }
}

function Invoke-PostgresPendingCase($Fixture, [string] $Label, [switch] $KillService) {
    $shopper = New-Shopper $Label
    $key = "feature019-$Label-" + [Guid]::NewGuid().ToString('N')
    Invoke-Compose @('stop', 'postgres')
    $response = Submit-Reservation $shopper $Fixture $key
    Require-Status $response @(503) "$Label reservation"
    if ([string](($response.Content | ConvertFrom-Json).errorCode) -ne 'FLASH_SALE_ACCEPTANCE_PENDING') {
        throw "$Label must return FLASH_SALE_ACCEPTANCE_PENDING after a Redis winner cannot reach PostgreSQL."
    }
    if ($KillService) {
        Invoke-Compose @('kill', 'flashsale-service')
    }
    Invoke-Compose @('start', 'postgres')
    Wait-PostgresReady
    if ($KillService) {
        Invoke-Compose @('start', 'flashsale-service')
    }
    Wait-Http "$flashSaleBase/actuator/health/readiness" 150
    Wait-PostgresReady
    Start-Sleep -Seconds 3
    $recovered = $null
    $lastRecoveryResponse = $null
    $recoveryDeadline = (Get-Date).AddSeconds(120)
    do {
        $lastRecoveryResponse = Submit-Reservation $shopper $Fixture $key
        if ([int]$lastRecoveryResponse.StatusCode -eq 202) {
            $recovered = $lastRecoveryResponse
            break
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $recoveryDeadline)
    if ($null -eq $recovered) {
        $recovered = $lastRecoveryResponse
    }
    Require-Status $recovered @(202) "$Label same-key recovery"
    Write-Output "PASS failure $Label status=503/202 stable-key-recovery=true"
}

function Invoke-ProcessAfterDatabaseCase($Fixture) {
    $shopper = New-Shopper 'process-after-db'
    $key = 'feature019-process-after-db-' + [Guid]::NewGuid().ToString('N')
    $response = Submit-Reservation $shopper $Fixture $key
    Require-Status $response @(202) 'process-after-db reservation'
    $accepted = $response.Content | ConvertFrom-Json
    $purchaseRequestId = [Guid]$accepted.data.purchaseRequestId
    Wait-DatabaseValue 'flashsale_db' "SELECT COUNT(*) FROM purchase_requests WHERE id='$purchaseRequestId'::uuid;" '1'
    # ACK is intentionally asynchronous. Killing immediately after observing the committed record
    # exercises recovery of the same Stream command rather than creating a replacement winner.
    Invoke-Compose @('kill', 'flashsale-service')
    Invoke-Compose @('start', 'flashsale-service')
    Wait-Http "$flashSaleBase/actuator/health/readiness" 150
    Wait-DatabaseValue 'flashsale_db' "SELECT COUNT(*) FROM flash_sale_outbox_events WHERE aggregate_id='$purchaseRequestId'::uuid;" '1'
    Write-Output 'PASS failure process-after-db-before-ack durable-identity-preserved=true'
}

function Invoke-OutboxOutageCase($Fixture, [string] $Dependency, [string] $Label, [switch] $RestartFlashSale) {
    $shopper = New-Shopper $Label
    if ($RestartFlashSale) {
        Invoke-Compose @('stop', 'schema-registry')
        Invoke-Compose @('restart', 'flashsale-service')
        Wait-Http "$flashSaleBase/actuator/health/readiness" 150
    } else {
        Invoke-Compose @('stop', $Dependency)
    }
    try {
        $response = Submit-Reservation $shopper $Fixture ("feature019-$Label-" + [Guid]::NewGuid().ToString('N'))
        Require-Status $response @(202) "$Label accepted reservation"
        $purchaseRequestId = [Guid](($response.Content | ConvertFrom-Json).data.purchaseRequestId)
        Wait-Until -Description "$Label pending outbox retry" -Seconds 120 -Condition {
            $row = [string](Invoke-Database 'flashsale_db' "SELECT status || '|' || attempt_count FROM flash_sale_outbox_events WHERE aggregate_id='$purchaseRequestId'::uuid;" |
                Select-Object -First 1)
            return $row -match '^(PENDING|PROCESSING)\|[1-9]'
        }
        $eventId = [string](Invoke-Database 'flashsale_db' "SELECT event_id FROM flash_sale_outbox_events WHERE aggregate_id='$purchaseRequestId'::uuid;" |
            Select-Object -First 1)
        if ($RestartFlashSale) {
            Invoke-Compose @('start', 'schema-registry')
            Wait-Http "$schemaRegistryBase/subjects" 120
        } else {
            Invoke-Compose @('start', $Dependency)
            if ($Dependency -eq 'kafka') { Wait-Http "$schemaRegistryBase/subjects" 120 }
        }
        Wait-DatabaseValue 'flashsale_db' "SELECT status FROM flash_sale_outbox_events WHERE event_id='$eventId'::uuid;" 'PUBLISHED' 150
        Write-Output "PASS failure $Label accepted=202 original-event-id=$eventId published-after-recovery=true"
    } finally {
        if ($Dependency -eq 'kafka') { try { Invoke-Compose @('start', 'kafka') } catch { } }
        if ($Dependency -eq 'schema-registry') { try { Invoke-Compose @('start', 'schema-registry') } catch { } }
    }
}

function Invoke-WrongAudienceCase([Guid] $ReservationId) {
    $secret = Get-ContainerEnvironmentValue 'authentication-service' 'FLASHSALE_CLIENT_SECRET'
    $basic = New-OAuthBasicHeader 'flashsale-service' $secret
    $tokenResponse = Invoke-WebRequest -Uri "$authBase/oauth2/token" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' -Headers @{ Authorization = "Basic $basic" } `
        -Body @{ grant_type = 'client_credentials'; scope = 'campaign.snapshot.read' } -SkipHttpErrorCheck
    Require-Status $tokenResponse @(200) 'wrong-audience machine token issuance'
    $response = Invoke-WebRequest -Uri "$gatewayBase/api/v1/flash-sales/reservations/$ReservationId" -Method Get `
        -Headers @{ Authorization = "Bearer $([string](($tokenResponse.Content | ConvertFrom-Json).access_token))" } `
        -SkipHttpErrorCheck
    Require-Status $response @(401) 'wrong-audience public reservation request'
    Write-Output 'PASS failure wrong-audience-jwt status=401 secret-details=false'
}

$script:adminId = $null
$script:shopperIds = [System.Collections.Generic.List[Guid]]::new()
$restoreServices = $true
try {
    if ($SkipTopology) {
        Write-Output 'SKIP topology bootstrap (using the already-running local stack)'
    } else {
        Initialize-Topology
    }
    $admin = New-AdminToken
    $script:adminId = $admin.Id
    $fixture = New-CampaignFixture $admin
    $owner = New-Shopper 'owner'
    $foreign = New-Shopper 'foreign'
    $script:shopperIds.Add($owner.Id)
    $script:shopperIds.Add($foreign.Id)
    $key = 'feature019-happy-' + [Guid]::NewGuid().ToString('N')
    $accepted = Assert-DurableAcceptance (Submit-Reservation $owner $fixture $key) $owner $fixture $key
    Assert-OwnedQuery $owner $foreign $accepted.ReservationId
    Assert-KafkaPublication $accepted.PurchaseRequestId
    Invoke-WrongAudienceCase $accepted.ReservationId
    if (-not [string]::IsNullOrWhiteSpace($FixtureOutputPath)) {
        [pscustomobject]@{
            adminId = [string]$admin.Id
            ownerId = [string]$owner.Id
            ownerToken = [string]$owner.Token
            foreignId = [string]$foreign.Id
            foreignToken = [string]$foreign.Token
            campaignId = [string]$fixture.CampaignId
            variantId = [string]$fixture.VariantId
            reservationId = [string]$accepted.ReservationId
            purchaseRequestId = [string]$accepted.PurchaseRequestId
        } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $FixtureOutputPath -Encoding UTF8
        Write-Output "FIXTURE_METADATA=$FixtureOutputPath"
    }
    Write-Output 'FEATURE_019_SMOKE=PASS'

    if ($RunFailureMatrix) {
        Invoke-RedisDownCase $fixture
        Invoke-PostgresPendingCase $fixture 'postgres-after-winner'
        Invoke-PostgresPendingCase $fixture 'process-after-lua' -KillService
        Invoke-ProcessAfterDatabaseCase $fixture
        Invoke-OutboxOutageCase $fixture 'kafka' 'kafka-down'
        Invoke-OutboxOutageCase $fixture 'schema-registry' 'registry-down' -RestartFlashSale
        Write-Output 'FEATURE_019_FAILURE_MATRIX=PASS'
    }
} finally {
    if ($restoreServices) {
        try { Invoke-Compose @('start', 'postgres', 'redis', 'kafka', 'schema-registry', 'flashsale-service') } catch { }
    }
    if (-not $PreserveFixtureUsers) {
        foreach ($shopperId in $script:shopperIds) {
            try { Invoke-Database 'auth_db' "DELETE FROM users WHERE id='$shopperId'::uuid;" | Out-Null } catch { }
        }
        if ($script:adminId) {
            try { Invoke-Database 'auth_db' "DELETE FROM users WHERE id='$script:adminId'::uuid;" | Out-Null } catch { }
        }
    }
}
