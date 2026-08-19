[CmdletBinding()]
param(
    [switch] $RunFailureMatrix,
    [switch] $RunK6,
    [switch] $RunStripeCli,
    [switch] $SkipTopology,
    [switch] $PreserveFixtureUsers,
    [Guid] $PaymentId = [Guid]::Empty,
    [Guid] $OrderId = [Guid]::Empty,
    [string] $OwnerToken = [Environment]::GetEnvironmentVariable('PAYMENT_SMOKE_OWNER_TOKEN'),
    [string] $ForeignToken = [Environment]::GetEnvironmentVariable('PAYMENT_SMOKE_FOREIGN_TOKEN')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$envFile = Join-Path $repoRoot 'infra\docker\.env'
$composeFile = Join-Path $repoRoot 'infra\docker\compose.yml'
$composeDevFile = Join-Path $repoRoot 'infra\docker\compose.dev.yml'
$topicBootstrapScript = Join-Path $repoRoot 'infra\docker\kafka\init-payment-topics.sh'
$schemaBootstrapScript = Join-Path $repoRoot 'infra\docker\schema-registry\register-payment-schemas.ps1'
$k6Script = Join-Path $repoRoot 'load-tests\payment-service\feature-021-payment.js'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'infra/docker/.env is required; add local-only values there and never commit the file.'
}

function Get-ProcessValue([string] $Name, [string] $Fallback) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $Fallback }
    return $value
}

$gatewayBase = "http://127.0.0.1:$(Get-ProcessValue 'GATEWAY_PORT' '18080')"
$paymentBase = "http://127.0.0.1:$(Get-ProcessValue 'PAYMENT_SERVICE_PORT' '18086')"
$schemaRegistryBase = "http://127.0.0.1:$(Get-ProcessValue 'SCHEMA_REGISTRY_HOST_PORT' '8081')"
$kafkaBootstrap = "127.0.0.1:$(Get-ProcessValue 'KAFKA_HOST_PORT' '29092')"

function Invoke-Compose([string[]] $Arguments) {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile apps @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Get-ServiceContainer([string] $Service) {
    $container = & docker compose --env-file $envFile -f $composeFile ps -q $Service
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($container)) {
        throw "$Service container is not running."
    }
    return [string]$container
}

function Invoke-Database([string] $Database, [string] $Sql) {
    $container = Get-ServiceContainer 'postgres'
    $output = & docker exec $container sh -c 'psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh $Database $Sql
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

function Wait-Http([string] $Uri, [int[]] $Expected = @(200), [int] $Seconds = 120) {
    Wait-Until -Description $Uri -Seconds $Seconds -Condition {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 4 -SkipHttpErrorCheck
        return $Expected -contains [int]$response.StatusCode
    }
}

function Invoke-Json([string] $Uri, [string] $Method, [hashtable] $Headers, [object] $Body = $null) {
    $parameters = @{
        Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body
    }
    return Invoke-WebRequest @parameters
}

function Require-Status($Response, [int[]] $Expected, [string] $Step) {
    if ($Expected -notcontains [int]$Response.StatusCode) {
        throw "$Step returned HTTP $($Response.StatusCode)."
    }
}

function Invoke-OwnerQuery([string] $Token, [Guid] $Id) {
    return Invoke-Json "$gatewayBase/api/v1/payments/$Id" 'Get' `
        @{ Authorization = "Bearer $Token"; 'X-Trace-Id' = 'feature021-smoke-owner' }
}

function Invoke-TopicBootstrap {
    $kafkaContainer = Get-ServiceContainer 'kafka'
    $containerScript = '/tmp/feature-021-init-payment-topics.sh'
    $normalizedScript = Join-Path ([IO.Path]::GetTempPath()) `
        ('feature-021-payment-topics-' + [Guid]::NewGuid().ToString('N') + '.sh')
    try {
        $scriptText = [IO.File]::ReadAllText($topicBootstrapScript).Replace("`r`n", "`n").Replace("`r", "`n")
        [IO.File]::WriteAllText($normalizedScript, $scriptText, [Text.UTF8Encoding]::new($false))
        & docker cp $normalizedScript "${kafkaContainer}:$containerScript"
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy the Payment topic bootstrap.' }
        & docker exec $kafkaContainer bash $containerScript
        if ($LASTEXITCODE -ne 0) { throw 'Payment topic provisioning failed.' }
    } finally {
        if (Test-Path -LiteralPath $normalizedScript) { Remove-Item -LiteralPath $normalizedScript -Force }
    }
}

function Invoke-Topology {
    Invoke-Compose @('up', '-d', 'postgres', 'redis', 'kafka')
    Invoke-TopicBootstrap
    Invoke-Compose @('up', '-d', 'schema-registry')
    Wait-Http "$schemaRegistryBase/subjects" @(200) 180
    & $schemaBootstrapScript -SchemaRegistryUrl $schemaRegistryBase
    if ($LASTEXITCODE -ne 0) { throw 'Payment Schema Registry provisioning failed.' }

    Invoke-Compose @('build', 'payment-service')
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile migrations `
        run --rm --no-deps payment-migration --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Payment Liquibase migration failed.' }
    Invoke-Compose @('up', '-d', 'authentication-service')
    # Gateway owns both Payment routing and the first JWT boundary; rebuild it so a stale
    # local image cannot turn an approved route into deny-by-default HTTP 403.
    Invoke-Compose @('up', '-d', '--build', 'api-gateway')
    Invoke-Compose @('up', '-d', '--force-recreate', 'payment-service')
    Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
    Wait-Http "$gatewayBase/actuator/health" @(200) 180
}

function Assert-PaymentEnvironment {
    $container = Get-ServiceContainer 'payment-service'
    & docker exec $container sh -c '
      failed=""
      for name in STRIPE_SECRET_KEY STRIPE_PUBLISHABLE_KEY STRIPE_WEBHOOK_SECRET; do
        eval "value=\${$name:-}"
        [ -n "$value" ] || failed="$failed $name"
      done
      case "${STRIPE_SECRET_KEY:-}" in sk_test_*) ;; *) failed="$failed STRIPE_SECRET_KEY(test-mode)";; esac
      case "${STRIPE_PUBLISHABLE_KEY:-}" in pk_test_*) ;; *) failed="$failed STRIPE_PUBLISHABLE_KEY(test-mode)";; esac
      case "${STRIPE_WEBHOOK_SECRET:-}" in whsec_*) ;; *) failed="$failed STRIPE_WEBHOOK_SECRET";; esac
      for name in PAYMENT_ACCEPTANCE_ENABLED PAYMENT_CHECKOUT_ENABLED PAYMENT_WEBHOOK_PROCESSING_ENABLED PAYMENT_CONSUMER_ENABLED PAYMENT_OUTBOX_PUBLISHER_ENABLED PAYMENT_RECOVERY_ENABLED STRIPE_ENABLED; do
        eval "value=\${$name:-false}"
        [ "$value" = true ] || failed="$failed $name=true"
      done
      [ -z "$failed" ] || { echo "Missing or invalid variable names:$failed" >&2; exit 1; }
    '
    if ($LASTEXITCODE -ne 0) {
        throw 'Payment runtime environment is incomplete; only variable names were inspected.'
    }
}

function New-Shopper([string] $Label) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $password = 'Sm0ke!Aa' + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
    $email = "feature021-$Label-$suffix@example.test"
    $trace = "feature021-$Label-$suffix"
    $register = Invoke-Json "$gatewayBase/api/v1/auth/register" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ email = $email; username = "feature021-$Label-$suffix"; password = $password } | ConvertTo-Json -Compress)
    Require-Status $register @(201) "$Label registration"
    $userId = [Guid](($register.Content | ConvertFrom-Json).data.userId)
    $login = Invoke-Json "$gatewayBase/api/v1/auth/login" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ login = $email; password = $password; deviceName = 'feature-021-smoke' } | ConvertTo-Json -Compress)
    Require-Status $login @(200) "$Label login"
    return [pscustomobject]@{
        Id = $userId
        Token = [string](($login.Content | ConvertFrom-Json).data.accessToken)
    }
}

function Publish-PaymentFixture([Guid] $FixtureOrderId, [Guid] $OwnerId) {
    $output = & (Join-Path $repoRoot 'mvnw.cmd') --batch-mode --no-transfer-progress -q `
        -pl services/payment-service -am '-Dtest=PaymentRequestedSmokeFixturePublisherTests' `
        '-Dsurefire.failIfNoSpecifiedTests=false' '-Dpayment.smoke.fixture=true' `
        "-Dpayment.smoke.order-id=$FixtureOrderId" "-Dpayment.smoke.user-id=$OwnerId" `
        "-Dpayment.kafka.bootstrap=$kafkaBootstrap" "-Dpayment.schema-registry.url=$schemaRegistryBase" test 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PaymentRequested fixture publication failed: $($output | Select-Object -Last 20 | Out-String)"
    }
    Wait-Until -Description 'PaymentRequested durable acceptance' -Seconds 120 -Condition {
        [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payments WHERE order_id='$FixtureOrderId'::uuid;" |
            Select-Object -First 1) -eq 1
    }
    return [Guid](Invoke-Database 'payment_db' "SELECT id FROM payments WHERE order_id='$FixtureOrderId'::uuid;" |
        Select-Object -First 1)
}

function Assert-OwnerAndForeignQueries {
    $owner = Invoke-OwnerQuery $OwnerToken $PaymentId
    Require-Status $owner @(200) 'owner payment query'
    $foreign = Invoke-OwnerQuery $ForeignToken $PaymentId
    Require-Status $foreign @(404) 'foreign payment query'
    $unknown = Invoke-OwnerQuery $ForeignToken ([Guid]::NewGuid())
    Require-Status $unknown @(404) 'unknown payment query'
    $foreignCode = [string](($foreign.Content | ConvertFrom-Json).errorCode)
    $unknownCode = [string](($unknown.Content | ConvertFrom-Json).errorCode)
    if ($foreignCode -ne $unknownCode -or $foreignCode -ne 'PAYMENT_NOT_FOUND') {
        throw 'Foreign and absent Payment identities are enumerable through different errors.'
    }
}

function Assert-CommandAndDurability {
    $payments = [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payments WHERE id='$PaymentId'::uuid;" |
        Select-Object -First 1)
    $inbox = [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payment_command_inbox WHERE order_id='$OrderId'::uuid;" |
        Select-Object -First 1)
    if ($payments -ne 1 -or $inbox -ne 1) {
        throw "Expected one durable Payment and command receipt; payments=$payments inbox=$inbox."
    }
}

function Send-ContainerSignedWebhook([Guid] $AttemptId, [string] $ProviderSessionId) {
    $container = Get-ServiceContainer 'payment-service'
    & docker exec $container sh -c '
      payment="$1"; attempt="$2"; order="$3"; session="$4"
      timestamp=$(date +%s)
      event_id="evt_feature021_$(printf %s "$payment" | tr -d -)_$timestamp"
      payload=$(printf "{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"created\":%s,\"livemode\":false,\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"%s\",\"object\":\"checkout.session\",\"status\":\"open\",\"payment_status\":\"unpaid\",\"metadata\":{\"paymentId\":\"%s\",\"attemptId\":\"%s\",\"orderId\":\"%s\"}}}}" "$event_id" "$STRIPE_API_VERSION" "$timestamp" "$session" "$payment" "$attempt" "$order")
      digest=$(printf "%s" "$timestamp.$payload" | openssl dgst -sha256 -hmac "$STRIPE_WEBHOOK_SECRET" -hex | awk "{print \$NF}")
      status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Stripe-Signature: t=$timestamp,v1=$digest" --data-binary "$payload" http://api-gateway:8080/webhooks/v1/payments/stripe)
      [ "$status" = 204 ] || { echo "Webhook returned HTTP $status" >&2; exit 1; }
    ' sh $PaymentId $AttemptId $OrderId $ProviderSessionId
    if ($LASTEXITCODE -ne 0) { throw 'Container-signed Stripe webhook was rejected.' }
}

function Assert-CheckoutAndWebhook {
    $key = 'feature021-smoke-' + [Guid]::NewGuid().ToString('N')
    $script:checkoutReplayKey = $key
    $headers = @{
        Authorization = "Bearer $OwnerToken"; 'Idempotency-Key' = $key
        'X-Trace-Id' = 'feature021-smoke-checkout'
    }
    $first = Invoke-Json "$gatewayBase/api/v1/payments/$PaymentId/checkout-sessions" 'Post' $headers
    Require-Status $first @(200, 201, 202) 'checkout create'
    $replay = Invoke-Json "$gatewayBase/api/v1/payments/$PaymentId/checkout-sessions" 'Post' $headers
    Require-Status $replay @(200, 201, 202) 'checkout replay'

    $attempt = [string](Invoke-Database 'payment_db' `
        "SELECT id || '|' || provider_session_id FROM payment_attempts WHERE payment_id='$PaymentId'::uuid ORDER BY attempt_number DESC LIMIT 1;" |
        Select-Object -First 1)
    $parts = $attempt.Split('|', 2)
    if ($parts.Length -ne 2 -or [string]::IsNullOrWhiteSpace($parts[1])) {
        throw 'Stripe test-mode Checkout did not persist a provider session identity.'
    }
    Send-ContainerSignedWebhook ([Guid]$parts[0]) $parts[1]
    Wait-Until -Description 'durable Stripe webhook receipt' -Seconds 60 -Condition {
        [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payment_provider_event_receipts WHERE payment_id='$PaymentId'::uuid;" |
            Select-Object -First 1) -ge 1
    }
}

function Invoke-FailureMatrix {
    Invoke-Compose @('restart', 'payment-service')
    Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
    Require-Status (Invoke-OwnerQuery $OwnerToken $PaymentId) @(200) 'process restart owner query'

    Invoke-Compose @('stop', 'redis')
    try {
        Wait-Http "$paymentBase/actuator/health/readiness" @(200) 30
        Require-Status (Invoke-OwnerQuery $OwnerToken $PaymentId) @(200) 'Redis outage owner query'
    } finally { Invoke-Compose @('start', 'redis') }

    Invoke-Compose @('stop', 'kafka')
    try {
        Wait-Http "$paymentBase/actuator/health/readiness" @(200) 30
        Require-Status (Invoke-OwnerQuery $OwnerToken $PaymentId) @(200) 'Kafka outage owner query'
    } finally { Invoke-Compose @('start', 'kafka') }
    Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180

    Invoke-Compose @('stop', 'postgres')
    try { Wait-Http "$paymentBase/actuator/health/readiness" @(503) 45 } finally {
        Invoke-Compose @('start', 'postgres')
    }
    Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
    Require-Status (Invoke-OwnerQuery $OwnerToken $PaymentId) @(200) 'PostgreSQL recovery owner query'

    $attempts = [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payment_attempts WHERE payment_id='$PaymentId'::uuid;" |
        Select-Object -First 1)
    $active = [int](Invoke-Database 'payment_db' "SELECT COUNT(*) FROM payment_attempts WHERE payment_id='$PaymentId'::uuid AND status IN ('CREATING','OPEN','PROCESSING','UNKNOWN');" |
        Select-Object -First 1)
    if ($active -gt 1 -or $attempts -gt 3) {
        throw "Recovery created invalid attempt identity; attempts=$attempts active=$active."
    }
}

function Invoke-K6Profiles {
    $names = @('PAYMENT_K6_BASE_URL', 'PAYMENT_K6_PAYMENT_ID', 'PAYMENT_K6_OWNER_TOKEN',
        'PAYMENT_K6_FOREIGN_TOKEN', 'PAYMENT_K6_TARGET_VUS', 'PAYMENT_K6_WARMUP',
        'PAYMENT_K6_STAGE_DURATION', 'PAYMENT_K6_OWNER_P95_MS', 'PAYMENT_K6_CHECKOUT_P95_MS',
        'PAYMENT_K6_SCENARIO', 'PAYMENT_K6_CHECKOUT_KEY')
    $previous = @{}
    foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    try {
        $env:PAYMENT_K6_BASE_URL = $gatewayBase
        $env:PAYMENT_K6_PAYMENT_ID = [string]$PaymentId
        $env:PAYMENT_K6_OWNER_TOKEN = $OwnerToken
        $env:PAYMENT_K6_FOREIGN_TOKEN = $ForeignToken
        $env:PAYMENT_K6_TARGET_VUS = '20'
        $env:PAYMENT_K6_WARMUP = '15s'
        $env:PAYMENT_K6_STAGE_DURATION = '10s'
        $env:PAYMENT_K6_OWNER_P95_MS = '200'
        $env:PAYMENT_K6_CHECKOUT_P95_MS = '150'

        $env:PAYMENT_K6_SCENARIO = 'owner-query'
        & k6 run $k6Script
        if ($LASTEXITCODE -ne 0) { throw 'Owner-query k6 profile failed.' }

        $env:PAYMENT_K6_SCENARIO = 'checkout'
        # Stripe retrieval is deliberately included in this black-box profile; the 150 ms
        # service-local budget is verified separately with the deterministic provider suite.
        $env:PAYMENT_K6_TARGET_VUS = '5'
        $env:PAYMENT_K6_CHECKOUT_P95_MS = '1500'
        $env:PAYMENT_K6_CHECKOUT_KEY = $script:checkoutReplayKey
        & k6 run $k6Script
        if ($LASTEXITCODE -ne 0) { throw 'Checkout replay k6 profile failed.' }
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
}

function Invoke-ConcurrentIdentityProfile {
    $output = & (Join-Path $repoRoot 'mvnw.cmd') --batch-mode --no-transfer-progress -q `
        -pl services/payment-service -am '-Dtest=CheckoutAttemptConcurrencyIntegrationTests,CheckoutReplayConcurrencyIntegrationTests' `
        '-Dsurefire.failIfNoSpecifiedTests=false' test 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "100-concurrent identity profile failed: $($output | Select-Object -Last 20 | Out-String)"
    }
    Write-Output 'FEATURE_021_CONCURRENT_IDENTITIES=PASS count=100'
}

function Start-StripeForwarder {
    $command = Get-Command stripe -ErrorAction Stop
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $tempDirectory = Join-Path $tempRoot ("feature-021-stripe-" + [Guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($tempDirectory) | Out-Null
    $stdout = Join-Path $tempDirectory 'stdout.log'
    $stderr = Join-Path $tempDirectory 'stderr.log'
    $process = Start-Process -FilePath $command.Source -ArgumentList @(
        'listen', '--latest', '--events', 'checkout.session.completed', '--forward-to',
        "$gatewayBase/webhooks/v1/payments/stripe") -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
    $secret = $null
    $deadline = (Get-Date).AddSeconds(45)
    do {
        foreach ($path in @($stdout, $stderr)) {
            if (Test-Path -LiteralPath $path) {
                $content = Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
                if ($content -match '(whsec_[A-Za-z0-9]+)') { $secret = $Matches[1]; break }
            }
        }
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 250
    } while (-not $secret -and (Get-Date) -lt $deadline)
    if (-not $secret) {
        if (-not $process.HasExited) { $process.Kill($true) }
        Remove-Item -LiteralPath $tempDirectory -Recurse -Force -ErrorAction SilentlyContinue
        throw 'Stripe CLI did not expose its ephemeral signing secret within 45 seconds.'
    }
    return [pscustomobject]@{
        Process = $process
        Secret = $secret
        TempDirectory = $tempDirectory
        OutputPaths = @($stdout, $stderr)
    }
}

function Invoke-StripeCliEndToEnd {
    $listener = Start-StripeForwarder
    $previousWebhook = [Environment]::GetEnvironmentVariable('STRIPE_WEBHOOK_SECRET', 'Process')
    try {
        $env:STRIPE_WEBHOOK_SECRET = $listener.Secret
        Invoke-Compose @('up', '-d', '--force-recreate', 'payment-service')
        Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
        $before = [int](Invoke-Database 'payment_db' 'SELECT COUNT(*) FROM payment_provider_event_receipts;' |
            Select-Object -First 1)
        $attemptId = [string](Invoke-Database 'payment_db' `
            "SELECT id FROM payment_attempts WHERE payment_id='$PaymentId'::uuid ORDER BY attempt_number DESC LIMIT 1;" |
            Select-Object -First 1)
        & stripe trigger checkout.session.completed --api-version 2026-07-29.dahlia `
            --override "checkout_session:metadata[paymentId]=$PaymentId" `
            --override "checkout_session:metadata[attemptId]=$attemptId" `
            --override "checkout_session:metadata[orderId]=$OrderId"
        if ($LASTEXITCODE -ne 0) { throw 'Stripe CLI checkout.session.completed trigger failed.' }
        Wait-Until -Description 'Stripe CLI event receipt through Gateway' -Seconds 90 -Condition {
            [int](Invoke-Database 'payment_db' 'SELECT COUNT(*) FROM payment_provider_event_receipts;' |
                Select-Object -First 1) -gt $before
        }
    } catch {
        $diagnostic = foreach ($path in $listener.OutputPaths) {
            if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Tail 20 }
        }
        $safeDiagnostic = ($diagnostic -join [Environment]::NewLine) `
            -replace 'whsec_[A-Za-z0-9]+', '[REDACTED_WEBHOOK_SECRET]' `
            -replace 'sk_test_[A-Za-z0-9]+', '[REDACTED_SECRET_KEY]' `
            -replace 'pk_test_[A-Za-z0-9]+', '[REDACTED_PUBLISHABLE_KEY]'
        if (-not [string]::IsNullOrWhiteSpace($safeDiagnostic)) {
            Write-Warning "Stripe CLI forwarding diagnostics:`n$safeDiagnostic"
        }
        throw
    } finally {
        if (-not $listener.Process.HasExited) { $listener.Process.Kill($true) }
        $listener.Process.Dispose()
        if ([string]::IsNullOrWhiteSpace($previousWebhook)) {
            Remove-Item Env:STRIPE_WEBHOOK_SECRET -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable('STRIPE_WEBHOOK_SECRET', $previousWebhook, 'Process')
        }
        $resolvedTemp = [IO.Path]::GetFullPath($listener.TempDirectory)
        if ($resolvedTemp.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),
                [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedTemp -Recurse -Force -ErrorAction SilentlyContinue
        }
        Invoke-Compose @('up', '-d', '--force-recreate', 'payment-service')
        Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
    }
}

$runtimeFlags = @('PAYMENT_ACCEPTANCE_ENABLED', 'PAYMENT_CHECKOUT_ENABLED',
    'PAYMENT_WEBHOOK_PROCESSING_ENABLED', 'PAYMENT_CONSUMER_ENABLED',
    'PAYMENT_OUTBOX_PUBLISHER_ENABLED', 'PAYMENT_RECOVERY_ENABLED', 'STRIPE_ENABLED')
$previousRuntimeFlags = @{}
foreach ($name in $runtimeFlags) {
    $previousRuntimeFlags[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, 'true', 'Process')
}

$fixtureUserIds = [Collections.Generic.List[Guid]]::new()
$script:checkoutReplayKey = $null
try {
    if ($SkipTopology) {
        Write-Output 'SKIP topology bootstrap (using the already-running local stack)'
        # Recreate Payment so the smoke-only Process-scoped feature flags above take
        # effect even when the rest of the already-running topology is reused.
        Invoke-Compose @('up', '-d', '--force-recreate', 'payment-service')
        Wait-Http "$paymentBase/actuator/health/readiness" @(200) 180
        Wait-Http "$gatewayBase/actuator/health" @(200) 180
    } else { Invoke-Topology }
    Assert-PaymentEnvironment

    if ([string]::IsNullOrWhiteSpace($OwnerToken)) {
        $owner = New-Shopper 'owner'
        $OwnerToken = $owner.Token
        $fixtureUserIds.Add($owner.Id)
    } elseif ($PaymentId -eq [Guid]::Empty -or $OrderId -eq [Guid]::Empty) {
        throw 'Externally supplied owner tokens require an explicit accepted PaymentId/OrderId fixture.'
    }
    if ([string]::IsNullOrWhiteSpace($ForeignToken)) {
        $foreign = New-Shopper 'foreign'
        $ForeignToken = $foreign.Token
        $fixtureUserIds.Add($foreign.Id)
    }
    if ($PaymentId -eq [Guid]::Empty -or $OrderId -eq [Guid]::Empty) {
        $OrderId = [Guid]::NewGuid()
        $PaymentId = Publish-PaymentFixture $OrderId $owner.Id
    }

    Assert-OwnerAndForeignQueries
    Assert-CommandAndDurability
    Assert-CheckoutAndWebhook
    Write-Output 'FEATURE_021_SMOKE=PASS'
    if ($RunFailureMatrix) {
        Invoke-FailureMatrix
        Write-Output 'FEATURE_021_FAILURE_MATRIX=PASS'
    }
    if ($RunK6) {
        Invoke-K6Profiles
        Invoke-ConcurrentIdentityProfile
    }
    if ($RunStripeCli) {
        Invoke-StripeCliEndToEnd
        Write-Output 'FEATURE_021_STRIPE_CLI=PASS'
    }
} finally {
    foreach ($name in $runtimeFlags) {
        if ([string]::IsNullOrWhiteSpace($previousRuntimeFlags[$name])) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousRuntimeFlags[$name], 'Process')
        }
    }
    try {
        Invoke-Compose @('start', 'postgres', 'redis', 'kafka', 'schema-registry')
        # Restore the caller/.env feature flags instead of leaving smoke-only flags behind.
        Invoke-Compose @('up', '-d', '--force-recreate', 'payment-service')
    } catch { }
    if (-not $PreserveFixtureUsers) {
        foreach ($userId in $fixtureUserIds) {
            try { Invoke-Database 'auth_db' "DELETE FROM users WHERE id='$userId'::uuid;" | Out-Null } catch { }
        }
    }
}
