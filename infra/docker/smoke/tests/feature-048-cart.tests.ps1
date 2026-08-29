<#
.SYNOPSIS
  Static contract checks for the authenticated Cart local smoke runner.

.DESCRIPTION
  Reads tracked Cart/Gateway/Compose assets only. It never calls Docker, Kubernetes, Product, or
  Authentication and never reads a real secret value.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$runnerPath = Join-Path $repoRoot 'infra\docker\smoke\feature-048-cart.ps1'
$composePath = Join-Path $repoRoot 'infra\docker\compose.yml'
$envExamplePath = Join-Path $repoRoot 'infra\docker\.env.example'
$gatewayConfigPath = Join-Path $repoRoot 'services\api-gateway\src\main\resources\application.yml'
$gatewaySecurityPath = Join-Path $repoRoot 'services\api-gateway\src\main\java\com\philia\flashsale\gateway\security\GatewaySecurityConfiguration.java'
$cartOpenApiPath = Join-Path $repoRoot 'services\cart-service\src\main\java\com\philia\flashsale\cart\configuration\CartOpenApiConfiguration.java'

function Assert-True([bool] $condition, [string] $message) {
    if (-not $condition) { throw "Feature 048 static test failed: $message" }
}

foreach ($path in @($runnerPath, $composePath, $envExamplePath, $gatewayConfigPath,
        $gatewaySecurityPath, $cartOpenApiPath)) {
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "missing required file $path"
}

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$parseErrors) | Out-Null
Assert-True ($parseErrors.Count -eq 0) 'Cart smoke runner has PowerShell parse errors'

$runner = Get-Content -LiteralPath $runnerPath -Raw
$compose = Get-Content -LiteralPath $composePath -Raw
$envExample = Get-Content -LiteralPath $envExamplePath -Raw
$gateway = Get-Content -LiteralPath $gatewayConfigPath -Raw
$security = Get-Content -LiteralPath $gatewaySecurityPath -Raw
$openApi = Get-Content -LiteralPath $cartOpenApiPath -Raw

foreach ($marker in @(
        '[ValidateSet(''All'', ''Static'', ''Security'', ''Crud'', ''Replay'', ''ProductDegradation'', ''Performance'')]'
        '[ValidateRange(60, 900)]'
        'FEATURE_048_SECURITY=PASS'
        'FEATURE_048_REPLAY=PASS'
        'FEATURE_048_PRODUCT_DEGRADATION=PASS'
        'FEATURE_048_PERFORMANCE=PASS'
        'Cache-Control'
        'detailsAvailable'
        'Stop-Compose'
        'finally')) {
    Assert-True ($runner.Contains($marker)) "runner marker is missing: $marker"
}

Assert-True ($runner -match 'Authorization|accessToken') 'runner must exercise authenticated requests'
Assert-True ($runner -match 'CART_CLIENT_SECRET') 'runner must validate the Cart client secret by name'
Assert-True ($runner -notmatch '(?i)Bearer\s+[A-Za-z0-9._-]{12,}') 'runner contains a credential-like literal'
Assert-True ($runner -notmatch '(?i)CART_CLIENT_SECRET\s*=\s*[''"''][^''"'']+[''"'']') 'runner embeds a secret'
Assert-True ($runner -match 'TimeoutSec|TimeoutSeconds') 'runner must use bounded HTTP/process timeouts'
Assert-True ($runner -match 'Remove-Item.+Fixture|archive|cleanup') 'runner must clean up its fixture'

foreach ($marker in @('id: cart-api', 'Path=/api/v1/cart/**', 'id: openapi-cart-service',
        'Path=/openapi/cart-service', 'CART_SERVICE_URL')) {
    Assert-True ($gateway.Contains($marker)) "Gateway marker is missing: $marker"
}
Assert-True ($security.Contains('.pathMatchers("/api/v1/cart/**").authenticated()')) 'Cart route is not authenticated at Gateway'
Assert-True ($gateway -notmatch '/internal/v1/catalog/variants/display-details') 'Product internal path leaked into Gateway routes'
Assert-True ($openApi.Contains('Flash Sale Cart API')) 'Cart OpenAPI title is missing'
Assert-True ($compose.Contains('cart-migration:')) 'Cart migration service is missing'
Assert-True ($compose.Contains('jdbc:postgresql://postgres:5432/cart_db')) 'Cart database URL is missing'
Assert-True ($compose.Contains('SPRING_LIQUIBASE_ENABLED: "true"')) 'Cart migration does not enable Liquibase'
Assert-True ($compose.Contains('CART_CLIENT_SECRET: ${CART_CLIENT_SECRET:-}')) 'Cart client secret boundary is missing'
Assert-True ($envExample -match '(?m)^CART_CLIENT_SECRET=\s*$') 'env example must keep Cart secret empty'

Write-Output 'PHASE_048_STATIC=PASS'
Write-Output 'Gateway route/security, Compose migration/runtime, OpenAPI, and smoke guardrails verified.'
Write-Output 'No Docker, network, or Secret state was read or changed.'
