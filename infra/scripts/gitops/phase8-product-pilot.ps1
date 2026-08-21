<#
.SYNOPSIS
    Runs the manual Phase 8 Product Service pilot on EKS.
.DESCRIPTION
    With -Apply, creates the flash-sale namespace and runtime ConfigMap, prompts for a
    PostgreSQL password, creates Secrets in memory, starts one PostgreSQL StatefulSet with
    a PVC, and deploys only the Product Service base using the supplied immutable ECR image.
    Without -Apply, it only prints the planned action.
.SAFETY
    No password is written to disk or Git. The pilot is intentionally not the full eight-service
    Kustomize overlay and does not delete resources.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Image,
    [string]$Namespace = "flash-sale",
    [string]$StorageClassName = "gp2",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE."
    }
}

# Apply an in-memory YAML/JSON document without creating a temporary secret file.
function Apply-Manifest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Manifest
    )

    $Manifest | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl apply failed for an in-memory manifest."
    }
}

# Kubernetes Secret data values must be base64 encoded in the manifest sent to the API.
function ConvertTo-Base64Value {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}

# Build a Secret manifest in memory so credentials never become repository files.
function New-SecretManifest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [hashtable]$Values
    )

    $encodedData = [ordered]@{}
    foreach ($key in $Values.Keys) {
        $encodedData[$key] = ConvertTo-Base64Value $Values[$key]
    }

    [ordered]@{
        apiVersion = "v1"
        kind = "Secret"
        metadata = [ordered]@{
            name = $Name
            namespace = $Namespace
        }
        type = "Opaque"
        data = $encodedData
    } | ConvertTo-Json -Depth 6
}

# Dry mode is the default and performs no Kubernetes mutation.
if (-not $Apply) {
    Write-Host "Dry mode only. Would deploy PostgreSQL and product-service image:"
    Write-Host $Image
    Write-Host "Re-run with -Apply to create the namespace, Secrets, PVC, PostgreSQL and product Deployment."
    return
}

if (-not $Image.StartsWith("090814040069.dkr.ecr.")) {
    throw "Image must point to the project's ECR registry."
}

# Create the namespace idempotently from a client-rendered manifest.
$namespaceYaml = (& kubectl create namespace $Namespace --dry-run=client -o yaml) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0) {
    throw "Could not render namespace manifest."
}
Apply-Manifest $namespaceYaml

# Create only the non-secret runtime ConfigMap required by the pilot Deployment.
$configArguments = @(
    "create", "configmap", "flash-sale-runtime-config",
    "--namespace", $Namespace,
    "--from-literal=SPRING_PROFILES_ACTIVE=kubernetes",
    "--from-literal=SERVER_PORT=8080",
    "--from-literal=JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0",
    "--dry-run=client", "-o", "yaml"
)
$configMapYaml = (& kubectl @configArguments) -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0) {
    throw "Could not render runtime ConfigMap."
}
Apply-Manifest $configMapYaml

$securePassword = $null
$passwordPointer = $null
$plainPassword = $null
try {
    # Prompt interactively; the plain value exists only long enough to build in-memory Secret data.
    $securePassword = Read-Host "PostgreSQL password (never send it to chat)" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    Apply-Manifest (New-SecretManifest "product-postgres-credentials" @{
        POSTGRES_USER = "flashsale"
        POSTGRES_PASSWORD = $plainPassword
    })

    Apply-Manifest (New-SecretManifest "flash-sale-secrets" @{
        SPRING_DATASOURCE_URL = "jdbc:postgresql://product-postgres:5432/product_db"
        SPRING_DATASOURCE_USERNAME = "flashsale"
        SPRING_DATASOURCE_PASSWORD = $plainPassword
        SPRING_LIQUIBASE_ENABLED = "true"
    })
} finally {
    if ($passwordPointer) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    Remove-Variable plainPassword, securePassword, passwordPointer -ErrorAction SilentlyContinue
}

# PostgreSQL is deliberately one replica for this training pilot, not a production HA database.
$postgresManifest = @'
apiVersion: v1
kind: Service
metadata:
  name: product-postgres
  namespace: flash-sale
spec:
  clusterIP: None
  selector:
    app.kubernetes.io/name: product-postgres
  ports:
    - name: postgres
      port: 5432
      targetPort: postgres
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: product-postgres
  namespace: flash-sale
spec:
  serviceName: product-postgres
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: product-postgres
  template:
    metadata:
      labels:
        app.kubernetes.io/name: product-postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17-alpine
          ports:
            - name: postgres
              containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: product_db
            # EBS filesystems contain a lost+found directory at the mount root.
            # PostgreSQL must initialize a clean child directory instead of the mount point.
            - name: PGDATA
              value: /var/lib/postgresql/data/pgdata
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: product-postgres-credentials
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: product-postgres-credentials
                  key: POSTGRES_PASSWORD
          readinessProbe:
            exec:
              command:
                - sh
                - -c
                - pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
            periodSeconds: 10
          livenessProbe:
            exec:
              command:
                - sh
                - -c
                - pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
            periodSeconds: 15
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
          volumeMounts:
            - name: pgdata
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: pgdata
      spec:
        storageClassName: gp2
        accessModes:
          - ReadWriteOnce
        resources:
          requests:
            storage: 8Gi
'@

if ($StorageClassName -ne "gp2") {
    $postgresManifest = $postgresManifest.Replace("storageClassName: gp2", "storageClassName: " + $StorageClassName)
}
# Apply the stateful database and wait for its readiness probe before starting Product Service.
Apply-Manifest $postgresManifest

Invoke-Native "kubectl" @(
    "rollout", "status", "statefulset/product-postgres",
    "--namespace", $Namespace,
    "--timeout=180s"
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
# Apply only Product Service, not the Phase 7 all-services development overlay.
$productBase = Join-Path $repoRoot "infra\k8s\base\product-service"
Invoke-Native "kubectl" @("apply", "-k", $productBase, "--namespace", $Namespace)
Invoke-Native "kubectl" @(
    "set", "image",
    "deployment/product-service",
    "product-service=" + $Image,
    "--namespace", $Namespace
)
Invoke-Native "kubectl" @(
    "rollout", "status", "deployment/product-service",
    "--namespace", $Namespace,
    "--timeout=180s"
)
Invoke-Native "kubectl" @("get", "pods,svc,pvc", "--namespace", $Namespace)

Write-Host "Product pilot deployment completed. It is a manual live pilot, not yet Argo CD desired state."
