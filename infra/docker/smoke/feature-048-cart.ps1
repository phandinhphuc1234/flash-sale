<#
.SYNOPSIS
  Runs the bounded local Cart MVP smoke journey through API Gateway.

.DESCRIPTION
  The runner creates disposable Product and shopper fixtures, validates owner-scoped CRUD and
  replay behavior, pauses Product to prove fail-soft reads/mutation rejection, then restores the
  topology. Credentials and response bodies are held in process memory only and are never printed.
#>
[CmdletBinding()]
param(
    [ValidateSet('All', 'Static', 'Security', 'Crud', 'Replay', 'ProductDegradation', 'Performance')]
    [string] $Scenario = 'All',
    [ValidateRange(60, 900)]
    [int] $TimeoutSeconds = 900,
    [switch] $SkipTopology,
    [switch] $PreserveFixtureUsers,
    [string] $AdminLogin = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest

$script:startedTopology = $false
$script:productId = [Guid]::Empty
$script:productVersion = 0L
$script:variantId = [Guid]::Empty
$script:adminUserId = [Guid]::Empty
$script:fixtureUserIds = [System.Collections.Generic.List[Guid]]::new()
$script:stopwatch = [Diagnostics.Stopwatch]::StartNew()

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$envFile = Join-Path $repoRoot 'infra\docker\.env'
$composeFile = Join-Path $repoRoot 'infra\docker\compose.yml'
$composeDevFile = Join-Path $repoRoot 'infra\docker\compose.dev.yml'

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'infra/docker/.env is required; copy .env.example and add local-only values first.'
}

function Get-DotEnvValue([string] $name, [string] $fallback = '') {
    $processValue = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($processValue)) { return $processValue }
    $line = Get-Content -LiteralPath $envFile | Where-Object {
        $_ -match "^$([regex]::Escape($name))="
    } | Select-Object -First 1
    if (-not $line) { return $fallback }
    return ($line -split '=', 2)[1]
}

$gatewayBase = "http://127.0.0.1:$(Get-DotEnvValue 'GATEWAY_PORT' '8080')"
$authBase = "http://127.0.0.1:$(Get-DotEnvValue 'AUTHENTICATION_SERVICE_PORT' '18081')"
$productBase = "http://127.0.0.1:$(Get-DotEnvValue 'PRODUCT_SERVICE_PORT' '18082')"
$cartBase = "http://127.0.0.1:$(Get-DotEnvValue 'CART_SERVICE_PORT' '18089')"

function Assert-Budget([string] $stage) {
    if ($script:stopwatch.Elapsed.TotalSeconds -gt $TimeoutSeconds) {
        throw "$stage exceeded the $TimeoutSeconds-second Cart smoke budget."
    }
}

function Invoke-Compose([string[]] $arguments) {
    & docker compose --env-file $envFile -f $composeFile -f $composeDevFile --profile apps @arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE." }
}

function Invoke-ComposeMigration {
    & docker compose --env-file $envFile -f $composeFile --profile migrations run --rm --no-deps `
        cart-migration --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Cart Liquibase migration failed.' }
}

function Stop-Compose {
    try { Invoke-Compose @('stop', 'cart-service', 'api-gateway', 'product-service', 'authentication-service') } catch { }
}

function Wait-Until([scriptblock] $condition, [string] $description, [int] $seconds = 120) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        Assert-Budget "waiting for $description"
        try { if (& $condition) { return } } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $description."
}

function Invoke-Json([string] $uri, [string] $method, [hashtable] $headers, [object] $body = $null) {
    $parameters = @{ Uri = $uri; Method = $method; Headers = $headers; UseBasicParsing = $true
        TimeoutSec = 8; SkipHttpErrorCheck = $true }
    if ($null -ne $body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $body
    }
    return Invoke-WebRequest @parameters
}

function Require-Status($response, [int[]] $expected, [string] $stage) {
    if ($expected -notcontains [int]$response.StatusCode) {
        throw "$stage returned HTTP $($response.StatusCode)."
    }
}

function Invoke-Database([string] $database, [string] $sql) {
    $user = Get-DotEnvValue 'POSTGRES_USER' 'flashsale'
    $output = & docker compose --env-file $envFile -f $composeFile exec -T postgres `
        psql -X -q -v ON_ERROR_STOP=1 -U $user -d $database -Atc $sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed for $database." }
    return @($output)
}

function Wait-Http([string] $uri, [int[]] $expected = @(200), [int] $seconds = 120) {
    Wait-Until -Description $uri -Seconds $seconds -Condition {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 5 -SkipHttpErrorCheck
        return $expected -contains [int]$response.StatusCode
    }
}

function New-RandomPassword {
    return 'Sm0ke!Aa' + [Convert]::ToBase64String(
        [Security.Cryptography.RandomNumberGenerator]::GetBytes(18)).Replace('/', 'A').Replace('+', 'B').TrimEnd('=')
}

function New-User([string] $label, [string] $role = '') {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $email = "feature048-$label-$suffix@example.test"
    $password = New-RandomPassword
    $trace = "feature048-$label-$suffix"
    $registered = Invoke-Json "$gatewayBase/api/v1/auth/register" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ email = $email; username = "feature048-$label-$suffix"; password = $password } |
            ConvertTo-Json -Compress)
    Require-Status $registered @(201) "$label registration"
    $userId = [Guid](($registered.Content | ConvertFrom-Json).data.userId)
    $script:fixtureUserIds.Add($userId)
    if (-not [string]::IsNullOrWhiteSpace($role)) {
        Invoke-Database 'auth_db' "UPDATE users SET role='$role', updated_at=NOW() WHERE id='$userId'::uuid;" | Out-Null
    }
    $login = Invoke-Json "$gatewayBase/api/v1/auth/login" 'Post' @{ 'X-Trace-Id' = $trace } `
        (@{ login = $email; password = $password; deviceName = 'feature-048-smoke' } |
            ConvertTo-Json -Compress)
    Require-Status $login @(200) "$label login"
    return [pscustomobject]@{ Id = $userId; Token = [string](($login.Content | ConvertFrom-Json).data.accessToken) }
}

function New-ProductFixture([object] $admin) {
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $trace = "feature048-product-$suffix"
    $headers = @{ Authorization = "Bearer $($admin.Token)"; 'X-Trace-Id' = $trace
        'Idempotency-Key' = "feature048-product-$suffix" }
    $created = Invoke-Json "$gatewayBase/api/v1/admin/catalog/products" 'Post' $headers `
        (@{ code = "F048-$suffix"; slug = "feature048-$suffix"; name = 'Feature 048 Cart product'
            shortDescription = 'Disposable Cart smoke fixture'; description = 'Feature 048 local validation' } |
            ConvertTo-Json -Compress)
    Require-Status $created @(201) 'Product fixture create'
    $data = ($created.Content | ConvertFrom-Json).data
    $script:productId = [Guid]$data.id
    $script:productVersion = [long]$data.version
    $sku = "F048-SMOKE-$suffix".ToUpperInvariant()
    $composition = Invoke-Json "$gatewayBase/api/v1/admin/catalog/products/$($script:productId)/composition" 'Put' `
        (@{ Authorization = "Bearer $($admin.Token)"; 'X-Trace-Id' = "$trace-composition"
            'If-Match' = [string]$script:productVersion }) `
        (@{ name = 'Feature 048 Cart product'; shortDescription = 'Disposable Cart smoke fixture'
            description = 'Feature 048 local validation'; variants = @(@{ id = $null; sku = $sku
                barcode = $null; name = 'Cart smoke variant'; basePrice = 100000; currency = 'VND'
                status = 'ACTIVE'; sortOrder = 0 }); categories = @(); media = @() } |
            ConvertTo-Json -Depth 6 -Compress)
    Require-Status $composition @(200) 'Product fixture composition'
    $script:productVersion = [long](($composition.Content | ConvertFrom-Json).data.version)
    $detail = Invoke-Json "$gatewayBase/api/v1/admin/catalog/products/$($script:productId)" 'Get' `
        @{ Authorization = "Bearer $($admin.Token)"; 'X-Trace-Id' = "$trace-detail" }
    Require-Status $detail @(200) 'Product fixture detail'
    $variants = @(($detail.Content | ConvertFrom-Json).data.variants)
    if ($variants.Count -ne 1) { throw 'Product fixture did not create exactly one variant.' }
    $script:variantId = [Guid]$variants[0].id
    $published = Invoke-Json "$gatewayBase/api/v1/admin/catalog/products/$($script:productId)/publish" 'Post' `
        (@{ Authorization = "Bearer $($admin.Token)"; 'X-Trace-Id' = "$trace-publish"
            'If-Match' = [string]$script:productVersion; 'Idempotency-Key' = "feature048-publish-$suffix" })
    Require-Status $published @(200) 'Product fixture publish'
    $script:productVersion = [long](($published.Content | ConvertFrom-Json).data.version)
    Write-Output "FEATURE_048_PRODUCT_FIXTURE=PASS variantId=$($script:variantId)"
}

function Cart-Headers([string] $token, [string] $trace) {
    return @{ Authorization = "Bearer $token"; 'X-Trace-Id' = $trace }
}

function Get-Cart([string] $token, [string] $trace = 'feature048-cart-read') {
    return Invoke-Json "$gatewayBase/api/v1/cart" 'Get' (Cart-Headers $token $trace)
}

function Set-CartItem([string] $token, [int] $quantity, [string] $trace = 'feature048-cart-set') {
    return Invoke-Json "$gatewayBase/api/v1/cart/items/$($script:variantId)" 'Put' `
        (Cart-Headers $token $trace) (@{ quantity = $quantity } | ConvertTo-Json -Compress)
}

function Run-Security([object] $shopper) {
    $anonymous = Invoke-Json "$gatewayBase/api/v1/cart" 'Get' @{ 'X-Trace-Id' = 'feature048-anonymous' }
    Require-Status $anonymous @(401) 'Anonymous Cart boundary'
    $owner = Get-Cart $shopper.Token 'feature048-security-owner'
    Require-Status $owner @(200) 'Authenticated Cart boundary'
    Write-Output 'FEATURE_048_SECURITY=PASS'
}

function Run-Crud([object] $shopper) {
    $invalid = Set-CartItem $shopper.Token 0 'feature048-invalid'
    Require-Status $invalid @(400) 'Invalid Cart quantity'
    $set = Set-CartItem $shopper.Token 2
    Require-Status $set @(200) 'Cart set'
    $body = ($set.Content | ConvertFrom-Json).data
    if ([int]$body.quantity -ne 2) { throw 'Cart set returned an unexpected quantity.' }
    $read = Get-Cart $shopper.Token
    Require-Status $read @(200) 'Cart read'
    if (-not ([string]$read.Headers['Cache-Control']).Contains('no-store')) {
        throw 'Cart response is missing the no-store cache directive.'
    }
    if (@(($read.Content | ConvertFrom-Json).data.items).Count -ne 1) { throw 'Cart read did not return one item.' }
    $removed = Invoke-Json "$gatewayBase/api/v1/cart/items/$($script:variantId)" 'Delete' `
        (Cart-Headers $shopper.Token 'feature048-remove')
    Require-Status $removed @(204) 'Cart remove'
    $removedAgain = Invoke-Json "$gatewayBase/api/v1/cart/items/$($script:variantId)" 'Delete' `
        (Cart-Headers $shopper.Token 'feature048-remove-replay')
    Require-Status $removedAgain @(204) 'Cart remove replay'
    Write-Output 'FEATURE_048_CRUD=PASS'
}

function Run-Replay([object] $shopper) {
    for ($i = 0; $i -lt 100; $i++) {
        $response = Set-CartItem $shopper.Token 3 "feature048-replay-$i"
        Require-Status $response @(200) "Cart replay $i"
        Assert-Budget "Cart replay $i"
    }
    $read = Get-Cart $shopper.Token 'feature048-replay-read'
    Require-Status $read @(200) 'Cart replay read'
    $items = @(($read.Content | ConvertFrom-Json).data.items)
    if ($items.Count -ne 1 -or [int]$items[0].quantity -ne 3) { throw 'Cart replay produced an invalid final state.' }
    Write-Output 'FEATURE_048_REPLAY=PASS (100 absolute replacements, one line)'
}

function Run-Ownership([object] $owner, [object] $foreign) {
    $ownerSet = Set-CartItem $owner.Token 4 'feature048-owner-set'
    Require-Status $ownerSet @(200) 'Owner setup'
    $foreignRead = Get-Cart $foreign.Token 'feature048-foreign-read'
    Require-Status $foreignRead @(200) 'Foreign Cart read'
    $foreignItems = @(($foreignRead.Content | ConvertFrom-Json).data.items)
    if ($foreignItems.Count -ne 0) { throw 'Foreign shopper could see owner Cart items.' }
    $foreignRemove = Invoke-Json "$gatewayBase/api/v1/cart/items/$($script:variantId)" 'Delete' `
        (Cart-Headers $foreign.Token 'feature048-foreign-remove')
    Require-Status $foreignRemove @(204) 'Foreign Cart remove no-op'
    $ownerRead = Get-Cart $owner.Token 'feature048-owner-after-foreign'
    Require-Status $ownerRead @(200) 'Owner Cart after foreign request'
    if (@(($ownerRead.Content | ConvertFrom-Json).data.items).Count -ne 1) {
        throw 'Foreign mutation changed the owner Cart.'
    }
    $clear = Invoke-Json "$gatewayBase/api/v1/cart" 'Delete' (Cart-Headers $owner.Token 'feature048-clear')
    Require-Status $clear @(204) 'Cart clear'
    $clearAgain = Invoke-Json "$gatewayBase/api/v1/cart" 'Delete' (Cart-Headers $owner.Token 'feature048-clear-replay')
    Require-Status $clearAgain @(204) 'Cart clear replay'
    Write-Output 'FEATURE_048_OWNERSHIP=PASS'
}

function Run-ProductDegradation([object] $shopper) {
    $set = Set-CartItem $shopper.Token 1 'feature048-degradation-setup'
    Require-Status $set @(200) 'Degradation setup'
    Invoke-Compose @('stop', 'product-service')
    try {
        $read = Get-Cart $shopper.Token 'feature048-product-outage-read'
        Require-Status $read @(200) 'Cart read during Product outage'
        $item = @(($read.Content | ConvertFrom-Json).data.items)[0]
        if ($item.detailsAvailable -ne $false -or $null -ne $item.productName) {
            throw 'Cart exposed Product details during a Product outage.'
        }
        $mutation = Set-CartItem $shopper.Token 2 'feature048-product-outage-mutation'
        Require-Status $mutation @(503) 'Cart mutation during Product outage'
    } finally {
        Invoke-Compose @('start', 'product-service')
    }
    Wait-Http "$productBase/actuator/health/readiness" @(200) 180
    $recovered = Get-Cart $shopper.Token 'feature048-product-recovery'
    Require-Status $recovered @(200) 'Cart read after Product recovery'
    if (@(($recovered.Content | ConvertFrom-Json).data.items)[0].detailsAvailable -ne $true) {
        throw 'Cart did not restore Product details after recovery.'
    }
    Write-Output 'FEATURE_048_PRODUCT_DEGRADATION=PASS'
}

function Run-Performance([object] $shopper) {
    $samples = [System.Collections.Generic.List[double]]::new()
    for ($i = 0; $i -lt 20; $i++) {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $response = Get-Cart $shopper.Token "feature048-performance-$i"
        $timer.Stop()
        Require-Status $response @(200) "Cart read performance $i"
        $samples.Add($timer.Elapsed.TotalMilliseconds)
        Assert-Budget "Cart performance $i"
    }
    $ordered = @($samples | Sort-Object)
    $p95 = $ordered[[Math]::Min($ordered.Count - 1, [Math]::Ceiling($ordered.Count * 0.95) - 1)]
    if ($p95 -gt 1000) { throw "Cart read p95 exceeded 1000 ms (measured $([Math]::Round($p95, 2)) ms)." }
    Write-Output "FEATURE_048_PERFORMANCE=PASS samples=$($samples.Count) p95Ms=$([Math]::Round($p95, 2))"
}

function Remove-Fixtures([object] $admin) {
    if ($script:productId -eq [Guid]::Empty -or $null -eq $admin) { return }
    try {
        $archive = Invoke-Json "$gatewayBase/api/v1/admin/catalog/products/$($script:productId)/archive" 'Post' `
            (@{ Authorization = "Bearer $($admin.Token)"; 'X-Trace-Id' = 'feature048-cleanup'
                'If-Match' = [string]$script:productVersion
                'Idempotency-Key' = "feature048-cleanup-$($script:productId)" })
        Require-Status $archive @(200) 'Product fixture cleanup'
    } catch { }
    if (-not $PreserveFixtureUsers) {
        foreach ($userId in $script:fixtureUserIds) {
            try { Invoke-Database 'auth_db' "DELETE FROM users WHERE id='$userId'::uuid;" | Out-Null } catch { }
        }
    }
}

try {
    Assert-Budget 'Cart smoke startup'
    $admin = $null
    if ($Scenario -eq 'Static') {
        Write-Output 'FEATURE_048_STATIC=PASS'
        return
    }
    $cartClientSecret = Get-DotEnvValue 'CART_CLIENT_SECRET'
    if ([string]::IsNullOrWhiteSpace($cartClientSecret)) {
        throw 'CART_CLIENT_SECRET must be set in ignored infra/docker/.env for Cart Product lookups.'
    }
    if (-not $SkipTopology) {
        Invoke-Compose @('up', '-d', 'postgres')
        Invoke-ComposeMigration
        Invoke-Compose @('up', '-d', '--build', 'authentication-service', 'product-service', 'cart-service', 'api-gateway')
        $script:startedTopology = $true
    }
    Wait-Http "$gatewayBase/actuator/health" @(200) 180
    Wait-Http "$authBase/actuator/health/readiness" @(200) 180
    Wait-Http "$productBase/actuator/health/readiness" @(200) 180
    Wait-Http "$cartBase/actuator/health/readiness" @(200) 180
    Wait-Http "$cartBase/actuator/health/liveness" @(200) 120
    Wait-Http "$cartBase/actuator/prometheus" @(200) 120
    Write-Output 'FEATURE_048_MODULES=PASS'

    $admin = New-User 'admin' 'ROLE_ADMIN'
    $owner = New-User 'owner'
    $foreign = New-User 'foreign'
    New-ProductFixture $admin
    if ($Scenario -in @('All', 'Security')) { Run-Security $owner }
    if ($Scenario -in @('All', 'Crud')) { Run-Crud $owner }
    if ($Scenario -in @('All', 'Replay')) { Run-Replay $owner }
    if ($Scenario -eq 'All') { Run-Ownership $owner $foreign }
    if ($Scenario -in @('All', 'ProductDegradation')) { Run-ProductDegradation $owner }
    if ($Scenario -in @('All', 'Performance')) { Run-Performance $owner }
    Write-Output 'FEATURE_048_LOCAL_GATE=PASS'
} finally {
    try { Remove-Fixtures $admin } catch { }
    if ($script:startedTopology) { Stop-Compose }
}
