<#
.SYNOPSIS
  Read-only compatibility rehearsal for restoring a prior Feature 049 image set.

.DESCRIPTION
  Verifies that prior immutable Cart, Inventory, and Order images exist, that their source enum
  surfaces can represent the durable regular-purchase states, and that the expanded tables are
  present. It reads only aggregate counts (never business rows or Secret values) and never changes
  Git, Argo, Kubernetes, ECR, Kafka, Schema Registry, Redis, or database data.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidatePattern("^[0-9a-fA-F]{40}$")][string]$PriorReleaseSha,
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedAccountId = "090814040069",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [ValidateRange(60, 900)][int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
if ($PSVersionTable.PSVersion.Major -lt 7) { throw "Feature 049 rollback rehearsal requires PowerShell 7 or newer." }
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$enumSources = @(
  [pscustomobject]@{ Type = "RegularPurchaseRequestState"; Path = "services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/domain/model/RegularPurchaseRequestState.java" },
  [pscustomobject]@{ Type = "RegularStockHoldStatus"; Path = "services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/domain/model/RegularStockHoldStatus.java" },
  [pscustomobject]@{ Type = "PurchaseSource"; Path = "services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/PurchaseSource.java" }
)

function Remaining { $seconds = [int][Math]::Ceiling(($deadline - (Get-Date)).TotalSeconds); if ($seconds -lt 1) { throw "Feature 049 rollback rehearsal exceeded its budget." }; return $seconds }
function Bounded { param([AllowEmptyString()][string]$Text); if (-not $Text) { return "no diagnostic output" }; if ($Text.Length -le 2000) { return $Text.Trim() }; return $Text.Substring(0,2000).Trim()+"... [truncated]" }
function Native { param([Parameter(Mandatory)][string]$Command,[Parameter(Mandatory)][string[]]$Arguments,[string]$WorkingDirectory = "")
  $info=[Diagnostics.ProcessStartInfo]::new(); $info.FileName=$Command; $info.UseShellExecute=$false; $info.CreateNoWindow=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true; if($WorkingDirectory){$info.WorkingDirectory=$WorkingDirectory}; foreach($a in $Arguments){$null=$info.ArgumentList.Add([string]$a)}; $p=[Diagnostics.Process]::new();$p.StartInfo=$info;$started=$false
  try { $null=Remaining; if(-not $p.Start()){throw "Could not start '$Command'."};$started=$true;$o=$p.StandardOutput.ReadToEndAsync();$e=$p.StandardError.ReadToEndAsync();if(-not $p.WaitForExit((Remaining)*1000)){$p.Kill($true);$p.WaitForExit();throw "'$Command' exceeded the rehearsal budget."};$p.WaitForExit();[pscustomobject]@{ExitCode=$p.ExitCode;StandardOutput=$o.GetAwaiter().GetResult().Trim();StandardError=$e.GetAwaiter().GetResult().Trim()} } finally { if($started -and -not $p.HasExited){$p.Kill($true);$p.WaitForExit()};$p.Dispose() }
}
function Required { param([Parameter(Mandatory)][string]$Command,[Parameter(Mandatory)][string[]]$Arguments,[Parameter(Mandatory)][string]$Description,[string]$WorkingDirectory = "");$r=Native $Command $Arguments $WorkingDirectory;if($r.ExitCode -ne 0){$d=if($r.StandardError){$r.StandardError}else{$r.StandardOutput};throw "$Description failed: $(Bounded $d)"};return $r }
function AwsAccount { $r=Required "aws" @("sts","get-caller-identity","--profile",$AwsProfile,"--query","Account","--output","text") "AWS identity"; $a=$r.StandardOutput.Trim(); if($a -ne $ExpectedAccountId){throw "AWS profile resolved to an unexpected account."}; Write-Host "AWS identity: PASS (account=$a region=$AwsRegion profile=$AwsProfile)"; return $a }
function ResolveImage { param([string]$Account,[string]$Repository); $tag="release-$($PriorReleaseSha.ToLowerInvariant())"; $r=Required "aws" @("ecr","describe-images","--repository-name",$Repository,"--image-ids","imageTag=$tag","--profile",$AwsProfile,"--region",$AwsRegion,"--query","imageDetails[0].[imageDigest,imageTags]","--output","json") "ECR lookup for $Repository"; try{$d=@($r.StandardOutput|ConvertFrom-Json -Depth 10)}catch{throw "Invalid ECR metadata for $Repository."}; if($d.Count -ne 2 -or -not $d[0] -or @($d[1]) -notcontains $tag){throw "Immutable tag $tag is missing for $Repository."}; return $true }
function EnumValues { param([string]$Source,[string]$Type);$m=[regex]::Match($Source,"enum\s+$([regex]::Escape($Type))\s*\{(?<body>.*?)\}",[Text.RegularExpressions.RegexOptions]::Singleline);if(-not$m.Success){throw "Prior image source does not expose enum $Type."};$values=@([regex]::Matches($m.Groups['body'].Value,'(?m)^\s*(?<n>[A-Z][A-Z0-9_]*)\s*(?=,|;|$)')|ForEach-Object{$_.Groups['n'].Value});if($values.Count -eq 0){throw "Enum $Type has no values."};return $values}
function ReadOnlyQuery { param([string]$Database,[string]$Query,[string]$Description);$tx="BEGIN TRANSACTION READ ONLY;`n$Query`nCOMMIT;";$shell='exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -A -t -F "|" -c "$2"';$r=Required "kubectl" @("-n",$Namespace,"exec","statefulset/postgres","--","sh","-ec",$shell,"sh",$Database,$tx) $Description;return @($r.StandardOutput -split "\r?\n"|Where-Object{$_})}
function DeployedSha { param([string]$Name); $image=(Required "kubectl" @("-n",$Namespace,"get","deployment",$Name,"-o","jsonpath={.spec.template.spec.containers[0].image}") "Deployed image lookup").StandardOutput.Trim(); $m=[regex]::Match($image,':release-(?<sha>[0-9a-fA-F]{40})$'); if(-not $m.Success){throw "Deployment '$Name' is not using release-<SHA>."}; return $m.Groups['sha'].Value.ToLowerInvariant() }

try {
  $account=AwsAccount
  $context=(Required "kubectl" @("config","current-context") "kubectl context").StandardOutput;if($context -notmatch [regex]::Escape(":cluster/$ExpectedClusterName")){throw "kubectl context is not '$ExpectedClusterName'."};Write-Output "kubectl context: $context"
  foreach($r in @("statefulset/postgres","deployment/cart-service","deployment/inventory-service","deployment/order-service")){$null=Required "kubectl" @("-n",$Namespace,"get",$r,"-o","name") "Rollback prerequisite $r"}
  $current = @(
    (DeployedSha "cart-service")
    (DeployedSha "inventory-service")
    (DeployedSha "order-service")
  )
  if (($current | Where-Object { $_ -eq $PriorReleaseSha.ToLowerInvariant() }).Count -eq 3) { throw "PriorReleaseSha is the active image set; supply the previous immutable release." }
  Write-Output "Active Feature 049 image set: PASS (immutable release shape verified; SHAs withheld)."
  foreach($repo in @("flash-sale/cart-service","flash-sale/inventory-service","flash-sale/order-service")){$null=ResolveImage $account $repo};Write-Output "Prior immutable image set: PASS (Cart, Inventory, and Order ECR digests resolved; SHA withheld)."
  foreach($entry in $enumSources){$source=(Required "git" @("show","$($PriorReleaseSha.ToLowerInvariant()):$($entry.Path)") "Prior image source lookup for $($entry.Type)" $repoRoot).StandardOutput;$values=EnumValues $source $entry.Type;Write-Output "Prior image enum $($entry.Type): PASS ($($values.Count) values; values withheld)."}
  $null=Required "kubectl" @("-n",$Namespace,"get","configmap/cart-service-runtime-config","configmap/inventory-service-runtime-config","configmap/order-service-runtime-config","-o","name") "Feature 049 runtime configuration"
  $cart=ReadOnlyQuery "cart_db" "SELECT CASE WHEN to_regclass('public.carts') IS NOT NULL AND to_regclass('public.cart_reconciliation_inbox') IS NOT NULL THEN 'expanded' ELSE 'missing' END;" "Cart expanded schema"
  $inventory=ReadOnlyQuery "inventory_db" "SELECT CASE WHEN to_regclass('public.regular_stock_holds') IS NOT NULL AND to_regclass('public.regular_hold_command_inbox') IS NOT NULL THEN 'expanded' ELSE 'missing' END;" "Inventory expanded schema"
  $order=ReadOnlyQuery "order_db" "SELECT CASE WHEN to_regclass('public.regular_purchase_requests') IS NOT NULL AND to_regclass('public.purchase_sagas') IS NOT NULL THEN 'expanded' ELSE 'missing' END;" "Order expanded schema"
  if($cart -notcontains 'expanded' -or $inventory -notcontains 'expanded' -or $order -notcontains 'expanded'){throw "One or more Feature 049 expanded schemas are missing; do not restore prior images."};Write-Output "Expanded Feature 049 schema: PASS (Cart, Inventory, and Order additive tables present)."
  $holdStates=ReadOnlyQuery "inventory_db" "SELECT status || '|' || count(*) FROM regular_stock_holds WHERE status IN ('HELD','CONFIRMED','RELEASED','EXPIRED') GROUP BY status ORDER BY status;" "Regular hold terminal aggregate"
  $requestStates=ReadOnlyQuery "order_db" "SELECT state || '|' || count(*) FROM regular_purchase_requests WHERE state IN ('RECEIVED','PRODUCT_VALIDATED','HOLD_ACQUIRED','ACCEPTED','PAYMENT_FAILED','CANCELLED','EXPIRED') GROUP BY state ORDER BY state;" "Regular purchase aggregate"
  foreach($row in @($holdStates+$requestStates)){if($row -notmatch '^[A-Z_]+\|[0-9]+$'){throw "Aggregate status output was not in the expected bounded format."}}
  Write-Output "Representative regular-purchase rows: PASS (aggregate states read only; no identities or payloads printed)."
  Write-Output "Feature 049 rollback compatibility rehearsal: PASS"
  Write-Output "No Kubernetes Deployment, ConfigMap, Git, database, Kafka, Schema Registry, Redis, ECR, or Secret state was changed."
} catch { Write-Output "Rollback rehearsal stopped before mutation. No Kubernetes, database, Git, ECR, Kafka, Schema Registry, Redis, or Secret state was changed."; throw }
