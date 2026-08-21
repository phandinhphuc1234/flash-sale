# Validation Evidence: GitOps Stateful Product Pilot

**Feature**: `024-gitops-stateful-pilot`
**Branch**: `codex/gitops-phase9-stateful-pilot`
**Date**: 2026-08-21

## Completed locally

| Check | Scope | Result |
|---|---|---|
| PowerShell parser | `infra/scripts/gitops/phase9-stateful-pilot.ps1` | PASS |
| Terraform validate | `infra/terraform` | PASS |
| Terraform format check | `infra/terraform` | PASS |
| Kustomize render | `infra/k8s/overlays/dev-pilot` | PASS — exactly six resources |
| Kubernetes client dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/dev-pilot` | PASS — no live mutation |
| Phase 9 script default mode | external Secret name checks + render + dry-run | PASS — no apply/delete/secret write |
| Secret-kind scan | new pilot manifests | PASS — no `kind: Secret` |
| Diff whitespace | repository diff | PASS |
| Terraform imports | EBS CSI role, policy attachment, and add-on | PASS — all three imported |
| Terraform plan | `infra/terraform` after imports | PASS — 0 add, 2 in-place changes, 0 destroy |
| Terraform apply | EBS CSI role and add-on adoption | PASS — 0 added, 2 changed, 0 destroyed |
| EBS CSI runtime | `aws eks describe-addon` and kube-system Pods | PASS — add-on `ACTIVE`; controllers 6/6 and nodes 3/3 Running |
| Phase 9 live apply | `phase9-stateful-pilot.ps1 -Apply` | PASS — six resources reconciled; rollout completed |
| Pilot runtime | `kubectl -n flash-sale get pods,svc,pvc` | PASS — PostgreSQL 1/1, Product 1/1, PVC Bound at 8 GiB, internal Services |

## Live evidence

- EBS CSI add-on status: `ACTIVE`.
- `ebs-csi-controller`: two Pods, each `6/6 Running`.
- `ebs-csi-node`: three Pods, each `3/3 Running`.
- `product-postgres-0`: `1/1 Running`.
- `product-service`: `1/1 Running`.
- `pgdata-product-postgres-0`: `Bound`, 8 GiB, `gp2`.
- Services: `product-postgres` headless and `product-service` ClusterIP; no public endpoint.

No Terraform apply or live Phase 9 overlay apply was performed by this implementation turn.
