# GitOps Roadmap Status

This ledger separates repository capability, historical cloud evidence, and current live state. The
project has local and one AWS cloud development environment only; there is no separate production
environment.

> **Current state (2026-09-08):** the EKS environment was intentionally destroyed for cost
> control. `Synced/Healthy`, NLB, Stripe, monitoring, and capacity results below are historical unless
> a newer `validation.md` explicitly records a recreated cluster.

## Canonical roadmap

| Phase | Goal | Repository/current status |
|---:|---|---|
| 19 | Full cloud overlay owned by Argo CD | Desired state implemented; historical `flash-sale-cloud` reconciliation passed; no current cluster |
| 20 | Kafka topics and Avro subjects | Provisioning guard implemented for the original set; Feature 049 adds regular-hold/Cart contracts that require the next live provision rehearsal |
| 21 | CI/CD for deployed services | Nine-service selective verify/build/ECR/promotion-PR workflow implemented; Notification excluded as scaffold |
| 22 | Internal end-to-end smoke | Historical Flash Sale flow and local Feature 049 normal checkout passed; current cloud rerun pending |
| 23 | Public AWS Gateway | HTTPS NLB/ACM/DNS smoke passed historically; no current AWS load balancer |
| 24 | Stripe cloud enablement | Signed webhook, replay, Kafka Payment event, Order confirmation, and reservation finalization passed historically in Stripe test mode; current rerun pending |
| 25 | Seckill observability | Prometheus/Grafana desired state and dashboard implemented and historically deployed; no current monitoring pods |
| 26 | Final validation and cleanup | Controlled capacity evidence and infrastructure cleanup exist; Feature 049 live migration/E2E/rollback evidence remains pending |

## Two different “phase” number sets

Some helper scripts were named before the final product roadmap stabilized. Treat their number as a
script identity, not automatic proof that the same canonical roadmap phase is complete.

| Technical helper | Purpose |
|---|---|
| `phase21-cloud-release-verify.ps1` | Read-only Argo, image digest, Payment flag, and Gateway verification |
| `phase22-cloud-guard.ps1` | Read-only desired-state, Secret/ConfigMap reference, private-service, and Kafka guard |
| `phase23-terraform-gate.ps1` | Terraform format/validate/plan; never apply/destroy/import/state mutation |

## Recreate and release order

When cloud work resumes:

1. Recreate/reconcile Terraform and confirm EKS nodes/add-ons.
2. Provision Secrets/config without printing values.
3. Run the base migrations and the Feature 044/049 migration Jobs; verify expand/contract and old
   image compatibility first.
4. Provision the complete current Kafka topic/Schema Registry subject inventory idempotently.
5. Let the delivery workflow publish current images and review the promotion PR.
6. Reconcile Argo and verify running image digests.
7. Run normal checkout and Flash Sale/Stripe E2E, then controlled rollback rehearsal.
8. Recreate monitoring only if needed; destroy idle resources again when the exercise ends.

Never infer business success from Terraform apply or Argo health alone. Record each command, commit,
environment, and result in the governing feature's `validation.md`.
