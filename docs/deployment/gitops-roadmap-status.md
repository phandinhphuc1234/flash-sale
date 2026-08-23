# GitOps Roadmap Status

This document is the canonical roadmap for the project's local-plus-cloud deployment model.
The repository has no separate staging or production environment; `flash-sale-dev` on AWS EKS is
the only cloud release-verification environment. No phase below authorizes production infrastructure
or public exposure by itself.

## Canonical roadmap

| Roadmap phase | Goal | Current status | Evidence / boundary |
|---:|---|---|---|
| 19 | Move the complete cloud overlay to Argo CD | **Complete** | `flash-sale-cloud` owns `infra/k8s/overlays/cloud` and is `Synced/Healthy`. |
| 20 | Provision Kafka topics and Avro schemas on EKS | **Complete** | Seven approved topics and nine Schema Registry subjects; Payment remains disabled. |
| 21 | CI/CD for all eight services | **Partial** | Selective Maven CI covers affected services; hosted ECR/PR promotion is currently proven for Product Service. Full eight-service image promotion is still required. |
| 22 | Internal end-to-end smoke | **Complete** | Live authenticated smoke passed on 2026-08-23: Product → Inventory → Campaign → Flash Sale reservation/replay → Order identity convergence. Evidence is recorded in `specs/040-gitops-internal-e2e/validation.md`; Payment remains disabled by design. |
| 23 | Public Gateway on AWS | **In implementation** | Approved development boundary: only `api-gateway` becomes an AWS NLB-backed `LoadBalancer` with an AWS-generated HTTP hostname; backend/platform Services remain private. |
| 24 | Stripe cloud enablement | **Pending** | Payment and Stripe runtime flags remain disabled; no cloud checkout/webhook flow is enabled. |
| 25 | Observability | **Pending** | Services expose Actuator/Prometheus endpoints; cloud Prometheus, Grafana, dashboards, and alerts are not yet deployed. |
| 26 | Final validation and cleanup | **Pending** | Final cloud E2E, load, full rollback, runbooks, and pilot cleanup remain. |

## Technical gates already added

The following repository phases are safety gates and must not be confused with the canonical roadmap
numbers above:

| Technical gate | Purpose | Relationship to roadmap |
|---|---|---|
| Repository Phase 21 | Verify Argo health, eight ECR/Pod digests, Payment flags, and internal Gateway smoke | Supports roadmap 21/22; does not implement full CI/CD or full E2E. |
| Repository Phase 22 | Verify cloud ownership, ConfigMap/Secret boundaries, private Services, Kafka safety, and Payment flags | Precondition for later cloud exposure; not canonical roadmap 22. |
| Repository Phase 23 | Run Terraform format/validate/plan safely with no apply | Precondition for infrastructure changes; not canonical roadmap 23 public Gateway. |

## Required order from here

After the Phase 22 PR is merged:

1. Merge and reconcile the canonical roadmap **23** public Gateway change, then run its bounded smoke.
2. Enable canonical roadmap **24** only with approved Stripe test secrets and explicit Payment flags.
3. Deploy canonical roadmap **25** monitoring and alerting.
4. Execute canonical roadmap **26** final validation, rollback evidence, documentation, and cleanup.

Do not skip canonical roadmap 22 to expose the Gateway publicly. Do not enable Stripe merely because
the cloud infrastructure is healthy.
