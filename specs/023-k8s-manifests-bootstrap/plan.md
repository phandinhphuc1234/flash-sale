# Implementation Plan: Kubernetes Manifest Bootstrap

**Branch**: codex/k8s-manifests-bootstrap | **Date**: 2026-08-20 | **Spec**: [spec.md](spec.md)
**Status**: Approved for source-only manifest work and client-side validation

## Summary

Create a Kustomize base and development overlay under root infra/k8s. The desired state describes
the eight currently approved services, but is intentionally not applied to the EKS cluster. It does
not deploy backing services, create credentials, or expose a public endpoint.

## Technical Context

| Area | Decision |
|---|---|
| Platform | Existing AWS EKS cluster flash-sale-dev, region ap-southeast-2 |
| Manifest owner | Root infra/k8s |
| Configuration tool | Kustomize rendered by kubectl kustomize |
| Services | api-gateway, authentication-service, campaign-service, flash-sale-service, inventory-service, order-service, payment-service, product-service |
| Runtime port | 8080 for every current service |
| Health signals | Spring Boot Actuator /actuator/health/liveness and /actuator/health/readiness |
| Image registry | Existing immutable ECR repositories, placeholder tag initial for source-only rendering |
| Secret source | External/manual only; manifests contain name reference flash-sale-secrets, never values |
| Live deployment | Explicitly out of scope; no non-dry-run apply |
| Validation | kubectl kustomize infra/k8s/overlays/dev, then kubectl apply --dry-run=client -k infra/k8s/overlays/dev |

## Constitution Check

| Rule | Result | Evidence |
|---|---|---|
| Root infrastructure ownership | Pass | Kustomize assets live in infra/k8s. |
| Platform-native discovery / controlled ingress | Pass | All eight services use ClusterIP; no Eureka or public service is added. |
| Observability | Pass | Existing Actuator health paths are used by startup/liveness/readiness probes. |
| Independent services | Pass | Each service receives one separate Deployment and Service; no Maven/module coupling. |
| Secret safety | Pass | No Secret resource or credentials are source controlled. |
| Kubernetes validation | Pass when executed | The dev overlay has an explicit client dry-run task. |
| ADR requirement | N/A | No service boundary, public ingress, persistence, or communication model is changed. |

## Project Structure

    infra/k8s/
    ├── base/
    │   ├── namespace.yml
    │   ├── kustomization.yaml
    │   └── <service>/
    │       ├── deployment.yaml
    │       ├── service.yaml
    │       └── kustomization.yaml
    └── overlays/
        └── dev/
            ├── flash-sale-config.yaml
            └── kustomization.yaml

    specs/023-k8s-manifests-bootstrap/
    ├── spec.md
    ├── plan.md
    ├── research.md
    ├── data-model.md
    ├── quickstart.md
    ├── contracts/runtime-manifest-contract.md
    └── tasks.md

## Implementation Design

### Base layer

The base contains the namespace and one directory per service. Each service uses only its short name
as the image placeholder, making the base environment-independent. The shared labels are
app.kubernetes.io/name and app.kubernetes.io/part-of: flash-sale.

Each Deployment requests 200m CPU and 512Mi memory, limits at 1 CPU and 768Mi memory, uses
imagePullPolicy: IfNotPresent, opens a named http port on 8080, and reads a non-secret ConfigMap
plus a referenced external Secret. The values are conservative bootstrap defaults copied from the
already reviewed product-service manifest; they are not a capacity decision.

Each Service is ClusterIP on port 8080 and selects only its own workload label. This provides
Kubernetes DNS without exposing traffic publicly.

### Development overlay

The dev overlay sets the namespace, includes the base, creates a ConfigMap containing
SPRING_PROFILES_ACTIVE=kubernetes, SERVER_PORT=8080, and JAVA_TOOL_OPTIONS, then maps all image
placeholders to the current ECR repositories. initial is a render-only placeholder and must be
replaced by an immutable image tag in the later delivery workflow.

### Deferred live-runtime design

A Docker Compose hostname such as postgres, redis, or kafka cannot be used by an EKS Pod. Before
Phase 8 deploys an application, an approved feature must decide:

1. Which managed or in-cluster backing services will be used.
2. Private networking and DNS.
3. The exact key set and management method for flash-sale-secrets.
4. How the first immutable images are built and pushed to ECR.
5. How api-gateway is exposed while preserving the single-ingress rule.

## Test Strategy and Evidence

| Layer | Applies | Rationale / command |
|---|---|---|
| Unit/integration/contract/load tests | No | No Java source, HTTP contract, Kafka contract, or runtime behavior changes. |
| Kustomize render | Yes | kubectl kustomize infra/k8s/overlays/dev must render 18 resources. |
| Kubernetes client validation | Yes | kubectl apply --dry-run=client -k infra/k8s/overlays/dev must succeed. |
| Whitespace / safety review | Yes | git diff --check and manifest scan confirm no Secret resource is introduced. |
| Live smoke test | No | Applying Pods without approved backing-service configuration would be misleading and is out of scope. |

Evidence is recorded in the implementation section of tasks.md and the pull request description.

## Complexity Tracking

No constitutional exception is introduced. A public LoadBalancer/Ingress, an in-cluster secret, and
a data-plane deployment were rejected for this feature because they would create unapproved runtime
behavior and cloud resources.

## Post-Design Constitution Check

Pass. The final manifest tree remains root-owned, internal-only, source-only, and validated by
Kubernetes client dry-run.
