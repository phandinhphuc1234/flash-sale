[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$envFile = Join-Path $repoRoot "infra\docker\.env"
$composeFile = Join-Path $repoRoot "infra\docker\compose.yml"
$composeDevFile = Join-Path $repoRoot "infra\docker\compose.dev.yml"
$gatewayBase = "http://127.0.0.1:18080"
$authBase = "http://127.0.0.1:18081"
$campaignBase = "http://127.0.0.1:18083"

if (-not (Test-Path $envFile)) {
    throw "infra/docker/.env is required"
}

function Get-DotEnvValue([string] $name, [string] $fallback = "") {
    $line = Get-Content $envFile | Where-Object { $_ -match "^$([regex]::Escape($name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $fallback
    }
    return ($line -split "=", 2)[1]
}

$postgresUser = Get-DotEnvValue "POSTGRES_USER" "flashsale"
$postgresPassword = Get-DotEnvValue "POSTGRES_PASSWORD"
$postgresPort = Get-DotEnvValue "POSTGRES_HOST_PORT" "15432"
$originalJavaToolOptions = Get-DotEnvValue "JAVA_TOOL_OPTIONS" "-XX:MaxRAMPercentage=75.0"

function Invoke-Compose([string[]] $commandArguments) {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile apps @commandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Database([string] $database, [string] $sql) {
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        psql -X -q -v ON_ERROR_STOP=1 -U $postgresUser -d $database -Atc $sql
    if ($LASTEXITCODE -ne 0) {
        throw "Database command failed for $database"
    }
    return @($output)
}

function Wait-Http([string] $uri, [int] $seconds = 60) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 3 -SkipHttpErrorCheck
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            # The container may still be starting.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $uri"
}

function Wait-DatabaseValue(
        [string] $database,
        [string] $sql,
        [string] $expected,
        [int] $seconds = 90) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        $value = [string](Invoke-Database $database $sql | Select-Object -First 1)
        if ($value -eq $expected) {
            return $value
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for database state $expected; last state was $value"
}

function Require-Status($response, [int[]] $expected, [string] $step) {
    if ($expected -notcontains [int]$response.StatusCode) {
        throw "$step returned HTTP $($response.StatusCode): $($response.Content)"
    }
}

function Get-ContainerEnvironmentValue([string] $service, [string] $name) {
    $container = & docker compose --env-file $envFile -f $composeFile ps -q $service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($container)) {
        throw "$service container is not running"
    }
    $entry = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $container |
        Where-Object { $_ -like "$name=*" } | Select-Object -First 1
    if (-not $entry) {
        throw "$name is not configured for $service"
    }
    $value = $entry.Substring($name.Length + 1)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$name is blank for $service"
    }
    return $value
}

function ConvertFrom-JwtPayload([string] $token) {
    $parts = $token.Split('.')
    if ($parts.Length -ne 3) {
        throw "Access token is not a compact JWT"
    }
    $value = $parts[1].Replace('-', '+').Replace('_', '/')
    switch ($value.Length % 4) {
        2 { $value += '==' }
        3 { $value += '=' }
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) | ConvertFrom-Json
}

function Invoke-InventoryFixture([string] $action, [Guid] $variantId, [string] $sku) {
    $previous = @{
        Url = $env:SPRING_DATASOURCE_URL
        Username = $env:SPRING_DATASOURCE_USERNAME
        Password = $env:SPRING_DATASOURCE_PASSWORD
        Profiles = $env:SPRING_PROFILES_ACTIVE
    }
    try {
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/inventory_db"
        $env:SPRING_DATASOURCE_USERNAME = $postgresUser
        $env:SPRING_DATASOURCE_PASSWORD = $postgresPassword
        $env:SPRING_PROFILES_ACTIVE = "test"
        $fixtureOutput = & (Join-Path $repoRoot "mvnw.cmd") -q `
            -pl services/inventory-service -am `
            "-Dtest=InventoryLocalSmokeFixture" `
            "-Dsurefire.failIfNoSpecifiedTests=false" `
            "-Dinventory.local.fixture.enabled=true" `
            "-Dinventory.local.fixture.action=$action" `
            "-Dinventory.local.fixture.variant-id=$variantId" `
            "-Dinventory.local.fixture.sku=$sku" `
            "-Dinventory.local.fixture.quantity=100" test 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Inventory fixture failed: $($fixtureOutput | Select-Object -Last 20 | Out-String)"
        }
    } finally {
        $env:SPRING_DATASOURCE_URL = $previous.Url
        $env:SPRING_DATASOURCE_USERNAME = $previous.Username
        $env:SPRING_DATASOURCE_PASSWORD = $previous.Password
        $env:SPRING_PROFILES_ACTIVE = $previous.Profiles
    }
}

$script:adminToken = $null
$script:tracePrefix = "feature017-" + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$script:variantId = $null
$script:sku = ("F017-SMOKE-" + [Guid]::NewGuid().ToString('N').Substring(0, 12)).ToUpperInvariant()
$script:productId = $null
$script:productVersion = $null
$script:userId = $null
$script:campaignRuntimeModified = $false
$script:campaignClientSecret = Get-ContainerEnvironmentValue "authentication-service" "CAMPAIGN_CLIENT_SECRET"
$script:flashsaleClientSecret = Get-ContainerEnvironmentValue "authentication-service" "FLASHSALE_CLIENT_SECRET"
$env:CAMPAIGN_CLIENT_SECRET = $script:campaignClientSecret
$env:FLASHSALE_CLIENT_SECRET = $script:flashsaleClientSecret

function New-CampaignFixture([string] $label, [DateTimeOffset] $startAt, [DateTimeOffset] $endAt) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $headers = @{
        Authorization = "Bearer $script:adminToken"
        "X-Trace-Id" = "$script:tracePrefix-$label-create"
    }
    $body = @{
        code = "F017-$label-$suffix"
        name = "Feature 017 $label smoke"
        startAt = $startAt.UtcDateTime.ToString("o")
        endAt = $endAt.UtcDateTime.ToString("o")
    } | ConvertTo-Json -Compress
    $created = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns" -Method Post `
        -ContentType "application/json" -Headers $headers -Body $body -SkipHttpErrorCheck
    Require-Status $created @(201) "$label campaign create"
    $data = ($created.Content | ConvertFrom-Json).data
    $campaignId = [Guid]$data.id
    $version = [long]$data.version

    $itemHeaders = @{
        Authorization = "Bearer $script:adminToken"
        "X-Trace-Id" = "$script:tracePrefix-$label-item"
        "If-Match" = '"' + $version + '"'
    }
    $itemBody = @{
        variantId = $script:variantId
        campaignPrice = 90000
        requestedQuantity = 5
        purchaseLimitPerUser = 1
    } | ConvertTo-Json -Compress
    $item = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$campaignId/item" `
        -Method Put -ContentType "application/json" -Headers $itemHeaders -Body $itemBody `
        -SkipHttpErrorCheck
    Require-Status $item @(200) "$label campaign item"
    $itemData = ($item.Content | ConvertFrom-Json).data
    return [pscustomobject]@{ Id = $campaignId; Version = [long]$itemData.version; Label = $label }
}

function Invoke-Schedule($campaign, [string] $key) {
    $headers = @{
        Authorization = "Bearer $script:adminToken"
        "X-Trace-Id" = "$script:tracePrefix-$($campaign.Label)-schedule"
        "If-Match" = '"' + $campaign.Version + '"'
        "Idempotency-Key" = $key
    }
    return Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$($campaign.Id)/schedule" `
        -Method Post -ContentType "application/json" -Headers $headers -Body '{}' -SkipHttpErrorCheck
}

function Invoke-ScheduleAfterRestart($campaign, [string] $key) {
    $lastResponse = $null
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        $lastResponse = Invoke-Schedule $campaign $key
        if ([int]$lastResponse.StatusCode -ne 503) {
            return $lastResponse
        }
        try {
            $errorCode = [string](($lastResponse.Content | ConvertFrom-Json).errorCode)
        } catch {
            return $lastResponse
        }
        if ($errorCode -ne "DOWNSTREAM_UNAVAILABLE") {
            return $lastResponse
        }
        Start-Sleep -Seconds 5
    }
    return $lastResponse
}

function Get-CampaignDetail([Guid] $campaignId) {
    return Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$campaignId" -Method Get `
        -Headers @{ Authorization = "Bearer $script:adminToken"; "X-Trace-Id" = "$script:tracePrefix-detail" } `
        -SkipHttpErrorCheck
}

$restoreServices = $true
try {
    Wait-Http "$gatewayBase/actuator/health" 90
    Wait-Http "$authBase/actuator/health/readiness" 90
    Wait-Http "http://127.0.0.1:18082/actuator/health/readiness" 90
    Wait-Http "$campaignBase/actuator/health/readiness" 90
    Wait-Http "http://127.0.0.1:18088/actuator/health/readiness" 90
    Wait-Http "http://127.0.0.1:8081/subjects" 90
    Write-Output "PASS topology-health gateway/auth/product/campaign/inventory/registry=200"

    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $email = "feature017-$suffix@example.test"
    $username = "feature017-$suffix"
    $password = "Sm0ke!Aa" + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
    $authTrace = "$script:tracePrefix-auth"
    $registerBody = @{ email = $email; username = $username; password = $password } |
        ConvertTo-Json -Compress
    $registered = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/register" -Method Post `
        -ContentType "application/json" -Headers @{ "X-Trace-Id" = $authTrace } `
        -Body $registerBody -SkipHttpErrorCheck
    Require-Status $registered @(201) "register disposable admin"
    $script:userId = [Guid](($registered.Content | ConvertFrom-Json).data.userId)
    Invoke-Database "auth_db" `
        "UPDATE users SET role='ROLE_ADMIN', updated_at=NOW() WHERE id='$script:userId'::uuid;" | Out-Null

    $loginBody = @{ login = $email; password = $password; deviceName = "feature-017-smoke" } |
        ConvertTo-Json -Compress
    $login = Invoke-WebRequest -Uri "$gatewayBase/api/v1/auth/login" -Method Post `
        -ContentType "application/json" -Headers @{ "X-Trace-Id" = $authTrace } `
        -Body $loginBody -SkipHttpErrorCheck
    Require-Status $login @(200) "login disposable admin"
    $script:adminToken = [string](($login.Content | ConvertFrom-Json).data.accessToken)
    $adminClaims = ConvertFrom-JwtPayload $script:adminToken
    if ($adminClaims.iss -ne "http://authentication-service:8080" -or
            $adminClaims.aud -notcontains "flash-sale-api" -or
            $adminClaims.authorities -notcontains "CAMPAIGN_ADMIN") {
        throw "Administrator JWT trust contract is not compatible with Campaign administration"
    }
    Write-Output "PASS auth-register-login status=201/200 authority=CAMPAIGN_ADMIN"

    $productSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $productTrace = "$script:tracePrefix-product"
    $productHeaders = @{
        Authorization = "Bearer $script:adminToken"
        "X-Trace-Id" = $productTrace
        "Idempotency-Key" = "product-create-$productSuffix"
    }
    $productBody = @{
        code = "F017-$productSuffix"
        slug = "feature017-$productSuffix"
        name = "Feature 017 Smoke Product"
        shortDescription = "Local smoke fixture"
        description = "Feature 017 end-to-end validation"
    } | ConvertTo-Json -Compress
    $productCreated = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/catalog/products" `
        -Method Post -ContentType "application/json" -Headers $productHeaders `
        -Body $productBody -SkipHttpErrorCheck
    Require-Status $productCreated @(201) "Product create"
    $productData = ($productCreated.Content | ConvertFrom-Json).data
    $script:productId = [Guid]$productData.id
    $script:productVersion = [long]$productData.version

    $composition = @{
        name = "Feature 017 Smoke Product"
        shortDescription = "Local smoke fixture"
        description = "Feature 017 end-to-end validation"
        variants = @(@{
            id = $null
            sku = $script:sku
            barcode = $null
            name = "Smoke Variant"
            basePrice = 100000
            currency = "VND"
            status = "ACTIVE"
            sortOrder = 0
        })
        categories = @()
        media = @()
    } | ConvertTo-Json -Depth 6 -Compress
    $compositionResponse = Invoke-WebRequest `
        -Uri "$gatewayBase/api/v1/admin/catalog/products/$script:productId/composition" `
        -Method Put -ContentType "application/json" -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = $productTrace
            "If-Match" = [string]$script:productVersion
        } -Body $composition -SkipHeaderValidation -SkipHttpErrorCheck
    Require-Status $compositionResponse @(200) "Product composition"
    $script:productVersion = [long](($compositionResponse.Content | ConvertFrom-Json).data.version)

    $productDetail = Invoke-WebRequest `
        -Uri "$gatewayBase/api/v1/admin/catalog/products/$script:productId" `
        -Method Get -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = $productTrace
        } -SkipHttpErrorCheck
    Require-Status $productDetail @(200) "Product detail after composition"
    $variantData = @(($productDetail.Content | ConvertFrom-Json).data.variants)
    if ($variantData.Count -ne 1) {
        throw "Product fixture must contain exactly one generated Variant"
    }
    $script:variantId = [Guid]$variantData[0].id

    $productPublished = Invoke-WebRequest `
        -Uri "$gatewayBase/api/v1/admin/catalog/products/$script:productId/publish" `
        -Method Post -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = $productTrace
            "If-Match" = [string]$script:productVersion
            "Idempotency-Key" = "product-publish-$productSuffix"
        } -SkipHeaderValidation -SkipHttpErrorCheck
    Require-Status $productPublished @(200) "Product publish"
    $publishedData = ($productPublished.Content | ConvertFrom-Json).data
    $script:productVersion = [long]$publishedData.version
    if ($publishedData.status -ne "ACTIVE") {
        throw "Product fixture is not ACTIVE"
    }
    Write-Output "PASS product-fixture create/composition/publish=201/200/200"

    Invoke-InventoryFixture "prepare" $script:variantId $script:sku
    $inventory = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/inventory/$script:variantId" `
        -Headers @{ Authorization = "Bearer $script:adminToken"; "X-Trace-Id" = "$script:tracePrefix-inventory" } `
        -SkipHttpErrorCheck
    Require-Status $inventory @(200) "Inventory fixture read"
    if ([long](($inventory.Content | ConvertFrom-Json).data.availableQuantity) -ne 100) {
        throw "Inventory fixture quantity is not 100"
    }
    Write-Output "PASS inventory-fixture application-usecase available=100"

    $now = [DateTimeOffset]::UtcNow
    $happy = New-CampaignFixture "HAPPY" $now.AddSeconds(30) $now.AddSeconds(65)
    $happyKey = "schedule-$($happy.Id)-v$($happy.Version)"
    $scheduled = Invoke-Schedule $happy $happyKey
    Require-Status $scheduled @(200) "Happy schedule"
    $scheduledData = ($scheduled.Content | ConvertFrom-Json).data
    if ($scheduledData.status -ne "SCHEDULED" -or
            [long]$scheduledData.item.allocatedQuantity -ne 5) {
        throw "Happy schedule did not freeze a complete allocation"
    }
    $scheduledVersion = [long]$scheduledData.version

    # Manual activation is invalid before the scheduled start time, even after preparation succeeds.
    $early = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$($happy.Id)/activate" `
        -Method Post -ContentType "application/json" -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = "$script:tracePrefix-early-activate"
            "If-Match" = '"' + $scheduledVersion + '"'
        } -Body '{}' -SkipHttpErrorCheck
    Require-Status $early @(409) "Early manual activation"

    $replayed = Invoke-Schedule $happy $happyKey
    Require-Status $replayed @(200) "Schedule replay"
    if ([long](($replayed.Content | ConvertFrom-Json).data.version) -ne $scheduledVersion) {
        throw "Schedule replay changed the Campaign version"
    }
    $conflictHeaders = @{
        Authorization = "Bearer $script:adminToken"
        "X-Trace-Id" = "$script:tracePrefix-idempotency-conflict"
        "If-Match" = '"' + ($happy.Version + 1) + '"'
        "Idempotency-Key" = $happyKey
    }
    $conflict = Invoke-WebRequest -Uri "$gatewayBase/api/v1/admin/campaigns/$($happy.Id)/schedule" `
        -Method Post -ContentType "application/json" -Headers $conflictHeaders -Body '{}' `
        -SkipHttpErrorCheck
    Require-Status $conflict @(409) "Schedule idempotency conflict"

    $basic = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("flashsale-service:$script:flashsaleClientSecret"))
    $serviceTokenResponse = Invoke-WebRequest -Uri "$authBase/oauth2/token" -Method Post `
        -ContentType "application/x-www-form-urlencoded" `
        -Headers @{ Authorization = "Basic $basic" } `
        -Body @{ grant_type = "client_credentials"; scope = "campaign.snapshot.read" } `
        -SkipHttpErrorCheck
    Require-Status $serviceTokenResponse @(200) "Flash Sale Client Credentials"
    $serviceTokenBody = $serviceTokenResponse.Content | ConvertFrom-Json
    if ($serviceTokenBody.PSObject.Properties.Name -contains "refresh_token") {
        throw "Client Credentials response unexpectedly returned a refresh token"
    }
    $snapshot = Invoke-WebRequest -Uri "$campaignBase/internal/v1/campaigns/$($happy.Id)/snapshot" `
        -Headers @{
            Authorization = "Bearer $($serviceTokenBody.access_token)"
            "X-Trace-Id" = "$script:tracePrefix-snapshot"
        } -SkipHttpErrorCheck
    Require-Status $snapshot @(200) "Flash Sale Campaign snapshot"
    if ([Guid](($snapshot.Content | ConvertFrom-Json).campaignId) -ne $happy.Id) {
        throw "Snapshot Campaign identity mismatch"
    }
    Write-Output "PASS schedule replay/conflict/snapshot=200/409/200"

    Wait-DatabaseValue "campaign_db" `
        "SELECT status FROM campaigns WHERE id='$($happy.Id)'::uuid;" "ACTIVE" 90 | Out-Null
    Wait-DatabaseValue "campaign_db" `
        "SELECT status FROM campaigns WHERE id='$($happy.Id)'::uuid;" "ENDED" 90 | Out-Null
    $happyEvents = Invoke-Database "campaign_db" `
        "SELECT event_type || '|' || publish_status FROM campaign_outbox_events WHERE aggregate_id='$($happy.Id)'::uuid ORDER BY aggregate_version;"
    if ($happyEvents.Count -ne 2 -or $happyEvents[0] -ne "CampaignScheduled|PUBLISHED" -or
            $happyEvents[1] -ne "CampaignActivated|PUBLISHED") {
        throw "Lifecycle event order/status mismatch: $($happyEvents -join ',')"
    }
    Write-Output "PASS lifecycle SCHEDULED-ACTIVE-ENDED events=Scheduled,Activated no-ended-event"

    $futureStart = [DateTimeOffset]::UtcNow.AddMinutes(30)
    $futureEnd = $futureStart.AddMinutes(10)
    $productFailure = New-CampaignFixture "PRODUCT-DOWN" $futureStart $futureEnd
    Invoke-Compose @("stop", "product-service")
    $productUnavailable = Invoke-Schedule $productFailure "schedule-$($productFailure.Id)"
    Require-Status $productUnavailable @(503) "Product unavailable schedule"
    $productFailureDetail = Get-CampaignDetail $productFailure.Id
    Require-Status $productFailureDetail @(200) "Product failure Campaign detail"
    if ((($productFailureDetail.Content | ConvertFrom-Json).data.status) -ne "DRAFT") {
        throw "Campaign changed state while Product was unavailable"
    }
    Invoke-Compose @("start", "product-service")
    Wait-Http "http://127.0.0.1:18082/actuator/health/readiness" 90
    $productRecovered = Invoke-Schedule $productFailure "schedule-$($productFailure.Id)"
    Require-Status $productRecovered @(200) "Product recovery schedule"
    Write-Output "PASS product-down-recovery status=503/DRAFT/200"

    $inventoryFailure = New-CampaignFixture "INVENTORY-DOWN" $futureStart.AddMinutes(1) $futureEnd.AddMinutes(1)
    Invoke-Compose @("stop", "inventory-service")
    $inventoryUnavailable = Invoke-Schedule $inventoryFailure "schedule-$($inventoryFailure.Id)"
    Require-Status $inventoryUnavailable @(503) "Inventory unavailable schedule"
    $requestIdBefore = [string](Invoke-Database "campaign_db" `
        "SELECT inventory_request_id FROM campaign_schedule_operations WHERE campaign_id='$($inventoryFailure.Id)'::uuid;" |
        Select-Object -First 1)
    Invoke-Compose @("start", "inventory-service")
    Wait-Http "http://127.0.0.1:18088/actuator/health/readiness" 90
    $inventoryRecovered = Invoke-Schedule $inventoryFailure "schedule-$($inventoryFailure.Id)"
    Require-Status $inventoryRecovered @(200) "Inventory recovery schedule"
    $requestIdAfter = [string](Invoke-Database "campaign_db" `
        "SELECT inventory_request_id FROM campaign_schedule_operations WHERE campaign_id='$($inventoryFailure.Id)'::uuid;" |
        Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($requestIdBefore) -or $requestIdBefore -ne $requestIdAfter) {
        throw "Inventory request identity changed during recovery"
    }
    Write-Output "PASS inventory-down-recovery status=503/200 stable-request-id=true"

    $registryFailure = New-CampaignFixture "REGISTRY-DOWN" $futureStart.AddMinutes(2) $futureEnd.AddMinutes(2)
    Invoke-Compose @("stop", "schema-registry")
    Invoke-Compose @("restart", "campaign-service")
    Wait-Http "$campaignBase/actuator/health/readiness" 90
    $registryScheduled = Invoke-ScheduleAfterRestart `
        $registryFailure "schedule-$($registryFailure.Id)"
    Require-Status $registryScheduled @(200) "Registry-down schedule"
    Start-Sleep -Seconds 3
    $registryOutboxBefore = [string](Invoke-Database "campaign_db" `
        "SELECT publish_status FROM campaign_outbox_events WHERE aggregate_id='$($registryFailure.Id)'::uuid ORDER BY aggregate_version LIMIT 1;" |
        Select-Object -First 1)
    if ($registryOutboxBefore -eq "PUBLISHED") {
        throw "Registry-down outbox unexpectedly published from a fresh producer"
    }
    Invoke-Compose @("start", "schema-registry")
    Wait-Http "http://127.0.0.1:8081/subjects" 90
    Wait-DatabaseValue "campaign_db" `
        "SELECT publish_status FROM campaign_outbox_events WHERE aggregate_id='$($registryFailure.Id)'::uuid ORDER BY aggregate_version LIMIT 1;" `
        "PUBLISHED" 90 | Out-Null
    Write-Output "PASS registry-outage durable-retry state=$registryOutboxBefore/PUBLISHED"

    $kafkaFailure = New-CampaignFixture "KAFKA-DOWN" $futureStart.AddMinutes(3) $futureEnd.AddMinutes(3)
    $env:JAVA_TOOL_OPTIONS = "$originalJavaToolOptions -Dflashsale.campaign.outbox.scan-delay=200ms -Dflashsale.campaign.outbox.retry-backoff-cap=1s -Dspring.kafka.producer.properties.max.block.ms=2000 -Dspring.kafka.producer.properties.request.timeout.ms=1000 -Dspring.kafka.producer.properties.delivery.timeout.ms=3000"
    $script:campaignRuntimeModified = $true
    Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "campaign-service")
    Wait-Http "$campaignBase/actuator/health/readiness" 90
    Invoke-Compose @("stop", "kafka")
    $kafkaScheduled = Invoke-ScheduleAfterRestart $kafkaFailure "schedule-$($kafkaFailure.Id)"
    Require-Status $kafkaScheduled @(200) "Kafka-down schedule"
    Wait-DatabaseValue "campaign_db" `
        "SELECT publish_status FROM campaign_outbox_events WHERE aggregate_id='$($kafkaFailure.Id)'::uuid ORDER BY aggregate_version LIMIT 1;" `
        "FAILED" 150 | Out-Null
    $failedEvent = Invoke-Database "campaign_db" `
        "SELECT id || '|' || retry_count FROM campaign_outbox_events WHERE aggregate_id='$($kafkaFailure.Id)'::uuid ORDER BY aggregate_version LIMIT 1;" |
        Select-Object -First 1
    $failedParts = ([string]$failedEvent).Split('|')
    $eventId = [Guid]$failedParts[0]
    if ([int]$failedParts[1] -ne 10) {
        throw "Terminal outbox retry count was not ten"
    }
    Invoke-Compose @("start", "kafka")
    $kafkaContainer = & docker compose --env-file $envFile -f $composeFile ps -q kafka
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $health = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $kafkaContainer
        if ($health -eq "healthy") { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ($health -ne "healthy") {
        throw "Kafka did not recover"
    }
    Wait-Http "http://127.0.0.1:8081/subjects" 90
    $requeue = Invoke-WebRequest `
        -Uri "$gatewayBase/api/v1/admin/campaigns/$($kafkaFailure.Id)/outbox-events/$eventId/requeue" `
        -Method Post -ContentType "application/json" -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = "$script:tracePrefix-requeue"
        } -Body '{}' -SkipHttpErrorCheck
    Require-Status $requeue @(200) "Outbox requeue"
    $requeueData = ($requeue.Content | ConvertFrom-Json).data
    if ([Guid]$requeueData.eventId -ne $eventId -or $requeueData.publishStatus -ne "PENDING") {
        throw "Requeue did not preserve the failed event identity"
    }
    Wait-DatabaseValue "campaign_db" `
        "SELECT publish_status FROM campaign_outbox_events WHERE id='$eventId'::uuid;" `
        "PUBLISHED" 90 | Out-Null
    $publishedEventId = [Guid](Invoke-Database "campaign_db" `
        "SELECT id FROM campaign_outbox_events WHERE id='$eventId'::uuid;" | Select-Object -First 1)
    if ($publishedEventId -ne $eventId) {
        throw "Published event identity changed after requeue"
    }
    Write-Output "PASS kafka-terminal-requeue retries=10 same-event-id=true final=PUBLISHED"

    $consumerOutput = & docker compose --env-file $envFile -f $composeFile exec -T kafka `
        /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 `
        --topic campaign.lifecycle.v1 --from-beginning --timeout-ms 5000 `
        --property print.key=true --property print.value=false --property print.headers=true 2>&1
    $consumerText = $consumerOutput -join "`n"
    if ($consumerText -notmatch [regex]::Escape([string]$happy.Id) -or
            $consumerText -notmatch "traceparent:" -or
            $consumerText -notmatch "contentType:application/avro") {
        throw "Kafka smoke did not observe the Campaign key and W3C/Avro headers"
    }
    $subjects = (Invoke-RestMethod -Uri "http://127.0.0.1:8081/subjects")
    if ($subjects -notcontains "campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1" -or
            $subjects -notcontains "campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1") {
        throw "Approved Campaign Schema Registry subjects are missing"
    }
    Write-Output "PASS kafka-registry key=true w3c=true avro=true subjects=2"

    $productArchived = Invoke-WebRequest `
        -Uri "$gatewayBase/api/v1/admin/catalog/products/$script:productId/archive" `
        -Method Post -Headers @{
            Authorization = "Bearer $script:adminToken"
            "X-Trace-Id" = "$script:tracePrefix-product-archive"
            "If-Match" = [string]$script:productVersion
            "Idempotency-Key" = "product-archive-$productSuffix"
        } -SkipHeaderValidation -SkipHttpErrorCheck
    Require-Status $productArchived @(200) "Product fixture archive"
    Write-Output "PASS product-fixture archived"

    Write-Output "FEATURE_017_SMOKE=PASS"
} finally {
    if ($restoreServices) {
        try { Invoke-Compose @("start", "kafka", "schema-registry", "product-service", "inventory-service") } catch { }
        if ($script:campaignRuntimeModified) {
            $env:JAVA_TOOL_OPTIONS = $originalJavaToolOptions
            try {
                Invoke-Compose @("up", "-d", "--no-deps", "--force-recreate", "campaign-service")
                Wait-Http "$campaignBase/actuator/health/readiness" 90
            } catch { }
        }
    }
    if ($script:userId) {
        try { Invoke-Database "auth_db" "DELETE FROM users WHERE id='$script:userId'::uuid;" | Out-Null } catch { }
    }
    if ($script:variantId) {
        try { Invoke-InventoryFixture "cleanup" $script:variantId $script:sku } catch { }
    }
    $script:adminToken = $null
}
