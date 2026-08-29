[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path

function Fail([string]$Message) {
    throw "API documentation verification failed: $Message"
}

function Read-RepositoryFile([string]$RelativePath) {
    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Fail "missing file '$RelativePath'"
    }
    return Get-Content -LiteralPath $path -Raw
}

function Assert-Contains(
    [string]$Content,
    [string]$Expected,
    [string]$Description
) {
    if (-not $Content.Contains($Expected)) {
        Fail "$Description is missing '$Expected'"
    }
}

$catalogPath = Join-Path $repoRoot "docs\api\README.md"
$catalogLines = Get-Content -LiteralPath $catalogPath
$endpointRows = @($catalogLines | Where-Object { $_ -match '^\| API-\d{3} \|' })

if ($endpointRows.Count -ne 45) {
    Fail "expected 45 endpoint rows, found $($endpointRows.Count)"
}

$endpoints = @($endpointRows | ForEach-Object {
    $columns = @($_.Split('|') | ForEach-Object { $_.Trim() })
    if ($columns.Count -lt 8) {
        Fail "malformed endpoint row: $_"
    }
    [pscustomobject]@{
        Id       = $columns[1]
        Owner    = $columns[2]
        Boundary = $columns[3]
        Method   = $columns[4]
        Path     = $columns[5].Trim('`')
    }
})

$expectedIds = 1..45 | ForEach-Object { 'API-{0:D3}' -f $_ }
$idDifferences = @(Compare-Object -ReferenceObject $expectedIds -DifferenceObject $endpoints.Id)
if ($idDifferences.Count -ne 0) {
    Fail "endpoint IDs must be the complete API-001 through API-045 sequence"
}

$duplicateKeys = @($endpoints |
        Group-Object { "$($_.Method) $($_.Path)" } |
        Where-Object Count -gt 1)
if ($duplicateKeys.Count -gt 0) {
    Fail "duplicate method/path pair(s): $($duplicateKeys.Name -join ', ')"
}

$expectedBoundaries = @{
    'Gateway-public' = 37
    'Internal'       = 7
    'Identity trust' = 1
}
foreach ($entry in $expectedBoundaries.GetEnumerator()) {
    $actual = @($endpoints | Where-Object Boundary -eq $entry.Key).Count
    if ($actual -ne $entry.Value) {
        Fail "boundary '$($entry.Key)' expected $($entry.Value), found $actual"
    }
}

$expectedOwners = [ordered]@{
    'Authentication' = 7
    'Product'        = 12
    'Campaign'       = 8
    'Inventory'      = 6
    'Flash Sale'     = 2
    'Order'          = 2
    'Payment'        = 4
    'Cart'           = 4
    'Notification'   = 0
}
foreach ($entry in $expectedOwners.GetEnumerator()) {
    $actual = @($endpoints | Where-Object Owner -eq $entry.Key).Count
    if ($actual -ne $entry.Value) {
        Fail "owner '$($entry.Key)' expected $($entry.Value), found $actual"
    }
}

$contract = Read-RepositoryFile "specs\047-api-documentation\contracts\http-inventory.md"
$contractRows = @($contract -split "`r?`n" | Where-Object { $_ -match '^\| API-\d{3} \|' })
foreach ($row in $endpointRows) {
    if ($contractRows -notcontains $row) {
        Fail "reader catalog row is not identical to the canonical contract: $row"
    }
}

$springdocVersion = Read-RepositoryFile "pom.xml"
Assert-Contains $springdocVersion '<springdoc.version>2.8.17</springdoc.version>' "root dependency management"

$services = [ordered]@{
    'api-gateway'            = 'springdoc-openapi-starter-webflux-ui'
    'authentication-service' = 'springdoc-openapi-starter-webmvc-ui'
    'product-service'        = 'springdoc-openapi-starter-webmvc-ui'
    'campaign-service'       = 'springdoc-openapi-starter-webmvc-ui'
    'flashsale-service'      = 'springdoc-openapi-starter-webmvc-ui'
    'inventory-service'      = 'springdoc-openapi-starter-webmvc-ui'
    'order-service'          = 'springdoc-openapi-starter-webmvc-ui'
    'payment-service'        = 'springdoc-openapi-starter-webmvc-ui'
    'cart-service'           = 'springdoc-openapi-starter-webmvc-ui'
}
foreach ($entry in $services.GetEnumerator()) {
    $pom = Read-RepositoryFile "services\$($entry.Key)\pom.xml"
    Assert-Contains $pom "<artifactId>$($entry.Value)</artifactId>" "$($entry.Key) Springdoc dependency"
}

$applicationServices = @(
    'authentication-service',
    'product-service',
    'campaign-service',
    'flashsale-service',
    'inventory-service',
    'order-service',
    'payment-service',
    'cart-service'
)
foreach ($service in $applicationServices) {
    $application = Read-RepositoryFile "services\$service\src\main\resources\application.yml"
    Assert-Contains $application 'API_DOCS_ENABLED:false' "$service safe documentation default"
    Assert-Contains $application '/swagger-ui.html' "$service Swagger UI path"
}

$gateway = Read-RepositoryFile "services\api-gateway\src\main\resources\application.yml"
$documentNames = @(
    'authentication-service',
    'product-service',
    'campaign-service',
    'flashsale-service',
    'inventory-service',
    'order-service',
    'payment-service',
    'cart-service'
)
foreach ($documentName in $documentNames) {
    Assert-Contains $gateway "Path=/openapi/$documentName" "Gateway $documentName OpenAPI route"
    Assert-Contains $gateway "url: /openapi/$documentName" "Gateway $documentName Swagger definition"
}

$gatewayBusinessRoutes = @($gateway -split "`r?`n" | Where-Object { $_ -match '^\s*- Path=' })
if (@($gatewayBusinessRoutes | Where-Object { $_ -match 'Path=/internal(?:/|$)' }).Count -gt 0) {
    Fail "Gateway must not expose an internal business route"
}

$compose = Read-RepositoryFile "infra\docker\compose.yml"
$composeSwitchCount = ([regex]::Matches($compose, 'API_DOCS_ENABLED:\s*\$\{API_DOCS_ENABLED:-false\}')).Count
if ($composeSwitchCount -ne 2) {
    Fail "Compose must pass the safe-default opt-in to backend services and Gateway (expected 2 definitions, found $composeSwitchCount)"
}

$envExample = Read-RepositoryFile "infra\docker\.env.example"
Assert-Contains $envExample 'API_DOCS_ENABLED=false' "local environment example"

$cloudFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "infra\k8s\overlays\cloud") -Recurse -File |
        Where-Object Extension -in @('.yaml', '.yml'))
$cloudOptIns = @($cloudFiles | Select-String -Pattern 'API_DOCS_ENABLED\s*:\s*["'']?true')
if ($cloudOptIns.Count -gt 0) {
    Fail "cloud manifests must not enable API documentation"
}

$gatewaySecurity = Read-RepositoryFile "services\api-gateway\src\main\java\com\philia\flashsale\gateway\security\GatewaySecurityConfiguration.java"
Assert-Contains $gatewaySecurity '${springdoc.api-docs.enabled:false}' "Gateway security safe default"
Assert-Contains $gatewaySecurity 'documentationAccess(apiDocsEnabled)' "Gateway documentation authorization gate"

$securityTest = Read-RepositoryFile "services\api-gateway\src\test\java\com\philia\flashsale\gateway\security\GatewaySecurityConfigurationTests.java"
Assert-Contains $securityTest 'documentationAccess(false)' "Gateway default-deny test"
Assert-Contains $securityTest 'documentationAccess(true)' "Gateway opt-in test"

Write-Host "API_DOCUMENTATION=PASS (45 supported endpoints; 8 service documents; defaults disabled)"
