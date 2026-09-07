<#
.SYNOPSIS
  Runs bounded validation scenarios for Feature 049 regular purchase checkout.

.DESCRIPTION
  Static validates the disabled-by-default boundary. BuyNowPaid is the first live local scenario:
  it enables the reviewed flags only in this PowerShell process, provisions the additive Kafka
  contracts, runs service-owned migrations/fixture adapters, and drives the public Buy Now and
  Payment endpoints through Gateway. It never prints .env values, tokens, Checkout URLs, or raw
  provider/webhook payloads. Use -SkipBuild when the local service images already match the
  checked-out source and only migrations/runtime smoke need to be repeated.
#>
[CmdletBinding()]
param(
    [ValidateSet(
        'Static',
        'Contracts',
        'BuyNowPaid',
        'CartPaid',
        'CartEditedWhilePaying',
        'PriceChanged',
        'InsufficientStock',
        'PaymentFailed',
        'HoldExpired',
        'Replay',
        'Concurrency',
        'DependencyRestart',
        'LateSuccess',
        'FlashSaleRegression',
        'All'
    )]
    [string] $Scenario = 'Static',
    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 600,
    [switch] $SkipBuild,
    [switch] $AllowDependencyRestart,
    [switch] $RunInteractive
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$budgetWatch = [Diagnostics.Stopwatch]::StartNew()

function Assert-Budget([string] $Stage) {
    if ($budgetWatch.Elapsed.TotalSeconds -gt $TimeoutSeconds) {
        throw "$Stage exceeded the Feature 049 $TimeoutSeconds-second execution budget."
    }
}

function Get-RepoPath([string] $RelativePath) {
    return Join-Path $repoRoot ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
}

$envFile = Get-RepoPath 'infra/docker/.env'
$composeFile = Get-RepoPath 'infra/docker/compose.yml'
$composeDevFile = Get-RepoPath 'infra/docker/compose.dev.yml'
$topicBootstrapScript = Get-RepoPath 'infra/docker/kafka/init-regular-purchase-topics.sh'
$schemaBootstrapScript = Get-RepoPath 'infra/docker/schema-registry/register-regular-purchase-schemas.ps1'

function Assert-File([string] $RelativePath) {
    Assert-Budget "checking $RelativePath"
    $path = Get-RepoPath $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required Feature 049 file is missing: $RelativePath"
    }
}

function Assert-Content([string] $RelativePath, [string] $Pattern, [string] $Description) {
    Assert-File $RelativePath
    $content = Get-Content -LiteralPath (Get-RepoPath $RelativePath) -Raw
    if ($content -notmatch $Pattern) {
        throw "$Description is missing from $RelativePath."
    }
}

function Invoke-Compose([string[]] $Arguments, [string] $Profile = 'apps') {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile $Profile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose command failed with exit code $LASTEXITCODE."
    }
}

function Get-ServiceContainer([string] $Service) {
    $container = & docker compose --env-file $envFile -f $composeFile ps -q $Service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($container -join ''))) {
        throw "$Service container is not running."
    }
    return [string]($container | Select-Object -First 1)
}

function Invoke-Database([string] $Database, [string] $Sql) {
    $container = Get-ServiceContainer 'postgres'
    $output = & docker exec $container sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh $Database $Sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed for $Database." }
    return @($output)
}

function Wait-Until([scriptblock] $Condition, [string] $Description, [int] $Seconds = 120) {
    $waitWatch = [Diagnostics.Stopwatch]::StartNew()
    do {
        Assert-Budget $Description
        try { if (& $Condition) { return } } catch { }
        Start-Sleep -Seconds 2
    } while ($waitWatch.Elapsed.TotalSeconds -lt $Seconds)
    throw "Timed out waiting for $Description."
}

function Wait-Http([string] $Uri, [int[]] $Expected = @(200), [int] $Seconds = 120) {
    Wait-Until -Description $Uri -Seconds $Seconds -Condition {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 4 -SkipHttpErrorCheck
        return $Expected -contains [int]$response.StatusCode
    }
}

function Invoke-Json([string] $Uri, [string] $Method, [hashtable] $Headers, [object] $Body = $null) {
    $parameters = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true
        SkipHttpErrorCheck = $true; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body
    }
    return Invoke-WebRequest @parameters
}

function Require-Status($Response, [int[]] $Expected, [string] $Step) {
    if ($Expected -notcontains [int]$Response.StatusCode) {
        $detail = ''
        try {
            $payload = $Response.Content | ConvertFrom-Json
            if ($null -ne $payload.code) { $detail = ", code=$($payload.code)" }
            elseif ($null -ne $payload.errorCode) { $detail = ", errorCode=$($payload.errorCode)" }
        } catch { }
        throw "$Step returned HTTP $($Response.StatusCode)$detail."
    }
}

function Get-RequiredObjectProperty([object] $Object, [string] $Name, [string] $Description) {
    if ($null -eq $Object) {
        throw "$Description is missing from the response."
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "$Description is missing property '$Name'."
    }
    return $property.Value
}

function New-Shopper([string] $Label) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $password = 'Sm0ke!Aa' + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
    $email = "feature049-$Label-$suffix@example.test"
    $trace = "feature049-$Label-$suffix"
    $register = Invoke-Json "$gatewayBase/api/v1/auth/register" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ email = $email; username = "feature049-$Label-$suffix"; password = $password } | ConvertTo-Json -Compress)
    Require-Status $register @(201) "$Label registration"
    $userId = [Guid](($register.Content | ConvertFrom-Json).data.userId)
    $login = Invoke-Json "$gatewayBase/api/v1/auth/login" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ login = $email; password = $password; deviceName = 'feature-049-smoke' } | ConvertTo-Json -Compress)
    Require-Status $login @(200) "$Label login"
    return [pscustomobject]@{ Id = $userId; Token = [string](($login.Content | ConvertFrom-Json).data.accessToken) }
}

function Get-CartFingerprint([string] $Token) {
    $response = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' `
        @{ Authorization = "Bearer $Token"; 'X-Trace-Id' = 'feature049-cart-before-after' }
    Require-Status $response @(200) 'Cart read'
    $data = ($response.Content | ConvertFrom-Json).data
    $items = @($data.items | ForEach-Object {
            [pscustomobject]@{ variantId = [string]$_.variantId; quantity = [int]$_.quantity }
        } | Sort-Object variantId)
    return ($items | ConvertTo-Json -Compress)
}

function Get-KafkaEndOffset([string] $Topic) {
    $container = Get-ServiceContainer 'kafka'
    $lines = & docker exec $container sh -c "/opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 --topic '$Topic'"
    if ($LASTEXITCODE -ne 0) { throw "Could not read Kafka end offset for $Topic." }
    $sum = [long]0
    foreach ($line in @($lines)) {
        $parts = ([string]$line).Trim().Split(':')
        if ($parts.Length -ge 3) { $sum += [long]$parts[2] }
    }
    return $sum
}

function Invoke-TopicAndSchemaBootstrap {
    Assert-Budget 'regular-purchase topic bootstrap'
    $kafkaContainer = Get-ServiceContainer 'kafka'
    $containerScript = '/tmp/feature-049-init-regular-purchase-topics.sh'
    $normalizedScript = Join-Path ([IO.Path]::GetTempPath()) `
        ('feature-049-regular-purchase-topics-' + [Guid]::NewGuid().ToString('N') + '.sh')
    try {
        $scriptText = [IO.File]::ReadAllText($topicBootstrapScript).Replace("`r`n", "`n").Replace("`r", "`n")
        [IO.File]::WriteAllText($normalizedScript, $scriptText, [Text.UTF8Encoding]::new($false))
        & docker cp $normalizedScript "${kafkaContainer}:$containerScript"
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy the Feature 049 topic bootstrap.' }
        & docker exec $kafkaContainer bash $containerScript
        if ($LASTEXITCODE -ne 0) { throw 'Feature 049 Kafka topic provisioning failed.' }
    } finally {
        if (Test-Path -LiteralPath $normalizedScript) { Remove-Item -LiteralPath $normalizedScript -Force }
    }
    & pwsh -NoLogo -NoProfile -File $schemaBootstrapScript -SchemaRegistryUrl 'http://127.0.0.1:8081'
    if ($LASTEXITCODE -ne 0) { throw 'Feature 049 Schema Registry provisioning failed.' }
}

function Invoke-InventoryMigration {
    # Inventory has no separate compose profile. Run its service-owned Liquibase process once,
    # with the web/listener workers disabled, before starting the live service.
    Invoke-Compose @('stop', 'inventory-service')
    $previous = [Environment]::GetEnvironmentVariable('INVENTORY_LIQUIBASE_ENABLED', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('INVENTORY_LIQUIBASE_ENABLED', 'true', 'Process')
        Invoke-Compose @('run', '--rm', '--no-deps', '-e', 'SPRING_LIQUIBASE_ENABLED=true',
            '-e', 'SPRING_MAIN_KEEP_ALIVE=false', '-e', 'SPRING_KAFKA_LISTENER_AUTO_STARTUP=false',
            '-e', 'SPRING_TASK_SCHEDULING_ENABLED=false',
            # Compose interpolates the live feature flags from the parent process. Explicitly
            # disable every regular-hold worker for this migration-only JVM so it does not
            # construct Kafka producer/consumer beans before Liquibase finishes.
            '-e', 'INVENTORY_REGULAR_HOLD_API_ENABLED=false',
            '-e', 'INVENTORY_REGULAR_HOLD_COMMAND_CONSUMER_ENABLED=false',
            '-e', 'INVENTORY_REGULAR_HOLD_OUTBOX_PUBLISHER_ENABLED=false',
            '-e', 'INVENTORY_REGULAR_HOLD_EXPIRY_ENABLED=false',
            'inventory-service',
            '--spring.main.web-application-type=none') 'migrations'
    } finally {
        [Environment]::SetEnvironmentVariable('INVENTORY_LIQUIBASE_ENABLED', $previous, 'Process')
    }
}

function Invoke-CartMigration {
    # Cart owns its Liquibase history. Run it as a one-off process so the live Cart replica never
    # races another replica for the Liquibase lock during a checkout smoke.
    Invoke-Compose @('stop', 'cart-service')
    Invoke-Compose @('run', '--rm', '--no-deps',
        '-e', 'SPRING_LIQUIBASE_ENABLED=true',
        '-e', 'SPRING_MAIN_KEEP_ALIVE=false',
        '-e', 'SPRING_KAFKA_LISTENER_AUTO_STARTUP=false',
        '-e', 'SPRING_TASK_SCHEDULING_ENABLED=false',
        # The listener has its own feature flag in the Cart configuration.  Keep it disabled
        # explicitly for the one-off Liquibase JVM so a compose .env cannot keep the process alive.
        '-e', 'CART_CHECKOUT_RECONCILIATION_CONSUMER_ENABLED=false',
        'cart-migration', '--spring.main.web-application-type=none') 'migrations'
}

function Invoke-InventoryFixture([Guid] $VariantId, [string] $Sku) {
    Assert-Budget 'Inventory fixture'
    Invoke-Compose @('run', '--rm', '--no-deps',
        '-e', 'INVENTORY_FIXTURE_ENABLED=true',
        '-e', "INVENTORY_FIXTURE_VARIANT_ID=$VariantId",
        '-e', "INVENTORY_FIXTURE_SKU_SNAPSHOT=$Sku",
        '-e', 'INVENTORY_FIXTURE_QUANTITY=1',
        '-e', 'INVENTORY_FIXTURE_REASON=feature049-buy-now',
        '-e', 'SPRING_MAIN_KEEP_ALIVE=false',
        '-e', 'SPRING_KAFKA_LISTENER_AUTO_STARTUP=false',
        '-e', 'SPRING_TASK_SCHEDULING_ENABLED=false',
        '-e', 'SPRING_LIQUIBASE_ENABLED=false',
        'inventory-service', '--spring.main.web-application-type=none') 'apps'
}

function Get-InventoryAvailable([Guid] $VariantId) {
    # The Inventory fixture is intentionally initialize-once.  Repeated smoke runs must not
    # invoke it for an existing row (the service correctly rejects that as "already initialized").
    # Read the availability equation so Cart scenarios can reuse an existing sellable fixture while
    # avoiding variants exhausted by an earlier run.  This is observation only; stock is still
    # created exclusively through the service-owned fixture command above.
    $sql = @"
SELECT (i.on_hand_quantity - i.campaign_allocated_quantity - COALESCE((
    SELECT SUM(hi.quantity)
    FROM regular_stock_hold_items hi
    JOIN regular_stock_holds h ON h.id = hi.hold_id
    WHERE hi.variant_id = i.variant_id
      AND h.status = 'HELD'
      AND h.expires_at > NOW()
), 0))
FROM inventory_items i
WHERE i.variant_id = '$VariantId';
"@
    $row = @(Invoke-Database 'inventory_db' $sql.Trim() | Select-Object -First 1)
    if ($row.Count -eq 0 -or [string]::IsNullOrWhiteSpace([string]$row[0])) {
        return $null
    }
    return [long]([string]$row[0]).Trim()
}

function Invoke-ContainerSignedWebhook([Guid] $PaymentId, [Guid] $AttemptId, [Guid] $OrderId,
        [string] $ProviderSessionId) {
    $container = Get-ServiceContainer 'payment-service'
    & docker exec $container sh -c '
      payment="$1"; attempt="$2"; order="$3"; session="$4"
      timestamp=$(date +%s)
      event_id="evt_feature049_$(printf %s "$payment" | tr -d -)_$timestamp"
      payload=$(printf "{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"created\":%s,\"livemode\":false,\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"%s\",\"object\":\"checkout.session\",\"status\":\"complete\",\"payment_status\":\"paid\",\"payment_intent\":\"pi_feature049_%s\",\"metadata\":{\"paymentId\":\"%s\",\"attemptId\":\"%s\",\"orderId\":\"%s\"}}}}" "$event_id" "$STRIPE_API_VERSION" "$timestamp" "$session" "$payment" "$payment" "$attempt" "$order")
      digest=$(printf "%s" "$timestamp.$payload" | openssl dgst -sha256 -hmac "$STRIPE_WEBHOOK_SECRET" -hex | awk "{print \$NF}")
      signature="t=$timestamp,v1=$digest"
      first=$(curl -sS -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Stripe-Signature: $signature" --data-binary "$payload" http://api-gateway:8080/webhooks/v1/payments/stripe)
      second=$(curl -sS -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Stripe-Signature: $signature" --data-binary "$payload" http://api-gateway:8080/webhooks/v1/payments/stripe)
      [ "$first" = "204" ] && [ "$second" = "204" ] || { echo "Webhook acknowledgement failed" >&2; exit 1; }
    ' sh $PaymentId $AttemptId $OrderId $ProviderSessionId
    if ($LASTEXITCODE -ne 0) { throw 'Signed Stripe test webhook was rejected.' }
}

function Invoke-BuyNowPaid {
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'infra/docker/.env is required for BuyNowPaid; local Secret values stay ignored.'
    }
    $runtimeFlags = @(
        'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED', 'ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED',
        'ORDER_REGULAR_HOLD_RESULT_CONSUMER_ENABLED', 'ORDER_REGULAR_HOLD_COMMAND_PRODUCER_ENABLED',
        'ORDER_CART_RECONCILIATION_PRODUCER_ENABLED', 'CART_CHECKOUT_RECONCILIATION_CONSUMER_ENABLED',
        'INVENTORY_REGULAR_HOLD_API_ENABLED', 'INVENTORY_REGULAR_HOLD_COMMAND_CONSUMER_ENABLED',
        'INVENTORY_REGULAR_HOLD_OUTBOX_PUBLISHER_ENABLED', 'PAYMENT_ACCEPTANCE_ENABLED',
        'PAYMENT_CHECKOUT_ENABLED', 'PAYMENT_WEBHOOK_PROCESSING_ENABLED', 'PAYMENT_CONSUMER_ENABLED',
        'PAYMENT_OUTBOX_PUBLISHER_ENABLED', 'PAYMENT_RECOVERY_ENABLED', 'STRIPE_ENABLED')
    $previous = @{}
    foreach ($name in $runtimeFlags) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
    }
    try {
        Invoke-Compose @('up', '-d', 'postgres', 'redis', 'kafka', 'schema-registry')
        Wait-Http 'http://127.0.0.1:8081/subjects' @(200) 180
        Invoke-TopicAndSchemaBootstrap
        # Build Flash Sale as well: its outbox migration owns the causation_id column
        # required by the current regular-purchase/late-confirmation runtime.
        if (-not $SkipBuild) {
            Invoke-Compose @('build', 'api-gateway', 'product-service', 'flashsale-service', 'order-service', 'inventory-service', 'payment-service', 'cart-service')
        }
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'SPRING_LIQUIBASE_ENABLED=true',
            '-e', 'SPRING_MAIN_KEEP_ALIVE=false',
            '-e', 'SPRING_KAFKA_LISTENER_AUTO_STARTUP=false',
            '-e', 'SPRING_TASK_SCHEDULING_ENABLED=false',
            '-e', 'FLASHSALE_RUNTIME_ENABLED=false',
            '-e', 'FLASHSALE_RUNTIME_RECONCILIATION_ENABLED=false',
            'flashsale-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-InventoryMigration
        Invoke-CartMigration
        # The migration services inherit compose-time flags from the parent process. Override
        # every Feature 049 worker explicitly so a Liquibase-only JVM does not require the
        # runtime persistence/HTTP/Kafka beans that are exercised by the live services.
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED=false',
            '-e', 'ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED=false',
            '-e', 'ORDER_REGULAR_HOLD_RESULT_CONSUMER_ENABLED=false',
            '-e', 'ORDER_REGULAR_HOLD_COMMAND_PRODUCER_ENABLED=false',
            '-e', 'ORDER_CART_RECONCILIATION_PRODUCER_ENABLED=false',
            'order-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'PAYMENT_ACCEPTANCE_ENABLED=false',
            '-e', 'PAYMENT_CHECKOUT_ENABLED=false',
            '-e', 'PAYMENT_WEBHOOK_PROCESSING_ENABLED=false',
            '-e', 'PAYMENT_CONSUMER_ENABLED=false',
            '-e', 'PAYMENT_OUTBOX_PUBLISHER_ENABLED=false',
            '-e', 'PAYMENT_RECOVERY_ENABLED=false',
            '-e', 'STRIPE_ENABLED=false',
            'payment-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-Compose @('up', '-d', '--force-recreate', 'product-service', 'flashsale-service', 'order-service',
            'inventory-service', 'payment-service', 'cart-service', 'api-gateway')
        Wait-Http 'http://127.0.0.1:18084/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18082/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18085/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18088/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18086/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18089/actuator/health/readiness' @(200) 240
        Wait-Http $gatewayBase/actuator/health @(200) 180

        $paymentEventsBefore = Get-KafkaEndOffset 'flashsale.payment.events.v1'
        $holdCommandsBefore = Get-KafkaEndOffset 'flashsale.inventory.regular-hold.commands.v1'
        $holdEventsBefore = Get-KafkaEndOffset 'flashsale.inventory.regular-hold.events.v1'
        $shopper = New-Shopper 'buynow'
        $cartBefore = Get-CartFingerprint $shopper.Token

        $catalogResponse = Invoke-Json "$gatewayBase/api/v1/catalog/products?page=0&size=100" 'Get' @{}
        Require-Status $catalogResponse @(200) 'catalog query'
        $variants = @((($catalogResponse.Content | ConvertFrom-Json).data.data | ForEach-Object { $_.variants }) |
            Where-Object {
                if ($null -eq $_) { return $false }
                # Catalog variants from older local images omit `sellable`; treat the
                # optional field as sellable unless the API explicitly marks it false.
                $sellableProperty = $_.PSObject.Properties['sellable']
                return $null -eq $sellableProperty -or $sellableProperty.Value -ne $false
            })
        $existingAvailableVariants = @((Invoke-Database 'inventory_db' `
                'SELECT i.variant_id FROM inventory_items i WHERE i.on_hand_quantity - i.campaign_allocated_quantity - COALESCE((SELECT SUM(hi.quantity) FROM regular_stock_hold_items hi JOIN regular_stock_holds h ON h.id = hi.hold_id WHERE hi.variant_id = i.variant_id AND h.status = ''HELD'' AND h.expires_at > NOW()), 0) >= 1 ORDER BY i.updated_at DESC;') |
            ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
        $existingVariants = @((Invoke-Database 'inventory_db' 'SELECT variant_id FROM inventory_items;') |
            ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
        $variant = $null
        $fixtureRequired = $true
        foreach ($candidate in $variants) {
            $candidateId = [string](Get-RequiredObjectProperty $candidate 'id' 'Catalog variant')
            if ($existingAvailableVariants -contains $candidateId) {
                $variant = $candidate
                $fixtureRequired = $false
                break
            }
        }
        if ($null -eq $variant) {
            foreach ($candidate in $variants) {
                $candidateId = [string](Get-RequiredObjectProperty $candidate 'id' 'Catalog variant')
                if ($existingVariants -notcontains $candidateId) { $variant = $candidate; break }
            }
        }
        # The fixture command is an idempotent local-only upsert. After a previous
        # smoke run every catalog variant may already have a row, so reuse the first
        # sellable variant instead of making repeatability depend on database cleanup.
        if ($null -eq $variant) {
            $variant = $variants | Select-Object -First 1
        }
        if ($null -eq $variant) { throw 'No sellable catalog variant is available for the fixture.' }
        $variantId = [Guid](Get-RequiredObjectProperty $variant 'id' 'Selected catalog variant')
        $variantSku = [string](Get-RequiredObjectProperty $variant 'sku' 'Selected catalog variant')
        $variantPrice = [string](Get-RequiredObjectProperty $variant 'basePrice' 'Selected catalog variant')
        $variantCurrency = [string](Get-RequiredObjectProperty $variant 'currency' 'Selected catalog variant')
        if ($fixtureRequired) {
            Invoke-InventoryFixture $variantId $variantSku
        }

        $idempotencyKey = 'feature049-' + [Guid]::NewGuid().ToString('N')
        $body = @{ variantId = [string]$variantId; quantity = 1
            expectedUnitPrice = $variantPrice; currency = $variantCurrency } |
            ConvertTo-Json -Compress
        $headers = @{ Authorization = "Bearer $($shopper.Token)"; 'Idempotency-Key' = $idempotencyKey
            'X-Trace-Id' = 'feature049-buynow' }
        $accepted = Invoke-Json "$gatewayBase/api/v1/orders/buy-now" 'Post' $headers $body
        Require-Status $accepted @(201) 'Buy Now acceptance'
        $acceptedData = ($accepted.Content | ConvertFrom-Json).data
        $orderId = [Guid]$acceptedData.orderId
        $purchaseRequestId = [Guid]$acceptedData.purchaseRequestId
        $replay = Invoke-Json "$gatewayBase/api/v1/orders/buy-now" 'Post' $headers $body
        Require-Status $replay @(200) 'Buy Now replay'
        if ($replay.Headers['Idempotency-Replayed'] -ne 'true') { throw 'Buy Now replay did not carry Idempotency-Replayed=true.' }
        if ([Guid](($replay.Content | ConvertFrom-Json).data.orderId) -ne $orderId) {
            throw 'Buy Now replay returned a different Order identity.'
        }

        $payment = $null
        Wait-Until -Description 'Payment creation from Buy Now' -Seconds 180 -Condition {
            $candidate = Invoke-Json "$gatewayBase/api/v1/payments/by-order/$orderId" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-payment' }
            if ([int]$candidate.StatusCode -eq 200) { $script:payment = ($candidate.Content | ConvertFrom-Json).data; return $true }
            return $false
        }
        # Wait-Until evaluates its condition in a child scope, so copy the
        # script-scoped response back before reading the payment identifier.
        $payment = $script:payment
        $paymentId = [Guid](Get-RequiredObjectProperty $payment 'id' 'Payment details')
        $checkoutKey = 'feature049-checkout-' + [Guid]::NewGuid().ToString('N')
        $checkoutHeaders = @{ Authorization = "Bearer $($shopper.Token)"; 'Idempotency-Key' = $checkoutKey
            'X-Trace-Id' = 'feature049-checkout' }
        $checkout = Invoke-Json "$gatewayBase/api/v1/payments/$paymentId/checkout-sessions" 'Post' $checkoutHeaders
        Require-Status $checkout @(201, 200, 202) 'Checkout session creation'
        $checkoutData = ($checkout.Content | ConvertFrom-Json).data
        if ([string]::IsNullOrWhiteSpace([string]$checkoutData.checkoutUrl)) { throw 'Checkout session did not return a provider URL.' }
        $checkoutReplay = Invoke-Json "$gatewayBase/api/v1/payments/$paymentId/checkout-sessions" 'Post' $checkoutHeaders
        Require-Status $checkoutReplay @(200, 201, 202) 'Checkout session replay'

        # The payment worker reconciles the signed webhook with Stripe's
        # authoritative Checkout Session state. Open the hosted test Checkout
        # and wait for the operator to complete it before injecting the signed
        # webhook; a payload alone must not mark an unpaid Stripe session paid.
        Start-Process ([string]$checkoutData.checkoutUrl)
        [void](Read-Host 'Complete the Stripe test Checkout in the browser, then press Enter')

        $attempt = [string](Invoke-Database 'payment_db' `
            "SELECT id || '|' || provider_session_id FROM payment_attempts WHERE payment_id='$paymentId'::uuid ORDER BY attempt_number DESC LIMIT 1;" |
            Select-Object -First 1)
        $attemptParts = $attempt.Split('|', 2)
        if ($attemptParts.Length -ne 2 -or [string]::IsNullOrWhiteSpace($attemptParts[1])) {
            throw 'Checkout did not persist a provider session identity.'
        }
        Invoke-ContainerSignedWebhook $paymentId ([Guid]$attemptParts[0]) $orderId $attemptParts[1]

        $order = $null
        Wait-Until -Description 'Order confirmation after Stripe webhook and Kafka' -Seconds 240 -Condition {
            $candidate = Invoke-Json "$gatewayBase/api/v1/orders/$orderId" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-order-final' }
            if ([int]$candidate.StatusCode -eq 200) {
                $script:order = ($candidate.Content | ConvertFrom-Json).data
                return [string]$script:order.status -eq 'CONFIRMED'
            }
            return $false
        }
        Wait-Until -Description 'regular Inventory hold confirmation' -Seconds 120 -Condition {
            $status = [string](Invoke-Database 'inventory_db' `
                "SELECT status FROM regular_stock_holds WHERE purchase_request_id='$purchaseRequestId'::uuid;" |
                Select-Object -First 1)
            return $status -eq 'CONFIRMED'
        }
        $hold = [string](Invoke-Database 'inventory_db' `
            "SELECT id FROM regular_stock_holds WHERE purchase_request_id='$purchaseRequestId'::uuid AND status='CONFIRMED';" |
            Select-Object -First 1)
        $movementCount = [int](Invoke-Database 'inventory_db' `
            "SELECT COUNT(*) FROM stock_movements WHERE reference_type='REGULAR_STOCK_HOLD' AND reference_id='$hold'::uuid AND movement_type='REGULAR_HOLD_CONFIRMED';" |
            Select-Object -First 1)
        if ($movementCount -ne 1) { throw "Expected one confirmed regular-stock movement; observed $movementCount." }
        Wait-Until -Description 'Payment event Kafka publication' -Seconds 60 -Condition {
            return (Get-KafkaEndOffset 'flashsale.payment.events.v1') -gt $paymentEventsBefore
        }
        Wait-Until -Description 'regular hold command Kafka publication' -Seconds 60 -Condition {
            return (Get-KafkaEndOffset 'flashsale.inventory.regular-hold.commands.v1') -gt $holdCommandsBefore
        }
        Wait-Until -Description 'regular hold result Kafka publication' -Seconds 60 -Condition {
            return (Get-KafkaEndOffset 'flashsale.inventory.regular-hold.events.v1') -gt $holdEventsBefore
        }
        $cartAfter = Get-CartFingerprint $shopper.Token
        if ($cartBefore -ne $cartAfter) { throw 'Buy Now changed the shopper Cart.' }
        $finalPayment = Invoke-Json "$gatewayBase/api/v1/payments/$paymentId" 'Get' `
            @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-payment-final' }
        Require-Status $finalPayment @(200) 'final Payment query'
        if ([string](($finalPayment.Content | ConvertFrom-Json).data.status) -ne 'SUCCEEDED') {
            throw 'Final Payment status was not SUCCEEDED.'
        }
        Write-Output 'FEATURE_049_BUY_NOW_PAID=PASS'
        Write-Output 'Buy Now: accepted and exact replay returned the original Order.'
        Write-Output 'Payment: Checkout Session created/replayed and signed test webhook acknowledged/replayed.'
        Write-Output 'Kafka: Payment event, regular-hold command, and regular-hold result advanced.'
        Write-Output 'Inventory: one regular hold CONFIRMED and exactly one physical deduction recorded.'
        Write-Output 'Order: CONFIRMED; Cart: unchanged.'
        Write-Output 'Secret values, tokens, Checkout URLs, provider payloads, and shopper identities were not printed.'
    } finally {
        foreach ($name in $runtimeFlags) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
}

function Invoke-CartPaid(
    [switch] $EditDuringPayment,
    [ValidateSet('Paid', 'PriceChanged', 'InsufficientStock')]
    [string] $ScenarioMode = 'Paid',
    [switch] $SkipBuild) {
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'infra/docker/.env is required for Cart checkout; local Secret values stay ignored.'
    }
    $runtimeFlags = @(
        'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED', 'ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED',
        'ORDER_REGULAR_HOLD_RESULT_CONSUMER_ENABLED', 'ORDER_REGULAR_HOLD_COMMAND_PRODUCER_ENABLED',
        'ORDER_CART_RECONCILIATION_PRODUCER_ENABLED', 'CART_CHECKOUT_RECONCILIATION_CONSUMER_ENABLED',
        'INVENTORY_REGULAR_HOLD_API_ENABLED', 'INVENTORY_REGULAR_HOLD_COMMAND_CONSUMER_ENABLED',
        'INVENTORY_REGULAR_HOLD_OUTBOX_PUBLISHER_ENABLED', 'PAYMENT_ACCEPTANCE_ENABLED',
        'PAYMENT_CHECKOUT_ENABLED', 'PAYMENT_WEBHOOK_PROCESSING_ENABLED', 'PAYMENT_CONSUMER_ENABLED',
        'PAYMENT_OUTBOX_PUBLISHER_ENABLED', 'PAYMENT_RECOVERY_ENABLED', 'STRIPE_ENABLED')
    $previous = @{}
    foreach ($name in $runtimeFlags) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
    }
    try {
        Invoke-Compose @('up', '-d', 'postgres', 'redis', 'kafka', 'schema-registry')
        Wait-Http 'http://127.0.0.1:8081/subjects' @(200) 180
        Invoke-TopicAndSchemaBootstrap
        if (-not $SkipBuild) {
            Invoke-Compose @('build', 'api-gateway', 'product-service', 'flashsale-service', 'order-service',
                'inventory-service', 'payment-service', 'cart-service')
        }
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'SPRING_LIQUIBASE_ENABLED=true',
            '-e', 'SPRING_MAIN_KEEP_ALIVE=false',
            '-e', 'SPRING_KAFKA_LISTENER_AUTO_STARTUP=false',
            '-e', 'SPRING_TASK_SCHEDULING_ENABLED=false',
            '-e', 'FLASHSALE_RUNTIME_ENABLED=false',
            '-e', 'FLASHSALE_RUNTIME_RECONCILIATION_ENABLED=false',
            'flashsale-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-InventoryMigration
        Invoke-CartMigration
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED=false',
            '-e', 'ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED=false',
            '-e', 'ORDER_REGULAR_HOLD_RESULT_CONSUMER_ENABLED=false',
            '-e', 'ORDER_REGULAR_HOLD_COMMAND_PRODUCER_ENABLED=false',
            '-e', 'ORDER_CART_RECONCILIATION_PRODUCER_ENABLED=false',
            'order-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-Compose @('run', '--rm', '--no-deps',
            '-e', 'PAYMENT_ACCEPTANCE_ENABLED=false', '-e', 'PAYMENT_CHECKOUT_ENABLED=false',
            '-e', 'PAYMENT_WEBHOOK_PROCESSING_ENABLED=false', '-e', 'PAYMENT_CONSUMER_ENABLED=false',
            '-e', 'PAYMENT_OUTBOX_PUBLISHER_ENABLED=false', '-e', 'PAYMENT_RECOVERY_ENABLED=false',
            '-e', 'STRIPE_ENABLED=false', 'payment-migration', '--spring.main.web-application-type=none') 'migrations'
        Invoke-Compose @('up', '-d', '--force-recreate', 'product-service', 'flashsale-service', 'order-service',
            'inventory-service', 'payment-service', 'cart-service', 'api-gateway')
        Wait-Http 'http://127.0.0.1:18084/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18082/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18085/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18088/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18086/actuator/health/readiness' @(200) 240
        Wait-Http 'http://127.0.0.1:18089/actuator/health/readiness' @(200) 240
        Wait-Http $gatewayBase/actuator/health @(200) 180

        $paymentEventsBefore = Get-KafkaEndOffset 'flashsale.payment.events.v1'
        $holdEventsBefore = Get-KafkaEndOffset 'flashsale.inventory.regular-hold.events.v1'
        $cartCommandsBefore = Get-KafkaEndOffset 'flashsale.cart.checkout.commands.v1'
        $shopperLabel = if ($EditDuringPayment) { 'cart-edited' } else { $ScenarioMode.ToLowerInvariant() }
        $shopper = New-Shopper $shopperLabel
        $catalogResponse = Invoke-Json "$gatewayBase/api/v1/catalog/products?page=0&size=100" 'Get' @{}
        Require-Status $catalogResponse @(200) 'catalog query for Cart checkout'
        $variants = @((($catalogResponse.Content | ConvertFrom-Json).data.data | ForEach-Object { $_.variants }) |
            Where-Object {
                if ($null -eq $_) { return $false }
                $sellable = $_.PSObject.Properties['sellable']
                return $null -eq $sellable -or $sellable.Value -ne $false
            })
        if ($variants.Count -lt 2) { throw 'Cart checkout requires at least two sellable catalog variants.' }
        # Prefer variants that either have not been initialized yet or still have at least one
        # available unit.  The negative scenarios are repeatable against a persistent local DB,
        # while successful Cart scenarios do not accidentally select a variant exhausted by an
        # earlier smoke run.
        $eligible = @($variants | Where-Object {
                $candidateId = [Guid](Get-RequiredObjectProperty $_ 'id' 'Cart catalog variant')
                $available = Get-InventoryAvailable $candidateId
                $null -eq $available -or $available -ge 1
            })
        if ($eligible.Count -lt 2) {
            throw 'Cart checkout requires two sellable variants with available or uninitialized stock.'
        }
        if ($ScenarioMode -eq 'InsufficientStock') {
            # The fixture initializes exactly one unit and is initialize-once. Prefer a fresh
            # variant, otherwise reuse a row whose current availability is exactly one; selecting
            # a larger stock would make this negative case nondeterministic on a persistent DB.
            $insufficientCandidate = $null
            foreach ($candidate in $eligible) {
                $candidateId = [Guid](Get-RequiredObjectProperty $candidate 'id' 'Cart catalog variant')
                $available = Get-InventoryAvailable $candidateId
                if ($null -eq $available -or $available -eq 1) {
                    $insufficientCandidate = $candidate
                    break
                }
            }
            if ($null -eq $insufficientCandidate) {
                throw 'InsufficientStock requires a fresh or exactly-one-unit inventory fixture.'
            }
            $selected = @($insufficientCandidate) + @($eligible |
                    Where-Object { $_.id -ne $insufficientCandidate.id } | Select-Object -First 1)
            if ($selected.Count -lt 2) {
                throw 'Cart checkout requires two distinct sellable catalog variants.'
            }
        } else {
            $selected = @($eligible | Select-Object -First 2)
        }
        foreach ($candidate in $selected) {
            $candidateId = [Guid](Get-RequiredObjectProperty $candidate 'id' 'Cart catalog variant')
            $candidateSku = [string](Get-RequiredObjectProperty $candidate 'sku' 'Cart catalog variant')
            if ($null -eq (Get-InventoryAvailable $candidateId)) {
                Invoke-InventoryFixture $candidateId $candidateSku
            }
            $set = Invoke-Json "$gatewayBase/api/v1/cart/items/$candidateId" 'Put' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-set' } `
                (@{ quantity = 1 } | ConvertTo-Json -Compress)
            Require-Status $set @(200) 'Cart item setup'
        }
        if ($ScenarioMode -eq 'InsufficientStock') {
            # Make the persisted Cart agree with the submitted quantity. The checkout API rejects
            # stale Cart snapshots before it reaches Inventory, so the negative case must create a
            # valid two-unit Cart and let Inventory (which owns one unit) reject the hold.
            $insufficientVariant = [Guid](Get-RequiredObjectProperty $selected[0] 'id' 'Cart catalog variant')
            $setInsufficientQuantity = Invoke-Json "$gatewayBase/api/v1/cart/items/$insufficientVariant" 'Put' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-insufficient-stock' } `
                (@{ quantity = 2 } | ConvertTo-Json -Compress)
            Require-Status $setInsufficientQuantity @(200) 'Cart insufficient-stock quantity setup'
        }
        $cartRead = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' `
            @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-read' }
        Require-Status $cartRead @(200) 'Cart checkout snapshot read'
        $cart = ($cartRead.Content | ConvertFrom-Json).data
        $cartItems = @($cart.items | Where-Object { $_.variantId -and $_.basePrice -and $_.currency })
        if ($cartItems.Count -ne 2) { throw "Expected two Cart items; observed $($cartItems.Count)." }
        $checkoutItems = @($cartItems | ForEach-Object {
            @{ variantId = [string]$_.variantId; quantity = [int]$_.quantity; itemVersion = [long]$_.itemVersion
                expectedUnitPrice = [string]$_.basePrice; currency = [string]$_.currency }
        })
        if ($ScenarioMode -eq 'PriceChanged') {
            $checkoutItems[0].expectedUnitPrice =
                ([decimal]$checkoutItems[0].expectedUnitPrice + 1).ToString(
                    [Globalization.CultureInfo]::InvariantCulture)
        } elseif ($ScenarioMode -eq 'InsufficientStock') {
            # The fixture owns one unit per variant. Asking for two must reject the
            # entire Cart submission before an Order, Payment, or hold is created.
            $checkoutItems[0].quantity = 2
        }
        $idempotencyKey = 'feature049-cart-' + [Guid]::NewGuid().ToString('N')
        $body = @{ cartVersion = [long]$cart.cartVersion; items = $checkoutItems } | ConvertTo-Json -Compress -Depth 8
        $headers = @{ Authorization = "Bearer $($shopper.Token)"; 'Idempotency-Key' = $idempotencyKey
            'X-Trace-Id' = 'feature049-cart-checkout' }
        $accepted = Invoke-Json "$gatewayBase/api/v1/orders/cart-checkouts" 'Post' $headers $body
        if ($ScenarioMode -eq 'PriceChanged' -or $ScenarioMode -eq 'InsufficientStock') {
            Require-Status $accepted @(409) "$ScenarioMode Cart checkout rejection"
            $rejection = $accepted.Content | ConvertFrom-Json
            $expectedCode = if ($ScenarioMode -eq 'PriceChanged') { 'PRICE_CHANGED' } else { 'INSUFFICIENT_STOCK' }
            if ([string]$rejection.errorCode -ne $expectedCode) {
                throw "$ScenarioMode Cart checkout returned unexpected errorCode '$($rejection.errorCode)'."
            }
            $cartAfterRejected = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-rejected-after' }
            Require-Status $cartAfterRejected @(200) "$ScenarioMode Cart after rejection"
            if (@((($cartAfterRejected.Content | ConvertFrom-Json).data.items)).Count -ne 2) {
                throw "$ScenarioMode Cart rejection unexpectedly changed Cart contents."
            }
            Write-Output "FEATURE_049_CART_$($ScenarioMode.ToUpperInvariant())=PASS"
            Write-Output "Cart checkout: $expectedCode rejected before Payment/hold; Cart remained unchanged."
            Write-Output 'Secret values, tokens, Checkout URLs, provider payloads, and shopper identities were not printed.'
            return
        }
        Require-Status $accepted @(201) 'Cart checkout acceptance'
        $acceptedData = ($accepted.Content | ConvertFrom-Json).data
        $orderId = [Guid]$acceptedData.orderId
        $purchaseRequestId = [Guid]$acceptedData.purchaseRequestId
        $replay = Invoke-Json "$gatewayBase/api/v1/orders/cart-checkouts" 'Post' $headers $body
        Require-Status $replay @(200) 'Cart checkout replay'
        if ($replay.Headers['Idempotency-Replayed'] -ne 'true') { throw 'Cart checkout replay did not carry Idempotency-Replayed=true.' }
        if ([Guid](($replay.Content | ConvertFrom-Json).data.orderId) -ne $orderId) {
            throw 'Cart checkout replay returned a different Order identity.'
        }
        if ($EditDuringPayment) {
            $edited = $cartItems | Select-Object -First 1
            $edit = Invoke-Json "$gatewayBase/api/v1/cart/items/$($edited.variantId)" 'Put' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-edit' } `
                (@{ quantity = 2 } | ConvertTo-Json -Compress)
            Require-Status $edit @(200) 'Cart edit while payment is pending'
        }
        $payment = $null
        Wait-Until -Description 'Payment creation from Cart checkout' -Seconds 180 -Condition {
            $candidate = Invoke-Json "$gatewayBase/api/v1/payments/by-order/$orderId" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-payment' }
            if ([int]$candidate.StatusCode -eq 200) { $script:payment = ($candidate.Content | ConvertFrom-Json).data; return $true }
            return $false
        }
        $payment = $script:payment
        $paymentId = [Guid](Get-RequiredObjectProperty $payment 'id' 'Cart Payment details')
        $checkoutKey = 'feature049-cart-checkout-' + [Guid]::NewGuid().ToString('N')
        $checkoutHeaders = @{ Authorization = "Bearer $($shopper.Token)"; 'Idempotency-Key' = $checkoutKey
            'X-Trace-Id' = 'feature049-cart-checkout-session' }
        $checkout = Invoke-Json "$gatewayBase/api/v1/payments/$paymentId/checkout-sessions" 'Post' $checkoutHeaders
        Require-Status $checkout @(201, 200, 202) 'Cart Checkout Session creation'
        $checkoutData = ($checkout.Content | ConvertFrom-Json).data
        if ([string]::IsNullOrWhiteSpace([string]$checkoutData.checkoutUrl)) { throw 'Cart Checkout Session did not return a provider URL.' }
        Start-Process ([string]$checkoutData.checkoutUrl)
        [void](Read-Host 'Complete the Cart Stripe test Checkout in the browser, then press Enter')
        $attempt = [string](Invoke-Database 'payment_db' `
            "SELECT id || '|' || provider_session_id FROM payment_attempts WHERE payment_id='$paymentId'::uuid ORDER BY attempt_number DESC LIMIT 1;" |
            Select-Object -First 1)
        $attemptParts = $attempt.Split('|', 2)
        if ($attemptParts.Length -ne 2 -or [string]::IsNullOrWhiteSpace($attemptParts[1])) {
            throw 'Cart Checkout did not persist a provider session identity.'
        }
        Invoke-ContainerSignedWebhook $paymentId ([Guid]$attemptParts[0]) $orderId $attemptParts[1]
        Wait-Until -Description 'Cart Order confirmation after Stripe webhook and Kafka' -Seconds 240 -Condition {
            $candidate = Invoke-Json "$gatewayBase/api/v1/orders/$orderId" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-order-final' }
            if ([int]$candidate.StatusCode -eq 200) {
                return [string](($candidate.Content | ConvertFrom-Json).data.status) -eq 'CONFIRMED'
            }
            return $false
        }
        Wait-Until -Description 'Cart regular hold confirmation' -Seconds 120 -Condition {
            $status = [string](Invoke-Database 'inventory_db' `
                "SELECT COUNT(*) FROM regular_stock_holds WHERE purchase_request_id='$purchaseRequestId'::uuid AND status='CONFIRMED';" |
                Select-Object -First 1)
            return [int]$status -eq 1
        }
        Wait-Until -Description 'Cart reconciliation command publication' -Seconds 120 -Condition {
            return (Get-KafkaEndOffset 'flashsale.cart.checkout.commands.v1') -gt $cartCommandsBefore
        }
        # Publication only proves that the reconciliation command reached Kafka; the Cart
        # consumer may still be processing it.  Wait for the shopper-visible Cart state to
        # converge before asserting the final contents, avoiding a transient false negative.
        Wait-Until -Description 'Cart reconciliation state' -Seconds 120 -Condition {
            $candidate = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' `
                @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-reconciliation-state' }
            if ([int]$candidate.StatusCode -ne 200) { return $false }
            $items = @((($candidate.Content | ConvertFrom-Json).data.items))
            if ($EditDuringPayment) {
                $edited = $items | Where-Object { [string]$_.variantId -eq [string]$cartItems[0].variantId }
                return @($items).Count -eq 1 -and $null -ne $edited -and [int]$edited.quantity -eq 2
            }
            return @($items).Count -eq 0
        }
        $cartAfterResponse = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' `
            @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-after' }
        Require-Status $cartAfterResponse @(200) 'Cart after confirmed payment'
        $cartAfterItems = @((($cartAfterResponse.Content | ConvertFrom-Json).data.items))
        if ($EditDuringPayment) {
            $editedAfter = $cartAfterItems | Where-Object { [string]$_.variantId -eq [string]$cartItems[0].variantId }
            if ($null -eq $editedAfter -or [int]$editedAfter.quantity -ne 2) {
                throw 'Cart reconciliation removed a later edit instead of preserving it.'
            }
            if (@($cartAfterItems).Count -ne 1) { throw 'Cart reconciliation did not remove the unchanged Cart item.' }
        } elseif (@($cartAfterItems).Count -ne 0) {
            throw 'Cart reconciliation did not remove all unchanged purchased items.'
        }
        $finalPayment = Invoke-Json "$gatewayBase/api/v1/payments/$paymentId" 'Get' `
            @{ Authorization = "Bearer $($shopper.Token)"; 'X-Trace-Id' = 'feature049-cart-payment-final' }
        Require-Status $finalPayment @(200) 'final Cart Payment query'
        if ([string](($finalPayment.Content | ConvertFrom-Json).data.status) -ne 'SUCCEEDED') {
            throw 'Final Cart Payment status was not SUCCEEDED.'
        }
        if ($EditDuringPayment) {
            Write-Output 'FEATURE_049_CART_EDITED_WHILE_PAYING=PASS'
            Write-Output 'Cart: later quantity edit survived confirmed-payment reconciliation; unchanged item was removed.'
        } else {
            Write-Output 'FEATURE_049_CART_PAID=PASS'
            Write-Output 'Cart: two immutable items became one Order/Payment and were removed after confirmed payment.'
        }
        Write-Output 'Cart checkout: exact replay returned the original Order; Payment, Kafka hold, Order, and reconciliation converged.'
        Write-Output 'Secret values, tokens, Checkout URLs, provider payloads, and shopper identities were not printed.'
    } finally {
        foreach ($name in $runtimeFlags) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
}

function Invoke-TestBackedScenario([string] $ScenarioName, [string[]] $Modules, [string] $Tests) {
    Assert-Budget "$ScenarioName scenario setup"
    $mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
        throw 'Maven wrapper is missing; cannot run the bounded Feature 049 scenario gate.'
    }
    $moduleArgument = $Modules -join ','
    $arguments = @('--batch-mode', '--no-transfer-progress', '-pl', $moduleArgument, '-am',
        "-Dtest=$Tests", '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
    Write-Output "FEATURE_049_$($ScenarioName.ToUpperInvariant())=RUN"
    $output = @(& $mavenWrapper @arguments 2>&1)
    $exitCode = $LASTEXITCODE
    Assert-Budget "$ScenarioName scenario"
    if ($exitCode -ne 0) {
        # Do not echo Maven logs: they can contain connection strings or environment-derived
        # diagnostics. The test reports remain available under each module's target directory.
        throw "$ScenarioName scenario failed (exitCode=$exitCode); inspect the bounded Surefire reports under target/surefire-reports."
    }
    Write-Output "FEATURE_049_$($ScenarioName.ToUpperInvariant())=PASS"
    Write-Output "${ScenarioName}: focused recovery/concurrency tests passed; diagnostics were redacted."
}

function Invoke-DependencyRestartScenario {
    if (-not $AllowDependencyRestart) {
        throw 'DependencyRestart requires explicit -AllowDependencyRestart because it restarts a local service.'
    }
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'infra/docker/.env is required for the dependency restart scenario.'
    }
    Assert-Budget 'dependency restart scenario setup'
    # Restart only the Order process; durable intake leases and idempotency rows are the recovery
    # boundary. The test suite verifies the same lease/replay behavior without printing payloads.
    Invoke-Compose @('restart', 'order-service')
    Invoke-TestBackedScenario 'DependencyRestart' @('services/order-service') `
        'RegularPurchaseRecoveryJobTests,RegularPurchaseRecoveryIntegrationTests'
}

function Invoke-StaticGate {
    $requiredFiles = @(
        'services/cart-service/pom.xml',
        'services/cart-service/src/main/resources/application.yml',
        'services/inventory-service/pom.xml',
        'services/inventory-service/src/main/resources/application.yml',
        'services/order-service/pom.xml',
        'services/order-service/src/main/resources/application.yml',
        'services/product-service/src/main/resources/application.yml',
        'infra/docker/.env.example',
        'infra/docker/compose.yml',
        'infra/k8s/overlays/cloud/config/cart-service-runtime-config.yaml',
        'infra/k8s/overlays/cloud/config/inventory-service-runtime-config.yaml',
        'infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml',
        'infra/k8s/overlays/cloud/config/product-service-runtime-config.yaml',
        'infra/k8s/overlays/cloud/config/authentication-service-runtime-config.yaml',
        'infra/scripts/gitops/phase15-secrets.ps1',
        'specs/049-regular-purchase-checkout/spec.md',
        'specs/049-regular-purchase-checkout/plan.md',
        'specs/049-regular-purchase-checkout/tasks.md',
        'specs/049-regular-purchase-checkout/validation.md'
    )
    foreach ($file in $requiredFiles) { Assert-File $file }

    Assert-Content 'services/order-service/pom.xml' `
        '<artifactId>spring-cloud-starter-openfeign</artifactId>' 'Order OpenFeign dependency'
    Assert-Content 'services/order-service/pom.xml' `
        '<artifactId>spring-boot-starter-oauth2-client</artifactId>' 'Order OAuth2 dependency'
    foreach ($service in @('cart-service', 'inventory-service')) {
        Assert-Content "services/$service/pom.xml" `
            '<artifactId>kafka-avro-contracts</artifactId>' "$service Avro contract dependency"
        Assert-Content "services/$service/pom.xml" `
            '<artifactId>spring-kafka</artifactId>' "$service Spring Kafka dependency"
    }

    $disabledProperties = @(
        @('services/cart-service/src/main/resources/application.yml',
            'consumer-enabled:\s*\$\{CART_CHECKOUT_RECONCILIATION_CONSUMER_ENABLED:false\}'),
        @('services/inventory-service/src/main/resources/application.yml',
            'api-enabled:\s*\$\{INVENTORY_REGULAR_HOLD_API_ENABLED:false\}'),
        @('services/inventory-service/src/main/resources/application.yml',
            'command-consumer-enabled:\s*\$\{INVENTORY_REGULAR_HOLD_COMMAND_CONSUMER_ENABLED:false\}'),
        @('services/inventory-service/src/main/resources/application.yml',
            'outbox-publisher-enabled:\s*\$\{INVENTORY_REGULAR_HOLD_OUTBOX_PUBLISHER_ENABLED:false\}'),
        @('services/inventory-service/src/main/resources/application.yml',
            'expiry-enabled:\s*\$\{INVENTORY_REGULAR_HOLD_EXPIRY_ENABLED:false\}'),
        @('services/order-service/src/main/resources/application.yml',
            'intake-enabled:\s*\$\{ORDER_REGULAR_PURCHASE_INTAKE_ENABLED:false\}'),
        @('services/order-service/src/main/resources/application.yml',
            'recovery-enabled:\s*\$\{ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED:false\}'),
        @('services/order-service/src/main/resources/application.yml',
            'hold-result-consumer-enabled:\s*\$\{ORDER_REGULAR_HOLD_RESULT_CONSUMER_ENABLED:false\}'),
        @('services/order-service/src/main/resources/application.yml',
            'hold-command-producer-enabled:\s*\$\{ORDER_REGULAR_HOLD_COMMAND_PRODUCER_ENABLED:false\}')
    )
    foreach ($check in $disabledProperties) {
        Assert-Content $check[0] $check[1] 'disabled-by-default regular purchase property'
    }

    Assert-Content 'infra/docker/.env.example' '(?m)^ORDER_CLIENT_ID=order-service$' `
        'Order machine client identifier'
    Assert-Content 'infra/docker/.env.example' '(?m)^ORDER_CLIENT_SECRET=$' `
        'empty Order machine client Secret placeholder'
    Assert-Content 'infra/docker/compose.yml' `
        'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED:\s*\$\{ORDER_REGULAR_PURCHASE_INTAKE_ENABLED:-false\}' `
        'Compose disabled Order intake flag'
    Assert-Content 'infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml' `
        'ORDER_REGULAR_PURCHASE_INTAKE_ENABLED:\s*"false"' 'cloud disabled Order intake flag'
    Assert-Content 'infra/k8s/overlays/cloud/config/cart-service-runtime-config.yaml' `
        'CART_CHECKOUT_RECONCILIATION_CONSUMER_ENABLED:\s*"false"' `
        'cloud disabled Cart reconciliation flag'
    Assert-Content 'infra/k8s/overlays/cloud/config/inventory-service-runtime-config.yaml' `
        'INVENTORY_REGULAR_HOLD_API_ENABLED:\s*"false"' 'cloud disabled Inventory hold API flag'
    Assert-Content 'services/api-gateway/src/main/resources/application.yml' `
        'id: order-api[\s\S]*Path=/api/v1/orders/\*\*[\s\S]*Method=POST' `
        'Gateway POST regular-order route'

    $cartCloudConfig = Get-Content -LiteralPath `
        (Get-RepoPath 'infra/k8s/overlays/cloud/config/cart-service-runtime-config.yaml') -Raw
    if ($cartCloudConfig -match '(?im)^\s*[A-Z0-9_]*(PASSWORD|SECRET|TOKEN)\s*:') {
        throw 'Cart cloud ConfigMap must not contain Secret-valued keys.'
    }

    Assert-Content 'infra/scripts/gitops/phase15-secrets.ps1' `
        '"ORDER_CLIENT_SECRET"' 'Order Secret validation boundary'
    Assert-Content 'infra/scripts/gitops/phase15-secrets.ps1' `
        '"CART_CLIENT_SECRET"' 'Cart Secret validation boundary'
    Assert-Content 'infra/scripts/gitops/phase15-secrets.ps1' `
        'Name\s*=\s*"cart-secrets"' 'Cart Kubernetes Secret boundary'

    Write-Output 'FEATURE_049_STATIC=PASS'
    Write-Output 'Feature 049 runtime boundary: regular purchase intake, hold workers, and Cart reconciliation are disabled.'
    Write-Output 'Validation-only: no Docker, Kubernetes, cloud, database, Kafka, or Secret state was read or changed.'
}

switch ($Scenario) {
    'Static' { Invoke-StaticGate }
    'BuyNowPaid' {
        $gatewayBase = "http://127.0.0.1:$([Environment]::GetEnvironmentVariable('GATEWAY_PORT', 'Process'))"
        if ($gatewayBase -eq 'http://127.0.0.1:') { $gatewayBase = 'http://127.0.0.1:18080' }
        Invoke-BuyNowPaid
    }
    'CartPaid' {
        $gatewayBase = "http://127.0.0.1:$([Environment]::GetEnvironmentVariable('GATEWAY_PORT', 'Process'))"
        if ($gatewayBase -eq 'http://127.0.0.1:') { $gatewayBase = 'http://127.0.0.1:18080' }
        Invoke-CartPaid -SkipBuild:$SkipBuild
    }
    'CartEditedWhilePaying' {
        $gatewayBase = "http://127.0.0.1:$([Environment]::GetEnvironmentVariable('GATEWAY_PORT', 'Process'))"
        if ($gatewayBase -eq 'http://127.0.0.1:') { $gatewayBase = 'http://127.0.0.1:18080' }
        Invoke-CartPaid -EditDuringPayment -SkipBuild:$SkipBuild
    }
    'PriceChanged' {
        $gatewayBase = "http://127.0.0.1:$([Environment]::GetEnvironmentVariable('GATEWAY_PORT', 'Process'))"
        if ($gatewayBase -eq 'http://127.0.0.1:') { $gatewayBase = 'http://127.0.0.1:18080' }
        Invoke-CartPaid -ScenarioMode PriceChanged -SkipBuild:$SkipBuild
    }
    'InsufficientStock' {
        $gatewayBase = "http://127.0.0.1:$([Environment]::GetEnvironmentVariable('GATEWAY_PORT', 'Process'))"
        if ($gatewayBase -eq 'http://127.0.0.1:') { $gatewayBase = 'http://127.0.0.1:18080' }
        Invoke-CartPaid -ScenarioMode InsufficientStock -SkipBuild:$SkipBuild
    }
    'PaymentFailed' {
        Invoke-TestBackedScenario 'PaymentFailed' @('services/order-service', 'services/inventory-service') `
            'RegularHoldRecoverySagaTests,RegularHoldRecoveryIntegrationTests,PaymentFailureTransitionIntegrationTests'
    }
    'HoldExpired' {
        Invoke-TestBackedScenario 'HoldExpired' @('services/order-service', 'services/inventory-service') `
            'RegularHoldRecoverySagaTests,RegularHoldRecoveryIntegrationTests,RegularHoldLoadTests'
    }
    'Replay' {
        Invoke-TestBackedScenario 'Replay' @('services/cart-service', 'services/inventory-service', 'services/order-service') `
            'CartReconciliationPersistenceIntegrationTests,RegularHoldLoadTests,RegularPurchaseReplayLoadTests'
    }
    'Concurrency' {
        Invoke-TestBackedScenario 'Concurrency' @('services/cart-service', 'services/inventory-service', 'services/order-service') `
            'CartReconciliationPersistenceIntegrationTests,MultiItemRegularHoldConcurrencyTests,RegularHoldLoadTests,RegularPurchaseReplayLoadTests'
    }
    'DependencyRestart' {
        Invoke-DependencyRestartScenario
    }
    'LateSuccess' {
        Invoke-TestBackedScenario 'LateSuccess' @('services/payment-service', 'services/order-service') `
            'CheckoutRecoveryIntegrationTests,PurchaseSagaLateSuccessTests,LatePaymentCorrectionIntegrationTests,RegularHoldRecoveryIntegrationTests'
    }
    'FlashSaleRegression' {
        $feature044 = Join-Path $repoRoot 'infra/docker/smoke/feature-044-purchase-saga.ps1'
        if (-not (Test-Path -LiteralPath $feature044 -PathType Leaf)) {
            throw 'Feature 044 regression runner is missing.'
        }
        & pwsh -NoLogo -NoProfile -File $feature044 -Scenario Replay -TimeoutSeconds ([Math]::Min($TimeoutSeconds, 1800)) | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Flash Sale regression scenario failed (exitCode=$LASTEXITCODE)." }
        Write-Output 'FEATURE_049_FLASHSALE_REGRESSION=PASS'
        Write-Output 'Flash Sale regression reused the existing bounded Feature 044 replay gate; diagnostics were redacted.'
    }
    'All' {
        # Aggregate gate is intentionally non-interactive by default. It runs every deterministic
        # source/test/regression check and leaves Docker Checkout/browser and service restart
        # boundaries opt-in so a CI invocation cannot mutate a shared local stack unexpectedly.
        Invoke-StaticGate
        Invoke-TestBackedScenario 'PaymentFailed' @('services/order-service', 'services/inventory-service') `
            'RegularHoldRecoverySagaTests,RegularHoldRecoveryIntegrationTests,PaymentFailureTransitionIntegrationTests'
        Invoke-TestBackedScenario 'HoldExpired' @('services/order-service', 'services/inventory-service') `
            'RegularHoldRecoverySagaTests,RegularHoldRecoveryIntegrationTests,RegularHoldLoadTests'
        Invoke-TestBackedScenario 'Replay' @('services/cart-service', 'services/inventory-service', 'services/order-service') `
            'CartReconciliationPersistenceIntegrationTests,RegularHoldLoadTests,RegularPurchaseReplayLoadTests'
        Invoke-TestBackedScenario 'Concurrency' @('services/cart-service', 'services/inventory-service', 'services/order-service') `
            'CartReconciliationPersistenceIntegrationTests,MultiItemRegularHoldConcurrencyTests,RegularHoldLoadTests,RegularPurchaseReplayLoadTests'
        Invoke-TestBackedScenario 'LateSuccess' @('services/payment-service', 'services/order-service') `
            'CheckoutRecoveryIntegrationTests,PurchaseSagaLateSuccessTests,LatePaymentCorrectionIntegrationTests,RegularHoldRecoveryIntegrationTests'
        $feature044 = Join-Path $repoRoot 'infra/docker/smoke/feature-044-purchase-saga.ps1'
        if (-not (Test-Path -LiteralPath $feature044 -PathType Leaf)) {
            throw 'Feature 044 regression runner is missing.'
        }
        & pwsh -NoLogo -NoProfile -File $feature044 -Scenario Replay -TimeoutSeconds ([Math]::Min($TimeoutSeconds, 1800)) | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Flash Sale regression scenario failed (exitCode=$LASTEXITCODE)." }
        Write-Output 'FEATURE_049_FLASHSALE_REGRESSION=PASS'
        if ($RunInteractive) {
            Invoke-BuyNowPaid
            Invoke-CartPaid -SkipBuild:$SkipBuild
            Invoke-CartPaid -EditDuringPayment -SkipBuild:$SkipBuild
            Invoke-CartPaid -ScenarioMode PriceChanged -SkipBuild:$SkipBuild
            Invoke-CartPaid -ScenarioMode InsufficientStock -SkipBuild:$SkipBuild
            Invoke-DependencyRestartScenario
            Write-Output 'FEATURE_049_INTERACTIVE=PASS'
        } else {
            Write-Output 'FEATURE_049_INTERACTIVE=DEFERRED (use -RunInteractive for Docker/Stripe scenarios)'
            Write-Output 'FEATURE_049_DEPENDENCY_RESTART=DEFERRED (use -AllowDependencyRestart explicitly)'
        }
        Write-Output 'FEATURE_049_LOCAL_AGGREGATE=PASS'
    }
    default {
        throw "Scenario '$Scenario' is not implemented yet; complete its approved Feature 049 task before running it."
    }
}
