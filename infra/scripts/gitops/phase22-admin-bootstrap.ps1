<#
.SYNOPSIS
  Validate and explicitly run the one-time Authentication administrator bootstrap Job.

.DESCRIPTION
  The default mode is validation-only. -Apply reads the password with Read-Host -AsSecureString,
  creates a temporary Secret, runs the currently deployed Authentication image as a non-web Job,
  waits for completion, and removes the temporary Secret and Job unless -KeepJob is supplied.
  Secret values and password hashes are never printed.
#>
[CmdletBinding()]
param(
  [string]$Namespace = "flash-sale",
  [Parameter(Mandatory = $true)]
  [string]$AdminEmail,
  [Parameter(Mandatory = $true)]
  [string]$AdminUsername,
  [ValidateRange(30, 1800)]
  [int]$TimeoutSeconds = 300,
  [switch]$Apply,
  [switch]$KeepJob,
  [switch]$ForceRerun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$jobName = "authentication-admin-bootstrap"
$secretName = "authentication-admin-bootstrap"

function Invoke-Kubectl {
  param([Parameter(Mandatory = $true)][string[]]$Arguments)
  & kubectl @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "kubectl failed with exit code ${LASTEXITCODE}: kubectl $($Arguments -join ' ')"
  }
}

function Get-KubectlText {
  param([Parameter(Mandatory = $true)][string[]]$Arguments)
  $previous = $ErrorActionPreference
  try {
    $ErrorActionPreference = "SilentlyContinue"
    $value = (& kubectl @Arguments 2>$null)
    if ($LASTEXITCODE -ne 0) { return "" }
    return ($value -join "").Trim()
  } finally {
    $ErrorActionPreference = $previous
  }
}

function Assert-Resource {
  param([Parameter(Mandatory = $true)][string[]]$Arguments, [Parameter(Mandatory = $true)][string]$Description)
  $resource = Get-KubectlText $Arguments
  if ([string]::IsNullOrWhiteSpace($resource)) {
    throw "Required resource is missing: $Description"
  }
}

function Apply-ManifestText {
  param([Parameter(Mandatory = $true)][string]$Manifest, [Parameter(Mandatory = $true)][switch]$DryRun)
  $arguments = @("apply")
  if ($DryRun) { $arguments += "--dry-run=client" }
  $Manifest | & kubectl @arguments -f -
  if ($LASTEXITCODE -ne 0) {
    throw "kubectl could not apply generated manifest (dryRun=$DryRun)."
  }
}

$context = Get-KubectlText @("config", "current-context")
if ([string]::IsNullOrWhiteSpace($context)) {
  throw "kubectl has no current context. Connect to the intended EKS cluster first."
}
Write-Output "kubectl context: $context"

Assert-Resource @("get", "namespace", $Namespace, "--ignore-not-found", "-o", "name") "namespace/$Namespace"
Assert-Resource @("-n", $Namespace, "get", "deployment/authentication-service", "-o", "name") "deployment/authentication-service"
foreach ($secret in @("authentication-secrets", "auth-jwt")) {
  Assert-Resource @("-n", $Namespace, "get", "secret", $secret, "-o", "name") "secret/$secret"
}
foreach ($configMap in @("flash-sale-runtime-config", "authentication-service-runtime-config")) {
  Assert-Resource @("-n", $Namespace, "get", "configmap", $configMap, "-o", "name") "configmap/$configMap"
}

$image = Get-KubectlText @("-n", $Namespace, "get", "deployment/authentication-service",
  "-o", "jsonpath={.spec.template.spec.containers[0].image}")
if ([string]::IsNullOrWhiteSpace($image)) { throw "Authentication Deployment image is empty." }

$existingJob = Get-KubectlText @("-n", $Namespace, "get", "job", $jobName, "--ignore-not-found", "-o", "name")
if (-not [string]::IsNullOrWhiteSpace($existingJob) -and -not $ForceRerun) {
  throw "Job '$Namespace/$jobName' already exists. Use -ForceRerun only after reviewing its status."
}

Write-Output "Authentication image: $image"
Write-Output "Bootstrap identity: email/username supplied (values withheld)"
Write-Output "Bootstrap mode: $(if ($Apply) { 'apply' } else { 'validation-only' })"
Write-Output "Bootstrap is disabled on the long-running Deployment."

$jobManifest = @"
apiVersion: batch/v1
kind: Job
metadata:
  name: $jobName
  namespace: $Namespace
  labels:
    app.kubernetes.io/name: authentication-service
    app.kubernetes.io/component: admin-bootstrap
    app.kubernetes.io/part-of: flash-sale
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 300
  template:
    metadata:
      labels:
        app.kubernetes.io/name: authentication-service
        app.kubernetes.io/component: admin-bootstrap
    spec:
      restartPolicy: Never
      securityContext:
        runAsUser: 999
        runAsGroup: 999
        runAsNonRoot: true
        fsGroup: 999
        fsGroupChangePolicy: OnRootMismatch
      containers:
        - name: admin-bootstrap
          image: $image
          imagePullPolicy: IfNotPresent
          env:
            - name: SPRING_MAIN_WEB_APPLICATION_TYPE
              value: none
            - name: AUTH_ADMIN_BOOTSTRAP_ENABLED
              value: "true"
            - name: AUTH_ADMIN_BOOTSTRAP_EMAIL
              valueFrom:
                secretKeyRef:
                  name: $secretName
                  key: AUTH_ADMIN_BOOTSTRAP_EMAIL
            - name: AUTH_ADMIN_BOOTSTRAP_USERNAME
              valueFrom:
                secretKeyRef:
                  name: $secretName
                  key: AUTH_ADMIN_BOOTSTRAP_USERNAME
            - name: AUTH_ADMIN_BOOTSTRAP_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: $secretName
                  key: AUTH_ADMIN_BOOTSTRAP_PASSWORD
          envFrom:
            - configMapRef:
                name: flash-sale-runtime-config
            - configMapRef:
                name: authentication-service-runtime-config
            - secretRef:
                name: authentication-secrets
          volumeMounts:
            - name: auth-jwt
              mountPath: /run/secrets/auth-jwt
              readOnly: true
      volumes:
        - name: auth-jwt
          secret:
            secretName: auth-jwt
            defaultMode: 0440
"@

Apply-ManifestText -Manifest $jobManifest -DryRun
Write-Output "Generated Job manifest passed client-side dry-run."

if (-not $Apply) {
  Write-Output "Validation-only mode: no Secret, Job, or auth_db row was changed."
  exit 0
}

$secretApplied = $false
$jobApplied = $false
$tempEnvFile = $null
$plainPassword = $null
$passwordPointer = [IntPtr]::Zero
try {
  if ($ForceRerun -and -not [string]::IsNullOrWhiteSpace($existingJob)) {
    Invoke-Kubectl @("-n", $Namespace, "delete", "job", $jobName, "--ignore-not-found=true")
  }

  $securePassword = Read-Host "Admin bootstrap password" -AsSecureString
  $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
  $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
  # PowerShell/.NET strings expose UTF-16 code units, while the application policy
  # is expressed in Unicode code points. UTF-32 encodes one code point per 4 bytes.
  $passwordLength = if ($null -eq $plainPassword) {
    0
  } else {
    [int]([Text.Encoding]::UTF32.GetByteCount($plainPassword) / 4)
  }
  if ($passwordLength -lt 12 -or $passwordLength -gt 128) {
    throw "Admin bootstrap password must contain 12 to 128 code points."
  }

  $tempEnvFile = [IO.Path]::GetTempFileName()
  $envLines = @(
    "AUTH_ADMIN_BOOTSTRAP_EMAIL=$AdminEmail",
    "AUTH_ADMIN_BOOTSTRAP_USERNAME=$AdminUsername",
    "AUTH_ADMIN_BOOTSTRAP_PASSWORD=$plainPassword"
  )
  [IO.File]::WriteAllLines($tempEnvFile, [string[]]$envLines, (New-Object Text.UTF8Encoding($false)))
  $secretManifest = & kubectl -n $Namespace create secret generic $secretName "--from-env-file=$tempEnvFile" --dry-run=client -o yaml
  if ($LASTEXITCODE -ne 0) { throw "kubectl could not render the temporary bootstrap Secret." }
  $secretManifest | & kubectl apply -f -
  if ($LASTEXITCODE -ne 0) { throw "kubectl could not apply the temporary bootstrap Secret." }
  $secretApplied = $true

  Apply-ManifestText -Manifest $jobManifest -DryRun:$false
  $jobApplied = $true
  Write-Output "Bootstrap Job started. Waiting up to $TimeoutSeconds seconds..."
  & kubectl -n $Namespace wait --for=condition=complete "job/$jobName" "--timeout=${TimeoutSeconds}s"
  if ($LASTEXITCODE -ne 0) {
    Write-Output "Bootstrap Job did not complete. Sanitized logs follow:"
    & kubectl -n $Namespace logs "job/$jobName" --all-containers=true --tail=100
    throw "Authentication admin bootstrap Job failed or timed out."
  }
  Write-Output "Authentication admin bootstrap Job completed successfully."
  & kubectl -n $Namespace logs "job/$jobName" --all-containers=true --tail=50
}
finally {
  if ($null -ne $passwordPointer -and $passwordPointer -ne [IntPtr]::Zero) {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
  }
  $plainPassword = $null
  if ($null -ne $tempEnvFile) {
    Remove-Item -LiteralPath $tempEnvFile -Force -ErrorAction SilentlyContinue
  }
  if ($secretApplied) {
    & kubectl -n $Namespace delete secret $secretName --ignore-not-found=true | Out-Null
  }
  if ($jobApplied -and -not $KeepJob) {
    & kubectl -n $Namespace delete job $jobName --ignore-not-found=true | Out-Null
  }
}

Write-Output "Bootstrap cleanup completed. Secret values were not displayed."
