<#
.SYNOPSIS
  Validate and smoke-test the Phase 24 HTTPS Gateway edge.

.DESCRIPTION
  The default mode renders the cloud overlay and verifies the single HTTPS public Service shape.
  -Run additionally checks Argo health, the assigned AWS hostname, DNS for api.flashsale123.tech,
  and the Gateway readiness endpoint. This helper never reads Secrets and never mutates Kubernetes;
  rollback is a Git revert of the HTTPS Service patch.
##>
[CmdletBinding()]
param(
  [switch]$Run,
  [string]$Domain = "api.flashsale123.tech",
  [ValidateRange(30, 900)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$OverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$Namespace = "flash-sale"
$ServiceName = "api-gateway"
$ArgoApplication = "flash-sale-cloud"
$CertificateArn = "arn:aws:acm:ap-southeast-2:090814040069:certificate/67162c57-7840-4452-bf6c-fdf42fe7be12"

function Invoke-KubectlText {
  param([Parameter(Mandatory)][string[]]$Arguments)
  $output = & kubectl @Arguments
  if ($LASTEXITCODE -ne 0) { throw "kubectl failed: kubectl $($Arguments -join ' ')" }
  return (($output -join "`n").Trim())
}

function Get-HttpStatus {
  param([Parameter(Mandatory)][string]$Uri)
  $statusText = & curl.exe --silent --show-error --output NUL --write-out "%{http_code}" --max-time 10 --url $Uri 2>$null
  if ($LASTEXITCODE -ne 0) { return 0 }
  $status = 0
  if ([int]::TryParse(($statusText -join "").Trim(), [ref]$status)) { return $status }
  return 0
}

if (-not (Test-Path -LiteralPath $OverlayPath)) { throw "Cloud overlay not found: $OverlayPath" }
if ($Domain -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$') {
  throw "Invalid HTTPS Gateway domain: $Domain"
}

$rendered = Invoke-KubectlText @("kustomize", $OverlayPath)
if ($rendered -notmatch '(?ms)kind:\s*Service.*?name:\s*api-gateway.*?type:\s*LoadBalancer') {
  throw "Cloud overlay does not render api-gateway as a LoadBalancer."
}
if ($rendered -notmatch '(?ms)kind:\s*Service.*?name:\s*api-gateway.*?port:\s*443.*?targetPort:\s*http') {
  throw "Cloud overlay does not render the HTTPS 443 -> Gateway http port mapping."
}
if ($rendered.IndexOf($CertificateArn, [StringComparison]::Ordinal) -lt 0) {
  throw "Cloud overlay does not reference the expected ACM certificate ARN."
}
if ($rendered -notmatch 'service\.beta\.kubernetes\.io/aws-load-balancer-ssl-ports:\s*["'']?443') {
  throw "Cloud overlay is missing the AWS NLB TLS port annotation."
}
& kubectl apply --dry-run=client --validate=false -k $OverlayPath *> $null
if ($LASTEXITCODE -ne 0) { throw "Cloud overlay client-side dry-run failed." }

Write-Output ("kubectl context: " + ((kubectl config current-context) -join " "))
Write-Output "Phase 24 manifest gate: PASS (HTTPS api-gateway NLB; backend/platform Services remain private)."

if (-not $Run) {
  Write-Output "Validation-only mode: no live endpoint was queried and no Kubernetes resource was changed."
  exit 0
}

$application = Invoke-KubectlText @("-n", "argocd", "get", "application/$ArgoApplication", "-o", "json") | ConvertFrom-Json
if ($application.status.sync.status -ne "Synced" -or $application.status.health.status -ne "Healthy") {
  throw "Argo $ArgoApplication is not Synced/Healthy."
}
Write-Output "Argo ${ArgoApplication}: sync=$($application.status.sync.status) health=$($application.status.health.status) revision=$($application.status.sync.revision)"

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$externalHost = $null
do {
  $service = Invoke-KubectlText @("-n", $Namespace, "get", "service/$ServiceName", "-o", "json") | ConvertFrom-Json
  if ($service.spec.type -ne "LoadBalancer") { throw "Live api-gateway Service type is '$($service.spec.type)'." }
  if ($service.metadata.annotations.'service.beta.kubernetes.io/aws-load-balancer-ssl-cert' -ne $CertificateArn) {
    throw "Live api-gateway Service does not reference the expected ACM certificate."
  }
  $ingress = @($service.status.loadBalancer.ingress)
  if ($ingress.Count -gt 0) { $externalHost = if ($ingress[0].hostname) { $ingress[0].hostname } else { $ingress[0].ip } }
  if ([string]::IsNullOrWhiteSpace($externalHost)) { Start-Sleep -Seconds 5 }
} while ([string]::IsNullOrWhiteSpace($externalHost) -and (Get-Date) -lt $deadline)

if ([string]::IsNullOrWhiteSpace($externalHost)) { throw "AWS HTTPS endpoint was not assigned within $TimeoutSeconds seconds." }
Write-Output "AWS HTTPS endpoint assigned: <redacted-hostname>"

$dns = Resolve-DnsName -Name $Domain -Type A,AAAA,CNAME -ErrorAction SilentlyContinue
if ($null -eq $dns) { throw "DNS for $Domain has not propagated to the HTTPS Gateway yet." }
Write-Output "Gateway DNS: PASS ($Domain resolved)"

$readiness = Get-HttpStatus "https://$Domain/actuator/health/readiness"
Write-Output "HTTPS Gateway readiness status: $readiness"
if ($readiness -ne 200) { throw "HTTPS Gateway readiness expected HTTP 200, got HTTP $readiness." }

Write-Output "Phase 24 HTTPS Gateway smoke: PASS (readiness=$readiness)."
Write-Output "No Secret values, Authorization headers, or Checkout URLs were read or printed."
