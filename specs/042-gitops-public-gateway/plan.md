# Implementation Plan: Development Public Gateway on AWS

**Branch**: `codex/gitops-phase23-public-gateway` | **Date**: 2026-08-23 | **Spec**: [spec.md](spec.md)

## Summary

Expose only the cloud `api-gateway` Service through one AWS-managed development endpoint, keep all
backend/platform Services private, verify the existing Gateway behavior through the discovered
hostname, and make rollback a normal GitOps change. The recommended implementation is a
`LoadBalancer` Service with AWS NLB annotations, pending ADR/operator confirmation. No application
source, database, Kafka, Redis, Secret, or Payment behavior changes.

## Technical Context

**Language/Version**: Kubernetes YAML/Kustomize and PowerShell 7; no Java source change.

**Primary Dependencies**: Existing EKS Service load-balancer integration, Argo CD,
`kubectl`, PowerShell HTTP/DNS, and the current Gateway smoke routes. No new production dependency
unless the approved ADR selects AWS Load Balancer Controller.

**Storage**: N/A. No durable business state or PVC is changed.

**Testing**: Kustomize client dry-run, PowerShell parse/static checks, Kubernetes Service inventory,
Argo health, bounded endpoint smoke, and GitOps rollback verification. Maven and Kafka tests are not
applicable because application code and contracts are unchanged.

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`, Argo Application
`flash-sale-cloud`, VPC public subnets tagged for load balancer discovery.

**Project Type**: Root GitOps/Kubernetes infrastructure and operator verification script.

**Performance Goals**: Endpoint provisioning and smoke complete within 600 seconds; this is not a
load or latency benchmark.

**Constraints**: Exactly one public Service; generated hostname is development-only; HTTP-only unless
TLS/domain is approved separately; no credentials/tokens/Secret values in output; rollback must
restore `ClusterIP` without data deletion.

**Scale/Scope**: One Gateway Service, one EKS development environment, one endpoint smoke per run.

## Constitution Check

- **Specification traceability**: PASS pending resolution of the two explicit exposure decisions and
  ADR acceptance. No public manifest task may start before that gate.
- **Service ownership**: PASS. No service source, JPA, schema, migration, or database access changes.
- **Communication**: PASS. All external requests still enter through `api-gateway`; Kubernetes
  Service/DNS remains internal service discovery; existing HTTP routes are reused.
- **Data and messaging**: PASS. PostgreSQL, Redis Lua, Kafka consumers, outbox, topics, and schemas
  are untouched.
- **Root infrastructure ownership**: PASS. Overlay patches and runner live under `infra/`.
- **Observability**: PASS. Existing readiness endpoint and trace-safe HTTP status checks are reused;
  no Prometheus registry code is added.
- **Contracts and dependencies**: PASS. `contracts/public-gateway-smoke.md` documents consumed
  routes; no application API contract changes. Controller/IAM/Helm is a blocked dependency unless
  the operator selects that alternative.
- **Validation**: PASS for the planned layers; Maven/Kafka/load layers are intentionally omitted
  because no application or messaging behavior changes.

## Design

### Desired-state ownership

The cloud Kustomize overlay remains the single source of truth. The base Service stays `ClusterIP`
for local and safe baseline usage. A cloud-only patch changes only `api-gateway` to the approved
public type and adds AWS load-balancer metadata. No backend Service or platform Service is patched.

### Public edge behavior

The verification helper waits for the Gateway Service external hostname, performs bounded DNS and
HTTP requests, and checks readiness/catalog/admin boundary. It does not create an alternate route,
read a Kubernetes Secret, or print an Authorization header. If the endpoint never provisions, the
helper exits with diagnostics and leaves rollback to Git.

### Rollback

Rollback is a revert of the public-edge overlay patch. Argo must converge the Gateway Service to
`ClusterIP`; the helper then inventories all Services and confirms no public endpoint remains. No
`kubectl delete`, PVC deletion, Secret rewrite, migration rerun, topic deletion, or data cleanup is
allowed.

## Project Structure

```text
infra/k8s/overlays/cloud/
├── patches/api-gateway-public-service.yaml  # approved cloud-only Service patch
└── kustomization.yaml                       # owns the patch
infra/scripts/gitops/
└── phase23-public-gateway.ps1               # bounded discover/smoke/guard helper
specs/042-gitops-public-gateway/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/public-gateway-smoke.md
├── quickstart.md
└── tasks.md
```

**Structure Decision**: Keep all shared Kubernetes and operator assets at repository root under
`infra/`; do not add service-module code or a second Helm/Kustomize source of truth.

## Implementation Sequence

1. Resolve the two human decisions and mark ADR 0026 Accepted.
2. Add the cloud-only Service patch and verify the rendered manifest contains exactly one public
   Service.
3. Add the bounded smoke/guard helper and static tests.
4. Open a PR; let Argo reconcile only after review and merge.
5. Run public smoke, record sanitized evidence, then run the rollback path and record private-service
   inventory.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Public ingress topology change | Canonical roadmap Phase 23 requires a cloud edge endpoint | Keeping `ClusterIP` cannot satisfy the public Gateway acceptance criteria |
