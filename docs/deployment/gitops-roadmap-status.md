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
| 21 | CI/CD for all eight services | **Implementation complete; hosted evidence pending** | `.github/workflows/service-delivery.yml` detects affected targets, builds/pushes ECR images, and opens a reviewed cloud-overlay PR. The first service-local and shared-change runs still need to be recorded. |
| 22 | Internal end-to-end smoke | **Partial** | Gateway readiness/catalog/admin smoke is proven (`200/200/401`); a real Auth → Product → Campaign → Flash Sale → Order journey is still required. |
| 23 | Public Gateway on AWS | **Pending** | Gateway remains `ClusterIP` and is tested through localhost port-forward. No public LoadBalancer/Ingress/DNS/TLS is enabled. |
| 24 | Stripe cloud enablement | **Pending** | Payment and Stripe runtime flags remain disabled; no cloud checkout/webhook flow is enabled. |
| 25 | Observability | **Pending** | Services expose Actuator/Prometheus endpoints; cloud Prometheus, Grafana, dashboards, and alerts are not yet deployed. |
| 26 | Final validation and cleanup | **Pending** | Final cloud E2E, load, full rollback, runbooks, and pilot cleanup remain. |

## Supporting technical gates

These repository helper phases support the roadmap but do not replace its canonical numbering:

| Repository gate | Purpose |
|---|---|
| Phase 21 cloud release verification | Read-only Argo/ECR/Pod digest, Payment flag, and Gateway smoke evidence after a release. |
| Phase 22 cloud guard | Read-only ownership, ConfigMap/Secret boundary, private Service, Kafka, and Payment checks. |
| Phase 23 Terraform gate | Read-only format/validate/plan and AWS identity safety check; it is not public Gateway exposure. |

## Required order from here

1. Record a service-local and shared-change hosted Phase 21 run, merge the promotion PRs, and run the
   Phase 21 release verification helper.
2. Complete canonical roadmap **22** with an internal authenticated end-to-end smoke.
3. Design and review canonical roadmap **23** before adding any public AWS endpoint.
4. Enable canonical roadmap **24** only with approved Stripe test secrets and explicit Payment flags.
5. Deploy canonical roadmap **25** monitoring and alerting.
6. Execute canonical roadmap **26** final validation, rollback evidence, documentation, and cleanup.

Do not skip roadmap 22 to expose the Gateway publicly. Do not enable Stripe merely because the cloud
infrastructure is healthy.
