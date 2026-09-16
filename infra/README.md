# Shared Infrastructure

`infra/` owns repository-wide platform and environment assets. Application source, runtime business
configuration, tests, and database changelogs remain with each service.

## Supported environments

The project intentionally models two environments:

- **local** — Docker Compose for development, integration tests, and direct debugging;
- **cloud** — AWS EKS desired state reconciled from Git by Argo CD.

There is no separate production environment. `dev-pilot` is historical Product pilot evidence, not
a third environment. The EKS cluster is currently absent after cost-control cleanup.

## Directory ownership

| Directory | Owns | Guide |
|---|---|---|
| `docker/` | Local PostgreSQL, Redis, Kafka, Schema Registry, optional tools, and app orchestration | [Docker](docker/README.md) |
| `terraform/` | AWS VPC, EKS, ECR, EBS CSI, optional ACM certificate | [Terraform](terraform/README.md) |
| `k8s/` | Kustomize bases/overlays, explicit migration Jobs, Argo applications | [Kubernetes](k8s/README.md) |
| `monitoring/` | Prometheus rules/scrape config and Grafana seckill dashboard | [Monitoring](monitoring/README.md) |
| `scripts/gitops/` | Guarded manual validation/apply/recovery helpers | [GitOps scripts](scripts/gitops/README.md) |
| `helm/` | Reserved approved chart ownership without duplicating Kustomize | [Helm](helm/README.md) |

## Ownership constraints

- Service-specific Dockerfiles stay under `services/<service>/Dockerfile`.
- Shared orchestration can create logical databases, but business migrations stay service-owned.
- Platform resources must not grant one service access to another service's schema.
- Kubernetes Service/DNS is discovery; Eureka must not be introduced.
- Secrets originate from ignored operator inputs or a secret manager, never committed YAML.
- Public exposure is limited to the reviewed TLS Gateway edge; backing services remain private.

## Delivery model

```text
Terraform creates AWS foundation
Kustomize describes cluster desired state
GitHub Actions publishes immutable ECR images and proposes tag changes
Argo CD reconciles reviewed develop state
```

Database migration and Kafka/schema provisioning are explicit gates before application promotion.
An Argo `Synced/Healthy` state proves reconciliation health, not end-to-end business correctness;
run the matching smoke/recovery tests afterward.

## Validation

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase23-terraform-gate.ps1 -AutoDetectPublicIp
```

The Terraform helper is read-only. Live `apply`, migration, Secret, Kafka, and Argo operations require
their explicit reviewed flags and prerequisites.
