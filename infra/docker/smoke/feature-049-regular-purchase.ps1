<#
.SYNOPSIS
  Runs bounded validation scenarios for Feature 049 regular purchase checkout.

.DESCRIPTION
  Phase 1 implements only the Static scenario. It verifies dependency and runtime-configuration
  scaffolding while every new regular-purchase entry point, Kafka consumer, producer, publisher,
  and recovery worker remains disabled. Later approved tasks fill the remaining scenarios into
  this same runner. The script never reads the ignored infra/docker/.env file or Secret values.
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
        'FlashSaleRegression',
        'All'
    )]
    [string] $Scenario = 'Static',
    [ValidateRange(60, 3600)]
    [int] $TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

function Assert-Budget([string] $Stage) {
    if ((Get-Date) -gt $deadline) {
        throw "$Stage exceeded the Feature 049 $TimeoutSeconds-second execution budget."
    }
}

function Get-RepoPath([string] $RelativePath) {
    return Join-Path $repoRoot ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
}

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

if ($Scenario -ne 'Static') {
    throw "Scenario '$Scenario' is intentionally unavailable in Phase 1. Complete its approved Feature 049 implementation task before running it."
}

Invoke-StaticGate
